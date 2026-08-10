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
}

# Permisos de red para colocar la función dentro de la VPC.
resource "aws_iam_role_policy_attachment" "api_vpc" {
  role       = aws_iam_role.api.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

data "aws_iam_policy_document" "api" {
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

  filename         = local.ruta_artefacto
  source_code_hash = filebase64sha256(local.ruta_artefacto)

  runtime = local.hay_backend ? var.runtime_api : "nodejs22.x"
  handler = local.hay_backend ? var.handler_api : "index.handler"

  memory_size = local.hay_backend ? var.memoria_api_mb : 128
  timeout     = 30

  reserved_concurrent_executions = var.concurrencia_reservada_api

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
      SPRING_PROFILES_ACTIVE = var.entorno
      BD_HOST                = aws_db_instance.principal.address
      BD_PUERTO              = tostring(aws_db_instance.principal.port)
      BD_NOMBRE              = aws_db_instance.principal.db_name
      BD_USUARIO             = aws_db_instance.principal.username
      BD_CONTRASENA          = random_password.bd.result
      COGNITO_POOL_ID        = aws_cognito_user_pool.inquilinos.id
      COGNITO_CLIENTE_ID     = aws_cognito_user_pool_client.spa.id
      BUCKET_MARCA           = aws_s3_bucket.marca.id
      CDN_MARCA              = var.gestionar_dns ? "https://cdn.${var.dominio}" : "https://${aws_cloudfront_distribution.marca.domain_name}"
    }
  }

  depends_on = [
    aws_iam_role_policy_attachment.api_vpc,
    aws_cloudwatch_log_group.api,
  ]

  tags = { Name = "${local.nombre}-api" }
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
    allow_origins = var.gestionar_dns ? ["https://app.${var.dominio}"] : ["https://${aws_cloudfront_distribution.sitio["app"].domain_name}"]
    allow_methods = ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"]
    allow_headers = ["authorization", "content-type"]
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
  api_id                 = aws_apigatewayv2_api.principal.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.api.invoke_arn
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

resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.api.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.principal.execution_arn}/*/*"
}
