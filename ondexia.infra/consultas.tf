/**
 * ondexia.consultas — la única función que sale a internet.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * SIN vpc_config, Y ESO ES TODO EL PUNTO DE ESTE ARCHIVO.
 * ══════════════════════════════════════════════════════════════════════════
 *
 * La Lambda de la API está en subred privada sin NAT: no tiene salida a
 * internet, y darle una cuesta 32 USD/mes de NAT Gateway o 7,30 de endpoint de
 * interfaz (DTE §4.8). Esta función no entra en la VPC, así que sale gratis.
 *
 * Lo que pierde por estar fuera: no puede ver la base de datos. Y no le hace
 * falta — consulta el padrón de SUNAT y firma el resultado; nada más.
 *
 * Lleva Spring Boot, igual que la API, y por eso lleva también SnapStart: sin él
 * un Spring Boot en Lambda arranca en 6-10 s (DT-02), y esta consulta ocurre
 * mientras alguien espera con el cursor en un formulario de registro.
 *
 * Ver ondexia.docs/11-registro-de-empresa.md y DT-19 del DTE.
 */

locals {
  hay_consultas = var.artefacto_consultas != ""

  /**
   * Los parámetros de SSM, por convención de nombre y no por variable.
   *
   * Sus VALORES no los crea Terraform, y es deliberado: un parámetro creado
   * desde aquí guarda su valor en el estado, y el estado vive en S3. Se crean
   * una vez con la CLI (ver el README de este directorio) y aquí solo se
   * nombran.
   *
   * La clave pública es la excepción y va como String, no SecureString: no es
   * un secreto. Está en SSM junto a la privada solo para que el par no se
   * separe — que la pública de un entorno acabe verificando firmas de otro es
   * un fallo silencioso y desconcertante.
   */
  ssm_prefijo = "/ondexia/${var.entorno}/consultas"

  ssm_firma_privada = "${local.ssm_prefijo}/firma-privada"
  ssm_firma_publica = "${local.ssm_prefijo}/firma-publica"
  ssm_decolecta     = "${local.ssm_prefijo}/decolecta"
  ssm_apiperu       = "${local.ssm_prefijo}/apiperu"

  arn_parametros = "arn:aws:ssm:${var.region}:${data.aws_caller_identity.actual.account_id}:parameter${local.ssm_prefijo}/*"
}

/**
 * La clave pública, leída para pasarla a la API.
 *
 * La API no puede leer SSM en ejecución —está en subred privada sin NAT—, así
 * que el valor viaja en su variable de entorno. Eso significa que queda en el
 * estado de Terraform, y aquí no importa: una clave pública no es un secreto.
 *
 * Es justamente lo que permitió elegir firma asimétrica en vez de un HMAC, que
 * habría exigido el mismo secreto en las dos partes y por tanto un secreto en
 * el estado.
 */
data "aws_ssm_parameter" "firma_publica" {
  count = local.hay_consultas ? 1 : 0
  name  = local.ssm_firma_publica
}

# ── Artefacto ──────────────────────────────────────────────────────────────

resource "aws_s3_object" "consultas" {
  count  = local.hay_consultas ? 1 : 0
  bucket = aws_s3_bucket.artefactos.id

  # Con el hash en la clave, un artefacto distinto es un objeto distinto y la
  # función se actualiza de verdad. Mismo motivo que en api.tf.
  key    = "consultas/${filemd5(var.artefacto_consultas)}.zip"
  source = var.artefacto_consultas
  etag   = filemd5(var.artefacto_consultas)

  tags = { Name = "${local.nombre}-artefacto-consultas" }
}

# ── Permisos ───────────────────────────────────────────────────────────────

resource "aws_iam_role" "consultas" {
  count              = local.hay_consultas ? 1 : 0
  name               = "${local.nombre}-consultas"
  assume_role_policy = data.aws_iam_policy_document.asumir_lambda.json

  tags = { Name = "${local.nombre}-consultas" }
}

/**
 * Rol propio, no el de la API, y es una decisión de DT-19.
 *
 * Los permisos de IAM son por función. Compartir rol le daría a esta función el
 * acceso a RDS y a los secretos de la API, y a la API el acceso a las claves de
 * los proveedores. El argumento vale en los dos sentidos: quien consulta el
 * padrón no debe poder leer un certificado digital.
 */
data "aws_iam_policy_document" "consultas" {
  count = local.hay_consultas ? 1 : 0

  statement {
    sid = "Registros"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = ["${aws_cloudwatch_log_group.consultas[0].arn}:*"]
  }

  statement {
    sid       = "LeerParametros"
    actions   = ["ssm:GetParameter"]
    resources = [local.arn_parametros]
  }

  /**
   * Descifrar los SecureString.
   *
   * Con la llave gestionada de AWS para SSM —que es gratis, a diferencia de una
   * propia a 1 USD/mes—. La condición limita el uso a través de SSM: sin ella,
   * este rol podría descifrar cualquier cosa cifrada con esa llave en la
   * cuenta.
   */
  statement {
    sid       = "DescifrarParametros"
    actions   = ["kms:Decrypt"]
    resources = ["arn:aws:kms:${var.region}:${data.aws_caller_identity.actual.account_id}:alias/aws/ssm"]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${var.region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "consultas" {
  count  = local.hay_consultas ? 1 : 0
  name   = "${local.nombre}-consultas"
  role   = aws_iam_role.consultas[0].id
  policy = data.aws_iam_policy_document.consultas[0].json
}

/**
 * Sin AWSLambdaVPCAccessExecutionRole, al contrario que la API.
 *
 * Esa política existe para crear y borrar interfaces de red en la VPC. Esta
 * función no entra en ninguna, así que no le hace falta — y concedérsela «por
 * simetría» sería dar permiso para manipular la red a algo que solo hace
 * peticiones HTTP.
 */

resource "aws_cloudwatch_log_group" "consultas" {
  count             = local.hay_consultas ? 1 : 0
  name              = "/aws/lambda/${local.nombre}-consultas"
  retention_in_days = var.retencion_logs_dias

  tags = { Name = "${local.nombre}-consultas" }
}

# ── Función ────────────────────────────────────────────────────────────────

resource "aws_lambda_function" "consultas" {
  count         = local.hay_consultas ? 1 : 0
  function_name = "${local.nombre}-consultas"
  role          = aws_iam_role.consultas[0].arn

  s3_bucket        = aws_s3_bucket.artefactos.id
  s3_key           = aws_s3_object.consultas[0].key
  source_code_hash = filebase64sha256(var.artefacto_consultas)

  runtime = var.runtime_api
  handler = "com.ondexia.consultas.lambda.ManejadorLambda::handleRequest"

  memory_size = var.memoria_consultas_mb

  /**
   * Más corto que los 29 s de la pasarela, y a propósito.
   *
   * Dentro hay dos proveedores en cascada a 6 s cada uno. Con 20 s, si los dos
   * agotan su tiempo la función responde su propio error antes de que la
   * pasarela corte por su cuenta con un 504 opaco — que es el error más difícil
   * de diagnosticar de los dos.
   */
  timeout = 20

  /**
   * SnapStart, por el mismo motivo que en la API.
   *
   * Lambda toma la instantánea del microVM DESPUÉS de la inicialización —con el
   * contexto de Spring ya construido— y la restaura en cada arranque en frío en
   * lugar de reconstruirlo: de 6-10 s a 200-400 ms, sin costo adicional en
   * runtimes Java gestionados.
   *
   * Solo actúa sobre VERSIONES PUBLICADAS, de ahí `publish = true` y el alias de
   * más abajo. Con la integración apuntando a $LATEST se puede activar y no
   * servir de nada — sin ningún aviso, solo arranques lentos.
   *
   * De la tabla de trampas de §4.2, aquí aplica una y media: no hay pool de
   * conexiones ni certificado de cliente, y la firma es Ed25519, que es
   * DETERMINISTA (RFC 8032) — así que una semilla de SecureRandom clonada entre
   * instancias no puede repetir un nonce, porque no hay nonce. Lo que sí queda en
   * la instantánea es nuestra clave privada de firma; se acepta a cambio de que
   * una clave mal puesta falle al desplegar y no en el primer alta.
   */
  snap_start {
    apply_on = "PublishedVersions"
  }

  publish = true


  environment {
    variables = {
      /**
       * Los NOMBRES de los parámetros, no sus valores.
       *
       * Es la diferencia que hace que esto no sea una variable de entorno con
       * un secreto dentro: aquí solo hay rutas de SSM, que no sirven de nada
       * sin el rol. Una clave en una variable de entorno la ve cualquiera con
       * lambda:GetFunctionConfiguration, y el cifrado en reposo no cambia eso
       * porque Lambda la descifra antes de ejecutar el código.
       */
      /**
       * El perfil, igual que en la API.
       *
       * Sin él, application.yml usa sus valores por omisión, que son los de
       * desarrollo. Aquí eso significaría no encontrar ningún parámetro y no
       * arrancar — que al menos es ruidoso, pero el mensaje no mencionaría el
       * perfil.
       */
      SPRING_PROFILES_ACTIVE = "aws,${var.entorno}"

      # Qué build está sirviendo. Además cambia con cada artefacto, así que un
      # despliegue nuevo siempre modifica $LATEST y Lambda publica versión nueva:
      # sin esto, republicar tras una versión fallida es imposible.
      ONDEXIA_VERSION = substr(filemd5(var.artefacto_consultas), 0, 12)

      CONSULTAS_FIRMA_PARAMETRO     = local.ssm_firma_privada
      CONSULTAS_DECOLECTA_PARAMETRO = local.ssm_decolecta

      /**
       * El relevo, solo si se contrató.
       *
       * Con la cadena vacía el código no lo mete en la cascada y lo dice en el
       * registro al arrancar. Sin esa línea, un entorno con un solo proveedor
       * es indistinguible de uno con el nombre del parámetro mal escrito.
       */
      CONSULTAS_APIPERU_PARAMETRO = var.usar_apiperu ? local.ssm_apiperu : ""
    }
  }

  depends_on = [aws_cloudwatch_log_group.consultas]

  tags = { Name = "${local.nombre}-consultas" }
}

# ── Ruta en la pasarela ────────────────────────────────────────────────────

resource "aws_apigatewayv2_integration" "consultas" {
  count            = local.hay_consultas ? 1 : 0
  api_id           = aws_apigatewayv2_api.principal.id
  integration_type = "AWS_PROXY"

  # El alias, no la función. Invocar la función a secas ejecuta $LATEST, que no
  # lleva instantánea de SnapStart.
  integration_uri        = aws_lambda_alias.consultas[0].invoke_arn
  payload_format_version = "2.0"
  timeout_milliseconds   = 25000
}

/**
 * La ruta, en la MISMA pasarela que la API.
 *
 * Es lo que hace que esta función no necesite validar tokens: hereda el
 * autorizador de Cognito que ya existe, y llega invocada solo si el token era
 * válido. Con una Function URL propia habría un origen nuevo, con su CORS y su
 * validación de JWT — una segunda implementación de la autenticación.
 *
 * Una ruta específica gana sobre `ANY /{proxy+}`, igual que ya hacen
 * `OPTIONS /{proxy+}` y `GET /salud` en api.tf. No hace falta tocar esa ruta.
 *
 * El preflight lo sigue atendiendo `OPTIONS /{proxy+}`, que va a Spring. Spring
 * responde a un origen que reconoce sin mirar la ruta, así que esto funciona
 * sin ruta de preflight propia.
 */
resource "aws_apigatewayv2_route" "consulta_ruc" {
  count              = local.hay_consultas ? 1 : 0
  api_id             = aws_apigatewayv2_api.principal.id
  route_key          = "GET /consultas/ruc/{ruc}"
  target             = "integrations/${aws_apigatewayv2_integration.consultas[0].id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito.id
}

/**
 * El alias es lo que se invoca. Nunca $LATEST.
 *
 * Un alias apunta a una versión inmutable y concreta, y es la pieza que hace que
 * SnapStart tenga efecto: la instantánea pertenece a la versión, no a la función.
 */
resource "aws_lambda_alias" "consultas" {
  count            = local.hay_consultas ? 1 : 0
  name             = "activo"
  description      = "Version que sirve el trafico"
  function_name    = aws_lambda_function.consultas[0].function_name
  function_version = aws_lambda_function.consultas[0].version
}

resource "aws_lambda_permission" "consultas_api_gateway" {
  count         = local.hay_consultas ? 1 : 0
  statement_id  = "AllowAPIGatewayInvokeConsultas"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.consultas[0].function_name
  qualifier     = aws_lambda_alias.consultas[0].name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.principal.execution_arn}/*/*"
}
