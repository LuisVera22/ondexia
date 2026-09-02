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

# La función de migraciones se invoca pocas veces, pero su log es justo el que
# se consulta cuando algo salió mal en un despliegue. Retención propia por si
# conviene guardarlo más tiempo que el tráfico normal.
resource "aws_cloudwatch_log_group" "migraciones" {
  name              = "/aws/lambda/${local.nombre}-migraciones"
  retention_in_days = var.retencion_logs_dias

  tags = { Name = "${local.nombre}-migraciones" }
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

# ── CloudTrail (hallazgo M12) ──────────────────────────────────────────────

/**
 * Quien hizo que en la cuenta de AWS. No habia nada.
 *
 * Sin CloudTrail, la unica huella de una accion sobre la infraestructura son los
 * registros de la aplicacion —que solo cubren la aplicacion— y el estado de
 * Terraform, que dice como quedo todo pero no quien lo cambio ni cuando. Si
 * manana aparece un rol que nadie recuerda haber creado, no hay forma de saber
 * de donde salio.
 *
 * Importa mas aqui que en una cuenta cualquiera por lo que este proyecto emite:
 * un comprobante con valor tributario. La pregunta «quien toco esto» tiene un
 * destinatario legal, no solo tecnico.
 *
 * COSTO: el primer trail de gestion de una cuenta es GRATIS —AWS no cobra por
 * los eventos de gestion del primer trail—, y lo que se paga es el
 * almacenamiento en S3: unos megabytes al mes para una cuenta de este tamano. No
 * se registran eventos de DATOS (lecturas de objetos de S3), que si se cobran y
 * en este proyecto serian ruido.
 */
resource "aws_s3_bucket" "cloudtrail" {
  bucket = "${local.nombre}-cloudtrail-${local.sufijo}"

  tags = { Name = "${local.nombre}-cloudtrail" }
}

resource "aws_s3_bucket_public_access_block" "cloudtrail" {
  bucket = aws_s3_bucket.cloudtrail.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "cloudtrail" {
  bucket = aws_s3_bucket.cloudtrail.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Sin versionado y con caducidad: los registros de auditoria se leen semanas
# despues, no anos, y la validacion de integridad del propio trail ya detecta
# manipulaciones sin necesidad de versiones.
resource "aws_s3_bucket_lifecycle_configuration" "cloudtrail" {
  bucket = aws_s3_bucket.cloudtrail.id

  rule {
    id     = "caducar"
    status = "Enabled"

    filter {}

    expiration {
      days = var.entorno == "prod" ? 365 : 90
    }
  }
}

data "aws_iam_policy_document" "cloudtrail" {
  statement {
    sid     = "PermitirComprobacionDeAcl"
    actions = ["s3:GetBucketAcl"]

    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }

    resources = [aws_s3_bucket.cloudtrail.arn]
  }

  statement {
    sid     = "PermitirEscritura"
    actions = ["s3:PutObject"]

    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }

    resources = ["${aws_s3_bucket.cloudtrail.arn}/AWSLogs/${data.aws_caller_identity.actual.account_id}/*"]

    condition {
      test     = "StringEquals"
      variable = "s3:x-amz-acl"
      values   = ["bucket-owner-full-control"]
    }
  }

  # Nada sin TLS, ni siquiera para el propio servicio.
  statement {
    sid     = "NadaSinTls"
    effect  = "Deny"
    actions = ["s3:*"]

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }

    resources = [
      aws_s3_bucket.cloudtrail.arn,
      "${aws_s3_bucket.cloudtrail.arn}/*",
    ]

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "cloudtrail" {
  bucket = aws_s3_bucket.cloudtrail.id
  policy = data.aws_iam_policy_document.cloudtrail.json
}

resource "aws_cloudtrail" "principal" {
  name           = "${local.nombre}-auditoria"
  s3_bucket_name = aws_s3_bucket.cloudtrail.id

  /**
   * Multi-region, y no es por completismo.
   *
   * Un trail de una sola region no ve lo que pase en las demas, y crear recursos
   * en una region que nadie mira es la forma habitual de usar una cuenta ajena
   * sin que se note. Los eventos globales —IAM, STS— solo se registran con esta
   * opcion.
   */
  is_multi_region_trail         = true
  include_global_service_events = true

  /**
   * Validacion de integridad: CloudTrail firma un resumen por hora.
   *
   * Sin esto, quien tenga acceso de escritura al bucket puede borrar o editar
   * los registros que le incomoden y no queda rastro. Con esto, el archivo de
   * resumen no cuadra.
   */
  enable_log_file_validation = true

  depends_on = [aws_s3_bucket_policy.cloudtrail]

  tags = { Name = "${local.nombre}-auditoria" }
}
