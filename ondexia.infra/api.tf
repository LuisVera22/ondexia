/**
 * API.
 *
 * API Gateway de tipo HTTP —no REST— por dos razones: cuesta bastante menos
 * por millón de solicitudes y trae autorizador JWT nativo. El autorizador
 * nativo es justamente el motivo por el que se eligió Cognito (DTE §7): validar
 * el token es configuración, no código, y no añade un arranque en frío a cada
 * petición como haría un autorizador Lambda propio.
 */

# ── Artefacto ──────────────────────────────────────────────────────────────

/**
 * Mientras ondexia.api no exista se despliega una función de relleno que
 * responde 501. No es adorno: permite aplicar y verificar la infraestructura
 * de extremo a extremo —dominio, autorizador, red, permisos— antes de escribir
 * la primera línea del backend, que es cuando salen baratos los errores de
 * infraestructura.
 */
data "archive_file" "relleno" {
  count = var.artefacto_api == "" ? 1 : 0

  type        = "zip"
  output_path = "${path.module}/.artefactos/relleno.zip"

  source {
    filename = "index.mjs"
    content  = <<-JS
      export const handler = async () => ({
        statusCode: 501,
        headers: { "content-type": "application/json; charset=utf-8" },
        body: JSON.stringify({
          error: "no_implementado",
          mensaje: "La infraestructura esta desplegada. El backend todavia no."
        })
      });
    JS
  }
}

locals {
  ruta_artefacto = var.artefacto_api == "" ? data.archive_file.relleno[0].output_path : var.artefacto_api
  hay_backend    = var.artefacto_api != ""
}

/**
 * El artefacto NO se sube directo a Lambda: pasa por S3.
 *
 * El límite de la subida directa —el ZIP viajando dentro de la llamada a la
 * API— son 50 MB. El artefacto del backend pesa unos 64 MB: Spring, Hibernate,
 * Spring Security y las dos versiones de Jackson que conviven porque springdoc
 * todavía arrastra la 2. Por el camino directo, `apply` falla con
 * RequestEntityTooLargeException y no hay ningún parámetro que lo evite.
 *
 * Vía S3 el techo son 250 MB ya descomprimidos, así que sobra sitio. De regalo,
 * el bucket versionado guarda cada artefacto desplegado, que es lo que permite
 * volver atrás sin recompilar.
 */
resource "aws_s3_bucket" "artefactos" {
  bucket = "${local.nombre}-artefactos-${local.sufijo}"
  tags   = { Name = "${local.nombre}-artefactos" }
}

resource "aws_s3_bucket_public_access_block" "artefactos" {
  bucket                  = aws_s3_bucket.artefactos.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "artefactos" {
  bucket = aws_s3_bucket.artefactos.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "artefactos" {
  bucket = aws_s3_bucket.artefactos.id

  versioning_configuration {
    status = "Enabled"
  }
}

# Cada despliegue deja un artefacto de ~64 MB. Sin caducidad, el bucket crece
# sin parar y se paga por almacenamiento algo que nadie va a mirar.
resource "aws_s3_bucket_lifecycle_configuration" "artefactos" {
  bucket = aws_s3_bucket.artefactos.id

  rule {
    id     = "retirar-artefactos-antiguos"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 30
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

resource "aws_s3_object" "api" {
  bucket = aws_s3_bucket.artefactos.id

  /**
   * La clave lleva el hash del contenido dentro.
   *
   * Con una clave fija, subir un artefacto nuevo cambia el objeto pero Lambda
   * sigue viendo la misma s3_key y no se entera: el despliegue «funciona» y
   * sigue corriendo el código viejo. Con el hash en el nombre, un artefacto
   * distinto es un objeto distinto, y la función se actualiza de verdad.
   */
  key    = "api/${filemd5(local.ruta_artefacto)}.zip"
  source = local.ruta_artefacto
  etag   = filemd5(local.ruta_artefacto)

  tags = { Name = "${local.nombre}-artefacto-api" }
}

# ── Permisos ───────────────────────────────────────────────────────────────

data "aws_iam_policy_document" "asumir_lambda" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "api" {
  name               = "${local.nombre}-api"
  assume_role_policy = data.aws_iam_policy_document.asumir_lambda.json

  /**
   * El techo, obligatorio: `iam:CreateRole` esta condicionado a que este rol
   * lleve exactamente esta frontera (ver despliegue.tf). Sin la linea, el apply
   * desde CI falla con AccessDenied al crear el rol.
   */
  permissions_boundary = aws_iam_policy.frontera_despliegue.arn
}

# Permisos de red para colocar la función dentro de la VPC.
resource "aws_iam_role_policy_attachment" "api_vpc" {
  role       = aws_iam_role.api.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

data "aws_iam_policy_document" "api" {
  # El rol lo comparten la API y la función de migraciones, así que necesita
  # escribir en los dos grupos. Sin el segundo, las migraciones se ejecutan pero
  # no dejan rastro — y el registro de una migración es exactamente lo que se
  # busca cuando un despliegue va mal.
  statement {
    sid     = "Logs"
    actions = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = [
      "${aws_cloudwatch_log_group.api.arn}:*",
      "${aws_cloudwatch_log_group.migraciones.arn}:*",
    ]
  }

  # Solo el bucket de marca, y solo sus objetos. La Lambda no tiene por qué
  # tocar los buckets de los sitios.
  statement {
    sid       = "Marca"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.marca.arn}/*"]
  }

  statement {
    sid       = "ListarMarca"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.marca.arn]
  }

  /**
   * Conectarse a la base como `ondexia_app`, sin contraseña.
   *
   * El identificador del recurso —`resource_id`, no el nombre— es a propósito:
   * si algún día se restaura la instancia desde una copia, cambia, y una
   * política escrita contra el nombre seguiría concediendo acceso a una base
   * que ya no es esa.
   *
   * Solo ese usuario. `ondexia_admin` queda deliberadamente fuera: conserva su
   * contraseña como salida de emergencia y ninguna función debe poder entrar
   * con él por esta vía.
   */
  statement {
    sid     = "ConectarBaseConIam"
    actions = ["rds-db:connect"]
    resources = [
      "arn:aws:rds-db:${var.region}:${data.aws_caller_identity.actual.account_id}:dbuser:${aws_db_instance.principal.resource_id}/ondexia_app"
    ]
  }
}

resource "aws_iam_role_policy" "api" {
  name   = "${local.nombre}-api"
  role   = aws_iam_role.api.id
  policy = data.aws_iam_policy_document.api.json
}

# ── Función ────────────────────────────────────────────────────────────────

resource "aws_lambda_function" "api" {
  function_name = "${local.nombre}-api"
  role          = aws_iam_role.api.arn

  s3_bucket        = aws_s3_bucket.artefactos.id
  s3_key           = aws_s3_object.api.key
  source_code_hash = filebase64sha256(local.ruta_artefacto)

  runtime = local.hay_backend ? var.runtime_api : "nodejs22.x"
  handler = local.hay_backend ? var.handler_api : "index.handler"

  memory_size = local.hay_backend ? var.memoria_api_mb : 128
  timeout     = 30

  reserved_concurrent_executions = var.concurrencia_reservada_api

  /**
   * SnapStart: la razón por la que este backend puede ser Spring Boot.
   *
   * Lambda toma una instantánea de la memoria DESPUÉS de la fase de
   * inicialización —con el contexto de Spring ya construido— y la restaura en
   * cada arranque en frío en lugar de reconstruirlo. Sin esto, un arranque en
   * frío de Spring Boot ronda los segundos; con esto, las centenas de
   * milisegundos.
   *
   * Dos condiciones que no son evidentes y que estaban sin cumplir:
   *
   *   1. Solo actúa sobre VERSIONES PUBLICADAS. Con la integración apuntando a
   *      $LATEST, se puede activar SnapStart y no servir de nada. De ahí
   *      `publish = true` y el alias de más abajo.
   *   2. Es incompatible con imágenes de contenedor. Es lo que decide que este
   *      artefacto sea un ZIP y no una imagen.
   *
   * Solo para el backend real: la función de relleno corre sobre Node, donde
   * SnapStart no aplica y declararlo sería un error de la API.
   */
  dynamic "snap_start" {
    for_each = local.hay_backend ? [1] : []

    content {
      apply_on = "PublishedVersions"
    }
  }

  publish = true

  vpc_config {
    subnet_ids         = aws_subnet.privada[*].id
    security_group_ids = [aws_security_group.lambda.id]
  }

  /**
   * Las credenciales viajan aquí, cifradas con KMS, y no en Secrets Manager.
   *
   * Lambda las descifra antes de ejecutar el código, sin tráfico de red.
   * Alcanzar Secrets Manager desde una subred privada sin NAT exigiría un
   * endpoint de interfaz a ~7.30 USD/mes, más caro que la instancia NAT que se
   * evitó (DTE §4.8).
   *
   * Contrapartida honesta: la contraseña queda en el estado de Terraform, que
   * por eso vive en un bucket cifrado y de acceso restringido. La solución
   * definitiva es autenticación por IAM contra RDS, que elimina la contraseña
   * — anotada como deuda en el README.
   */
  environment {
    variables = {
      /**
       * Dos perfiles, no uno.
       *
       * 'aws' trae lo que depende de la PLATAFORMA —cómo se compone la URL de
       * la base, que Flyway no migre al arrancar, de dónde sale el emisor de
       * los tokens— y vale igual en dev que en prod. El nombre del entorno
       * queda para lo que de verdad cambia entre ellos.
       *
       * Sin el perfil 'aws', application.yml usa su valor por omisión de
       * BD_URL, que apunta a localhost. En Lambda eso no falla al arrancar:
       * falla al primer intento de conexión, con un tiempo de espera agotado
       * que no dice nada de la causa.
       */
      SPRING_PROFILES_ACTIVE = "aws,${var.entorno}"

      /**
       * Qué build está sirviendo, visible en /salud.
       *
       * application.yml ya lee ONDEXIA_VERSION y cae a «0.1.0-SNAPSHOT» si no
       * está, que es lo que respondía la sonda: el mismo texto para siempre,
       * sin decir nada. Con el hash del artefacto, `curl /salud` identifica el
       * build exacto que corre — que es justo lo que se quiere saber cuando
       * algo se comporta distinto de lo esperado tras un despliegue.
       *
       * Efecto secundario deliberado: cambia con cada artefacto, así que un
       * despliegue nuevo SIEMPRE modifica $LATEST y Lambda publica versión
       * nueva. Sin esto, republicar tras una versión fallida es imposible —
       * `publish-version` devuelve la misma versión rota, porque $LATEST no ha
       * cambiado. Ocurrió al desplegar la Entrega 2.
       */
      ONDEXIA_VERSION = substr(filemd5(local.ruta_artefacto), 0, 12)

      /**
       * El origen del SPA, y NO es redundante con el CORS de la pasarela.
       *
       * El SPA no llama desde su propio origen: se sirve por CloudFront y la
       * API vive en execute-api. Son dominios distintos, así que toda llamada
       * es entre orígenes.
       *
       * El reparto de responsabilidades es contraintuitivo y conviene tenerlo
       * claro antes de tocar nada:
       *
       *   · Las CABECERAS que ve el navegador son SIEMPRE las de la pasarela.
       *     AWS lo documenta sin ambigüedad: «If you configure CORS for an API,
       *     API Gateway ignores CORS headers returned from your backend
       *     integration.» Las que ponga Spring se descartan.
       *
       *   · Pero el preflight lo RESPONDE Spring, porque la ruta
       *     `OPTIONS /{proxy+}` de más abajo gana sobre `ANY`. Y Spring
       *     rechaza con 403 un origen que no reconozca — lo cubre CorsIT.
       *
       * De ahí que esto importe: si aquí quedara el localhost:4200 por
       * omisión, Spring devolvería 403 a cada preflight, la pasarela le
       * pegaría sus cabeceras correctas a esa respuesta, y el navegador
       * bloquearía igual. Los registros mostrarían 403 sobre OPTIONS; en el
       * navegador solo se vería un error de red sin cuerpo.
       *
       * Por eso este valor y `allow_origins` de la pasarela salen los dos de
       * `local.origen_app`: tienen que ser el mismo o no funciona ninguno.
       */
      CORS_ORIGENES = local.origen_app

      BD_HOST   = aws_db_instance.principal.address
      BD_PUERTO = tostring(aws_db_instance.principal.port)
      BD_NOMBRE = aws_db_instance.principal.db_name

      /**
       * `ondexia_app`, no el usuario maestro, y SIN contraseña.
       *
       * Aquí ya no hay ningún secreto. El token de conexión lo genera la propia
       * función firmando con las credenciales de su rol, así que no queda nada
       * que leer con lambda:GetFunctionConfiguration ni nada que guardar en el
       * estado de Terraform.
       *
       * El nombre va a mano y no sale de `aws_db_instance.principal.username`
       * porque ese es el maestro. Tiene que coincidir letra por letra con el rol
       * que crea la V8 y con el que autoriza la política de rds-db:connect: si
       * los tres no dicen lo mismo, la conexión falla con «PAM authentication
       * failed», que no menciona IAM por ningún lado.
       */
      BD_USUARIO = "ondexia_app"

      /**
       * La clave PUBLICA con la que se verifican las atestaciones de RUC.
       *
       * No es un secreto, y por eso puede estar aqui. La privada vive en SSM y
       * solo la lee ondexia.consultas, que esta fuera de la VPC; esta funcion no
       * puede leer SSM en ejecucion —subred privada sin NAT— asi que el valor
       * tiene que viajar en su configuracion.
       *
       * Es exactamente lo que decidio que la firma sea Ed25519 y no un HMAC: con
       * un HMAC haria falta el MISMO secreto en las dos partes, y este sitio lo
       * dejaria en el estado de Terraform. Justo lo que se quito al pasar la base
       * de datos a autenticacion por IAM.
       *
       * Vacia cuando no hay modulo de consultas desplegado. La aplicacion arranca
       * igual y rechaza toda atestacion diciendo que no esta configurada — no
       * acepta ninguna.
       */
      CONSULTAS_FIRMA_PUBLICA = local.hay_consultas ? data.aws_ssm_parameter.firma_publica[0].value : ""

      COGNITO_POOL_ID    = aws_cognito_user_pool.inquilinos.id
      COGNITO_CLIENTE_ID = aws_cognito_user_pool_client.spa.id
      BUCKET_MARCA       = aws_s3_bucket.marca.id
      CDN_MARCA          = var.gestionar_dns ? "https://cdn.${var.dominio}" : "https://${aws_cloudfront_distribution.marca.domain_name}"
    }
  }

  depends_on = [
    aws_iam_role_policy_attachment.api_vpc,
    aws_cloudwatch_log_group.api,
  ]

  tags = { Name = "${local.nombre}-api" }
}

/**
 * El alias es lo que se invoca. Nunca $LATEST.
 *
 * Un alias apunta a una versión inmutable y concreta, y es la pieza que hace
 * que SnapStart tenga efecto: la instantánea pertenece a la versión, no a la
 * función. Apuntar la integración a $LATEST significa ejecutar el código sin
 * instantánea — sin ningún aviso, solo arranques en frío lentos.
 *
 * Es además el sitio natural para un despliegue gradual el día que haga falta:
 * un alias puede repartir el tráfico entre dos versiones.
 */
resource "aws_lambda_alias" "api" {
  name             = "activo"
  description      = "Version que sirve el trafico"
  function_name    = aws_lambda_function.api.function_name
  function_version = aws_lambda_function.api.version
}

# ── Migraciones ────────────────────────────────────────────────────────────

/**
 * Mismo artefacto, otro handler, otra función.
 *
 * Las migraciones no corren al arrancar la API — el porqué está en
 * ManejadorMigraciones. Aquí solo importan las diferencias de configuración:
 * más tiempo de espera (una migración sobre una tabla con datos puede tardar
 * minutos, y los 30 s de la API se quedarían cortos), sin SnapStart (se invoca
 * un puñado de veces al año) y sin concurrencia reservada.
 *
 * Se ejecuta después de cada despliegue:
 *
 *   aws lambda invoke --function-name NOMBRE --payload '{}' salida.json
 *
 * El nombre exacto lo publica el output `funcion_migraciones`.
 */
resource "aws_lambda_function" "migraciones" {
  count = local.hay_backend ? 1 : 0

  function_name = "${local.nombre}-migraciones"
  role          = aws_iam_role.api.arn

  s3_bucket        = aws_s3_bucket.artefactos.id
  s3_key           = aws_s3_object.api.key
  source_code_hash = filebase64sha256(local.ruta_artefacto)

  runtime = var.runtime_api
  handler = "com.ondexia.infrastructure.entrada.lambda.ManejadorMigraciones::handleRequest"

  memory_size = 1024
  timeout     = 600

  vpc_config {
    subnet_ids         = aws_subnet.privada[*].id
    security_group_ids = [aws_security_group.lambda.id]
  }

  environment {
    variables = {
      BD_HOST       = aws_db_instance.principal.address
      BD_PUERTO     = tostring(aws_db_instance.principal.port)
      BD_NOMBRE     = aws_db_instance.principal.db_name
      BD_USUARIO    = aws_db_instance.principal.username
      BD_CONTRASENA = random_password.bd.result
      # Lo lee la siembra de datos de ejemplo para negarse en produccion.
      ENTORNO = var.entorno
    }
  }

  depends_on = [
    aws_iam_role_policy_attachment.api_vpc,
    aws_cloudwatch_log_group.migraciones,
  ]

  tags = { Name = "${local.nombre}-migraciones" }
}

# ── API Gateway ────────────────────────────────────────────────────────────

resource "aws_apigatewayv2_api" "principal" {
  name          = "${local.nombre}-api"
  protocol_type = "HTTP"

  /**
   * CORS restringido al origen de la aplicación.
   *
   * Con comodín, cualquier página de internet podría llamar a la API desde el
   * navegador de un usuario con sesión abierta.
   */
  cors_configuration {
    allow_origins = [local.origen_app]
    allow_methods = ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"]
    /*
     * x-empresa-id va aquí, y no es opcional.
     *
     * El navegador solo deja enviar las cabeceras que la comprobación previa
     * autoriza. Sin esta, la petición se bloquea EN EL NAVEGADOR —no llega a
     * viajar— en cuanto el usuario elige empresa. Y como hasta ese momento la
     * cabecera no se manda, el fallo aparecería más tarde, al cambiar de
     * empresa, pareciendo un problema del selector.
     */
    allow_headers = ["authorization", "content-type", "x-empresa-id"]
    max_age       = 3600
  }

  tags = { Name = "${local.nombre}-api" }
}

resource "aws_apigatewayv2_authorizer" "cognito" {
  api_id           = aws_apigatewayv2_api.principal.id
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]
  name             = "cognito"

  jwt_configuration {
    audience = [aws_cognito_user_pool_client.spa.id]
    issuer   = "https://${aws_cognito_user_pool.inquilinos.endpoint}"
  }
}

resource "aws_apigatewayv2_integration" "api" {
  api_id           = aws_apigatewayv2_api.principal.id
  integration_type = "AWS_PROXY"
  # El alias, no la función. Invocar la función a secas ejecuta $LATEST, que no
  # lleva instantánea de SnapStart.
  integration_uri        = aws_lambda_alias.api.invoke_arn
  payload_format_version = "2.0"
  timeout_milliseconds   = 29000
}

/**
 * Una sola ruta que lo recoge todo, protegida por el autorizador.
 *
 * El enrutado fino lo hace Spring, que ya lo sabe hacer; duplicarlo aquí
 * obligaría a tocar la infraestructura cada vez que se añade un endpoint.
 * Lo que sí decide la infraestructura es que **nada** pasa sin token: se
 * deniega por defecto.
 */
resource "aws_apigatewayv2_route" "todo" {
  api_id             = aws_apigatewayv2_api.principal.id
  route_key          = "ANY /{proxy+}"
  target             = "integrations/${aws_apigatewayv2_integration.api.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito.id
}

/**
 * La comprobación previa de CORS, sin autorizador. Ruta aparte y explícita.
 *
 * `ANY /{proxy+}` incluye OPTIONS, así que sin esto la comprobación previa cae
 * en la ruta protegida. El navegador la envía **sin credenciales** —lo exige la
 * especificación de CORS— de modo que el autorizador JWT responde 401 y el
 * navegador cancela la petición real antes de intentarla.
 *
 * El síntoma es de los que engañan: la sesión se abre bien, el token es válido,
 * y aun así toda llamada a la API falla. En los registros de la pasarela se ve
 * el 401 sobre OPTIONS; en el navegador solo se ve un error de red sin cuerpo,
 * porque una respuesta que no pasa CORS es ilegible para el JavaScript que la
 * pidió.
 *
 * API Gateway prefiere la ruta más específica, así que esta gana sobre ANY.
 * Responde Spring, que ya tiene configurado el origen permitido (CORS_ORIGENES).
 */
resource "aws_apigatewayv2_route" "preflight" {
  api_id             = aws_apigatewayv2_api.principal.id
  route_key          = "OPTIONS /{proxy+}"
  target             = "integrations/${aws_apigatewayv2_integration.api.id}"
  authorization_type = "NONE"
}

# Sonda de vida, sin token: es lo que se consulta para saber si el sistema
# responde, y exigirle credenciales lo haría inútil.
resource "aws_apigatewayv2_route" "salud" {
  api_id    = aws_apigatewayv2_api.principal.id
  route_key = "GET /salud"
  target    = "integrations/${aws_apigatewayv2_integration.api.id}"
}

resource "aws_apigatewayv2_stage" "principal" {
  api_id      = aws_apigatewayv2_api.principal.id
  name        = "$default"
  auto_deploy = true

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_gateway.arn
    format = jsonencode({
      solicitudId = "$context.requestId"
      ip          = "$context.identity.sourceIp"
      metodo      = "$context.httpMethod"
      ruta        = "$context.path"
      estado      = "$context.status"
      latenciaMs  = "$context.responseLatency"
      usuario     = "$context.authorizer.claims.sub"
    })
  }

  default_route_settings {
    # Techo de peticiones. No es seguridad, es control de gasto: sin él, un
    # bucle en el frontend factura sin límite.
    throttling_burst_limit = 100
    throttling_rate_limit  = 50
  }
}

/**
 * El permiso se concede sobre el ALIAS, con `qualifier`.
 *
 * Un permiso sobre la función sin cualificar no cubre la invocación a través
 * del alias: la pasarela recibiría un 500 con «not authorized to perform
 * lambda:InvokeFunction», que despista bastante porque el rol y la política
 * parecen correctos.
 */
resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.api.function_name
  qualifier     = aws_lambda_alias.api.name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.principal.execution_arn}/*/*"
}
