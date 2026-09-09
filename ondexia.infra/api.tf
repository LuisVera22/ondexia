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
  # Solo el grupo de la API. Las migraciones tienen rol propio desde el hallazgo
  # A3: compartirlo obligaba a conceder aqui el rds-db:connect del rol de
  # migraciones, que es equivalente al maestro — es decir, una API comprometida
  # habria podido conectarse con el.
  statement {
    sid       = "Logs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.api.arn}:*"]
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

# ── El rol de las migraciones, aparte del de la API ────────────────────────

/**
 * Rol propio, y es lo que hace posible quitar la contrasena (hallazgo A3).
 *
 * La funcion de migraciones se conecta como `ondexia_migraciones`, que es
 * miembro de `ondexia_admin` y por tanto puede alterar cualquier objeto. Si ese
 * `rds-db:connect` viviera en el rol compartido con la API, una API
 * comprometida podria abrir una conexion con privilegios de maestro — se habria
 * cambiado un secreto en una variable de entorno por una escalada silenciosa.
 *
 * Por eso los roles se separan: cada funcion puede conectarse con SU usuario de
 * base y con ningun otro.
 */
resource "aws_iam_role" "migraciones" {
  count = local.hay_backend ? 1 : 0

  name                 = "${local.nombre}-migraciones"
  description          = "Ejecucion de las migraciones de Flyway"
  assume_role_policy   = data.aws_iam_policy_document.asumir_lambda.json
  permissions_boundary = aws_iam_policy.frontera_despliegue.arn

  tags = { Name = "${local.nombre}-migraciones" }
}

resource "aws_iam_role_policy_attachment" "migraciones_vpc" {
  count = local.hay_backend ? 1 : 0

  role       = aws_iam_role.migraciones[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

data "aws_iam_policy_document" "migraciones" {
  statement {
    sid       = "Logs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.migraciones.arn}:*"]
  }

  /**
   * Conectarse como `ondexia_migraciones`, sin contrasena.
   *
   * `ondexia_admin` sigue fuera, igual que en la politica de la API: conserva su
   * contrasena como salida de emergencia y ninguna funcion debe poder entrar con
   * el por esta via. El rol de migraciones es miembro suyo, que es otra cosa —
   * la pertenencia se puede revocar con una sentencia SQL; una contrasena
   * filtrada, no.
   */
  statement {
    sid     = "ConectarBaseConIam"
    actions = ["rds-db:connect"]
    resources = [
      "arn:aws:rds-db:${var.region}:${data.aws_caller_identity.actual.account_id}:dbuser:${aws_db_instance.principal.resource_id}/ondexia_migraciones"
    ]
  }
}

resource "aws_iam_role_policy" "migraciones" {
  count = local.hay_backend ? 1 : 0

  name   = "${local.nombre}-migraciones"
  role   = aws_iam_role.migraciones[0].id
  policy = data.aws_iam_policy_document.migraciones.json
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

  /**
   * Margen para que Terraform vea el DESENLACE de la publicacion.
   *
   * El apply del 2026-09-08 murio en 10m20s con «timeout while waiting for
   * state to become Successful (last state: InProgress)», y la lectura facil
   * —la instantanea va lenta— era falsa: los registros dicen
   * `Init Duration: 24278 ms, Status: error`. La inicializacion fallo en 24
   * SEGUNDOS, porque Hibernate valido el esquema y no encontro la tabla `caja`.
   *
   * Lo que consume los diez minutos es lo que Lambda hace DESPUES de que la
   * init falle: mantiene la publicacion en InProgress mientras reintenta, y
   * solo al final marca la version como Failed. Terraform deja de mirar antes
   * de ese final.
   *
   * Por eso subir el limite no arregla nada de la funcion —una init rota
   * seguira rota— pero cambia el mensaje que se recibe, que es lo que importa
   * a quien despliega: en vez de un tiempo agotado que no dice nada, el error
   * real, con la version fallida y su motivo. Diez minutos de espera para
   * acabar sin diagnostico se pagan dos veces.
   *
   * SnapStart y `validate` juntos hacen que el ORDEN sea parte del despliegue:
   * las migraciones van antes de publicar la API, y por eso deploy.yml las
   * invoca primero. Aplicar a mano descoloca ese orden — asi salio esto.
   */
  timeouts {
    create = "20m"
    update = "20m"
  }

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
       *   · El PREFLIGHT lo responde la pasarela, no Spring. Antes lo
       *     respondia Spring por una ruta `OPTIONS /{proxy+}` sin autorizador,
       *     que se retiro en el hallazgo A2 — era la unica via de invocacion
       *     sin token.
       *
       * De ahí que esto siga importando: Spring comprueba el `Origin` en CADA
       * peticion, no solo en el preflight, y con el localhost:4200 por omisión
       * devolvería 403 —«Invalid CORS request», texto plano— a toda llamada
       * real. La pasarela le pegaría sus cabeceras correctas a esa respuesta y
       * el navegador solo vería un 403 sin cuerpo legible. Es exactamente lo que
       * paso con la funcion de consultas.
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

      # Las claves publicas con las que se verifica la firma. Ver el data source
      # `jwks_inquilinos` mas abajo.
      COGNITO_JWKS = data.http.jwks_inquilinos.response_body
      BUCKET_MARCA = aws_s3_bucket.marca.id
      CDN_MARCA    = var.gestionar_dns ? "https://cdn.${var.dominio}" : "https://${aws_cloudfront_distribution.marca.domain_name}"
      # El bus con el Emisor (facturacion.tf). Lo que la API puede hacer en el
      # esta en aws_iam_role_policy.api_emision, alli mismo.
      BUCKET_EMISION = aws_s3_bucket.emision.id
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
  role          = aws_iam_role.migraciones[0].arn

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
      BD_HOST   = aws_db_instance.principal.address
      BD_PUERTO = tostring(aws_db_instance.principal.port)
      BD_NOMBRE = aws_db_instance.principal.db_name

      /**
       * `ondexia_migraciones`, y SIN contrasena (hallazgo A3).
       *
       * Aqui estaba `random_password.bd.result`: la contrasena MAESTRA de la
       * instancia, en texto plano, legible con lambda:GetFunctionConfiguration
       * por cualquiera con acceso de lectura a Lambda. Con ella se entra como
       * ondexia_admin.
       *
       * Ahora el token lo firma la propia funcion con las credenciales de su
       * rol, igual que hace la API desde la V8. No hay nada que leer.
       *
       * El nombre va a mano y tiene que coincidir letra por letra con el rol que
       * crea la V13 y con el que autoriza rds-db:connect. Si los tres no dicen
       * lo mismo, la conexion falla con «PAM authentication failed», que no
       * menciona IAM por ningun lado.
       */
      BD_USUARIO = "ondexia_migraciones"

      # Lo lee la siembra de datos de ejemplo para negarse en produccion.
      ENTORNO = var.entorno
    }
  }

  depends_on = [
    aws_iam_role_policy_attachment.migraciones_vpc,
    aws_cloudwatch_log_group.migraciones,
  ]

  tags = { Name = "${local.nombre}-migraciones" }
}


/**
 * El JWKS del pool, descargado AL APLICAR y horneado en la funcion (hallazgo A1).
 *
 * Las Lambdas estan en subred privada sin NAT: no pueden descargar
 * `/.well-known/jwks.json` en ejecucion, y por eso la aplicacion se limitaba a
 * mirar emisor y caducidad SIN VERIFICAR LA FIRMA. La premisa era que la
 * pasarela ya la habia verificado, lo que convierte cualquier descuido futuro
 * —una ruta sin autorizador, un permiso de invocacion mas ancho— en
 * autenticacion arbitraria.
 *
 * Traerlo aqui es la tercera via: la descarga la hace quien aplica Terraform,
 * que si tiene internet, y el valor viaja como variable de entorno. No es un
 * secreto —son claves publicas— asi que que quede en el estado no importa.
 *
 * SOBRE LA ROTACION. Cognito no rota las claves de firma de un grupo de usuarios
 * por su cuenta: viven lo que el grupo. Si algun dia lo hiciera, el sintoma
 * seria un 401 en toda peticion y se arregla volviendo a aplicar. Ese es el
 * precio de no pagar un endpoint de interfaz (~7.30 USD/mes), y es explicito.
 */
data "http" "jwks_inquilinos" {
  url = "https://cognito-idp.${var.region}.amazonaws.com/${aws_cognito_user_pool.inquilinos.id}/.well-known/jwks.json"

  # Sin esto, un 500 de Cognito se hornearia como cuerpo del JWKS y la funcion
  # arrancaria con un juego de claves que no parsea.
  lifecycle {
    postcondition {
      condition     = self.status_code == 200
      error_message = "No se pudo descargar el JWKS del pool de inquilinos (HTTP ${self.status_code})."
    }
  }
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
 * NO hay ruta `OPTIONS /{proxy+}`, y esa ausencia es el arreglo (hallazgo A2).
 *
 * La habia, sin autorizador, apuntando a la misma integracion que todo lo demas:
 * una puerta por la que se llegaba a la funcion sin presentar ningun token. Se
 * habia anadido por un motivo real —el navegador manda el preflight SIN
 * credenciales, asi que caia en la ruta protegida y el autorizador respondia 401,
 * y el sintoma engana: la sesion se abre bien, el token es valido, y aun asi toda
 * llamada falla con un error de red sin cuerpo—.
 *
 * Pero la solucion era otra. API Gateway responde el preflight POR SU CUENTA en
 * cuanto la API declara `cors_configuration`, sin invocar la integracion:
 *
 *   «If you configure CORS for an HTTP API, API Gateway automatically sends a
 *   response to preflight OPTIONS requests, even if there isn't an OPTIONS route
 *   configured.»
 *
 * Es decir, la ruta no hacia falta ni siquiera para lo que la motivo. Quitarla
 * cierra la unica via de invocacion sin autorizador que existia — la premisa
 * «la pasarela es la unica puerta» pasa a ser cierta.
 *
 * Consecuencia para la aplicacion: Spring ya no responde ningun preflight, asi
 * que las cabeceras las pone entera la pasarela. `CORS_ORIGENES` sigue
 * importando para las peticiones reales.
 */

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

  /**
   * Techo propio para la consulta del padrón, más bajo (hallazgo C2).
   *
   * Compartir los 50 rps de arriba significa dos cosas malas: que la consulta
   * puede consumir el presupuesto de peticiones de la API entera, y que 50 rps
   * sostenidos son ~180.000 consultas por hora contra una clave de pago de un
   * tercero.
   *
   * Dos por segundo con ráfagas de diez es holgado para lo que esto es: una
   * persona escribiendo un RUC en un formulario. Un alta normal hace una
   * consulta; dos si se equivoca al teclear.
   *
   * NO sustituye a una cuota por usuario, que es lo que de verdad cierra el
   * abuso: esto limita el caudal total, no cuánto gasta cada cual. Lo que cierra
   * C2 es el autoservicio cerrado del pool de inquilinos (identidad.tf).
   *
   * `dynamic` porque la ruta solo existe cuando hay artefacto de consultas, y
   * un `route_settings` sobre una ruta inexistente hace fallar el apply.
   */
  dynamic "route_settings" {
    for_each = local.hay_consultas ? [
      aws_apigatewayv2_route.consulta_ruc[0].route_key,
      aws_apigatewayv2_route.consulta_dni[0].route_key,
    ] : []

    content {
      route_key              = route_settings.value
      throttling_burst_limit = 10
      throttling_rate_limit  = 2
    }
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
