/**
 * El panel administrativo interno: su Lambda, su API y su identidad.
 *
 * Ver ondexia.docs/09-panel-administrativo.md §6.
 *
 * TODO SEPARADO DE LA API DE CLIENTES, Y ESE ES EL PUNTO
 *
 * Otra función, otra API, otro rol de IAM, otro rol de base de datos y otro
 * grupo de usuarios de Cognito. La alternativa —rutas nuevas en la API de
 * inquilinos— era menos trabajo y tenía un fallo que no se puede acotar después:
 * un error en el autorizador dejaría de ser un error para pasar a ser escalada de
 * privilegios entre clientes.
 *
 * Separado, la API de clientes no tiene credencial con la que leer otras cuentas
 * aunque su código se equivoque, y el panel no puede leer un comprobante aunque
 * el suyo se equivoque. No es una regla que se cumple: es una que no se puede
 * incumplir.
 */

locals {
  hay_panel = var.artefacto_panel != ""
}

# ── El artefacto ───────────────────────────────────────────────────────────

resource "aws_s3_object" "panel" {
  count = local.hay_panel ? 1 : 0

  bucket = aws_s3_bucket.artefactos.id
  # El hash en la clave hace que subir código nuevo sea un objeto nuevo, y que
  # Lambda se entere sin depender de que alguien recuerde cambiar una versión.
  key    = "panel/${filemd5(var.artefacto_panel)}.zip"
  source = var.artefacto_panel
  etag   = filemd5(var.artefacto_panel)

  tags = { Name = "${local.nombre}-artefacto-panel" }
}

# ── Identidad de ejecución ─────────────────────────────────────────────────

resource "aws_iam_role" "panel" {
  count = local.hay_panel ? 1 : 0

  name               = "${local.nombre}-panel"
  description        = "Ejecucion de la consola interna"
  assume_role_policy = data.aws_iam_policy_document.asumir_lambda.json

  tags = { Name = "${local.nombre}-panel" }
}

resource "aws_iam_role_policy_attachment" "panel_vpc" {
  count = local.hay_panel ? 1 : 0

  role       = aws_iam_role.panel[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

data "aws_iam_policy_document" "panel" {
  statement {
    sid     = "Logs"
    actions = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = [
      "arn:aws:logs:${var.region}:${data.aws_caller_identity.actual.account_id}:log-group:/aws/lambda/${local.nombre}-panel:*",
    ]
  }

  /**
   * Conectar a la base como `ondexia_panel`, y solo como ese.
   *
   * El nombre del usuario forma parte del recurso: esta política NO permite
   * conectarse como `ondexia_app` ni como el maestro. Junto con las concesiones
   * de la V9 —que no incluyen las tablas de comprobantes— es lo que hace que la
   * consola no pueda leer documentos tributarios de un cliente aunque su código
   * lo intentara.
   */
  statement {
    sid     = "ConectarBaseConIam"
    actions = ["rds-db:connect"]
    resources = [
      "arn:aws:rds-db:${var.region}:${data.aws_caller_identity.actual.account_id}:dbuser:${aws_db_instance.principal.resource_id}/ondexia_panel",
    ]
  }
}

resource "aws_iam_role_policy" "panel" {
  count = local.hay_panel ? 1 : 0

  name   = "${local.nombre}-panel"
  role   = aws_iam_role.panel[0].id
  policy = data.aws_iam_policy_document.panel.json
}

resource "aws_cloudwatch_log_group" "panel" {
  count = local.hay_panel ? 1 : 0

  name              = "/aws/lambda/${local.nombre}-panel"
  retention_in_days = var.retencion_logs_dias

  tags = { Name = "${local.nombre}-panel" }
}

# ── La función ─────────────────────────────────────────────────────────────

resource "aws_lambda_function" "panel" {
  count = local.hay_panel ? 1 : 0

  function_name = "${local.nombre}-panel"
  role          = aws_iam_role.panel[0].arn
  runtime       = "java21"
  handler       = "com.ondexia.admin.ManejadorLambda::handleRequest"

  s3_bucket        = aws_s3_bucket.artefactos.id
  s3_key           = aws_s3_object.panel[0].key
  source_code_hash = filebase64sha256(var.artefacto_panel)

  # Más memoria que la mínima porque en Lambda la CPU se asigna en proporción, y
  # aquí lo que duele es el arranque de Spring. Menos que la API porque no hay
  # Hibernate que inicializar: el panel usa SQL directo.
  memory_size = 768
  timeout     = 30

  /**
   * SIN SnapStart, a propósito.
   *
   * Son pocas invocaciones al día de gente que trabaja aquí, y un arranque en
   * frío de unos segundos es tolerable en una consola interna. A cambio se evita
   * lo que más ha costado en los despliegues de la API: publicar una versión con
   * instantánea tarda minutos, y cualquier fallo de arranque deja la versión en
   * `Failed` en vez de dar un error legible.
   *
   * Por lo mismo tampoco hay alias ni versiones publicadas: se despliega sobre
   * $LATEST, que para dos operadores es suficiente.
   */

  vpc_config {
    subnet_ids         = aws_subnet.privada[*].id
    security_group_ids = [aws_security_group.lambda.id]
  }

  environment {
    variables = {
      SPRING_PROFILES_ACTIVE = "aws"

      BD_HOST   = aws_db_instance.principal.address
      BD_PUERTO = tostring(aws_db_instance.principal.port)
      BD_NOMBRE = aws_db_instance.principal.db_name
      # Sin contraseña: la credencial es un token de IAM firmado en cada
      # conexión. El nombre tiene que coincidir letra por letra con el rol que
      # crea la V9 y con el que autoriza la política de arriba.
      BD_USUARIO = "ondexia_panel"

      # El grupo de PERSONAL. Un token de cliente está firmado por el otro pool y
      # aquí no valida: la separación es criptográfica, no de configuración.
      COGNITO_EMISOR_PERSONAL = "https://${aws_cognito_user_pool.personal.endpoint}"

      ONDEXIA_VERSION = substr(filemd5(var.artefacto_panel), 0, 12)
    }
  }

  depends_on = [aws_cloudwatch_log_group.panel]

  tags = { Name = "${local.nombre}-panel" }
}

# ── Cliente de Cognito para la consola ─────────────────────────────────────

resource "aws_cognito_user_pool_client" "panel" {
  name         = "${local.nombre}-panel"
  user_pool_id = aws_cognito_user_pool.personal.id

  generate_secret = false

  # SRP igual que el cliente de inquilinos: la interfaz alojada valida la
  # contraseña con este flujo y este mismo cliente. Sin él, un acceso correcto
  # sale como «Incorrect username or password».
  #
  # ALLOW_USER_PASSWORD_AUTH sigue fuera: aceptaría la contraseña en claro.
  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
  ]

  supported_identity_providers         = ["COGNITO"]
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]

  callback_urls = [local.origen_panel]
  logout_urls   = [local.origen_panel]

  # Una hora de acceso y ocho de sesión. Más corto que el de clientes: una
  # sesión abierta aquí ve las cuentas de todos.
  access_token_validity  = 60
  id_token_validity      = 60
  refresh_token_validity = 8
  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "hours"
  }

  prevent_user_existence_errors = "ENABLED"
}

# ── La API de la consola ───────────────────────────────────────────────────

resource "aws_apigatewayv2_api" "panel" {
  count = local.hay_panel ? 1 : 0

  name          = "${local.nombre}-panel"
  protocol_type = "HTTP"
  description   = "Consola interna de Ondexia"

  cors_configuration {
    # Solo el propio panel. Nada de comodines: esta API responde con datos de
    # todas las cuentas cliente.
    allow_origins = [local.origen_panel]
    allow_methods = ["GET", "PUT", "OPTIONS"]
    allow_headers = ["authorization", "content-type"]
    max_age       = 3600
  }

  tags = { Name = "${local.nombre}-panel" }
}

resource "aws_apigatewayv2_authorizer" "personal" {
  count = local.hay_panel ? 1 : 0

  api_id           = aws_apigatewayv2_api.panel[0].id
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]
  name             = "cognito-personal"

  jwt_configuration {
    audience = [aws_cognito_user_pool_client.panel.id]
    issuer   = "https://${aws_cognito_user_pool.personal.endpoint}"
  }
}

resource "aws_apigatewayv2_integration" "panel" {
  count = local.hay_panel ? 1 : 0

  api_id                 = aws_apigatewayv2_api.panel[0].id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.panel[0].invoke_arn
  payload_format_version = "2.0"
  timeout_milliseconds   = 30000
}

# La señal de vida, sin autorizador: la consulta el despliegue.
resource "aws_apigatewayv2_route" "panel_salud" {
  count = local.hay_panel ? 1 : 0

  api_id    = aws_apigatewayv2_api.panel[0].id
  route_key = "GET /salud"
  target    = "integrations/${aws_apigatewayv2_integration.panel[0].id}"
}

# Todo lo demás exige token del grupo de personal. El autorizador de la pasarela
# rechaza antes de invocar la función, así que un token inválido ni siquiera
# arranca un contenedor.
resource "aws_apigatewayv2_route" "panel_todo" {
  count = local.hay_panel ? 1 : 0

  api_id             = aws_apigatewayv2_api.panel[0].id
  route_key          = "$default"
  target             = "integrations/${aws_apigatewayv2_integration.panel[0].id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.personal[0].id
}

resource "aws_apigatewayv2_stage" "panel" {
  count = local.hay_panel ? 1 : 0

  api_id      = aws_apigatewayv2_api.panel[0].id
  name        = "$default"
  auto_deploy = true

  tags = { Name = "${local.nombre}-panel" }
}

resource "aws_lambda_permission" "panel" {
  count = local.hay_panel ? 1 : 0

  statement_id  = "AllowAPIGatewayInvokePanel"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.panel[0].function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.panel[0].execution_arn}/*/*"
}
