/**
 * Observabilidad y control de gasto.
 *
 * Los grupos de logs se crean aquí a propósito. Si se deja que Lambda cree el
 * suyo, nace **sin caducidad** y los logs se acumulan para siempre: es la fuga
 * de costo más común en proyectos pequeños (DTE §4.6).
 */

resource "aws_cloudwatch_log_group" "api" {
  name              = "/aws/lambda/${local.nombre}-api"
  retention_in_days = var.retencion_logs_dias

  tags = { Name = "${local.nombre}-api" }
}

resource "aws_cloudwatch_log_group" "api_gateway" {
  name              = "/aws/apigateway/${local.nombre}"
  retention_in_days = var.retencion_logs_dias

  tags = { Name = "${local.nombre}-api-gateway" }
}

/**
 * Presupuesto con alerta.
 *
 * Obligatorio antes del primer despliegue. Con esta arquitectura no hay forma
 * realista de dispararse, pero un bucle accidental o unos logs sin retención
 * son la fuga clásica, y hay que enterarse por correo y no por la factura.
 *
 * Dos avisos: al 80 % de lo gastado, y al 100 % de lo *previsto* a fin de mes,
 * que llega antes y por eso es el útil.
 */
resource "aws_budgets_budget" "mensual" {
  name         = "${local.nombre}-mensual"
  budget_type  = "COST"
  limit_amount = tostring(var.tope_presupuesto_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.correo_alertas]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.correo_alertas]
  }
}

/**
 * Alarma de errores de la API.
 *
 * Sin destinatario todavía: se crea el tema de SNS y se suscribe el correo,
 * que exige confirmar desde el buzón. Cinco errores en cinco minutos es un
 * umbral deliberadamente laxo para la v1 — con dos usuarios, cinco errores
 * seguidos no son ruido.
 */
resource "aws_sns_topic" "alertas" {
  name = "${local.nombre}-alertas"
}

resource "aws_sns_topic_subscription" "alertas_correo" {
  topic_arn = aws_sns_topic.alertas.arn
  protocol  = "email"
  endpoint  = var.correo_alertas
}

resource "aws_cloudwatch_metric_alarm" "errores_api" {
  alarm_name          = "${local.nombre}-errores-api"
  alarm_description   = "La Lambda de la API esta fallando"
  namespace           = "AWS/Lambda"
  metric_name         = "Errors"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 5
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = aws_lambda_function.api.function_name
  }

  alarm_actions = [aws_sns_topic.alertas.arn]
  ok_actions    = [aws_sns_topic.alertas.arn]
}

/**
 * Almacenamiento de la base.
 *
 * Que se llene el disco deja el sistema inoperativo y no se anuncia. El
 * autoescalado da margen, pero enterarse antes evita descubrirlo por una
 * llamada del cliente.
 */
resource "aws_cloudwatch_metric_alarm" "espacio_bd" {
  alarm_name          = "${local.nombre}-espacio-bd"
  alarm_description   = "Queda poco espacio libre en la base de datos"
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 2147483648 # 2 GB
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.principal.id
  }

  alarm_actions = [aws_sns_topic.alertas.arn]
}
