/**
 * ondexia.facturacion — el Emisor: firma y envía a SUNAT (doc 14).
 *
 * ══════════════════════════════════════════════════════════════════════════
 * FUERA DE LA VPC, SIN BASE DE DATOS, Y UN BUCKET ENTRE MEDIAS.
 * ══════════════════════════════════════════════════════════════════════════
 *
 * La API vive en subred privada sin NAT y SUNAT está en internet. En vez de
 * pagar la NAT (DTE §4.8), el Emisor vive fuera de la VPC como ondexia.consultas
 * y la API le habla por S3, que la API alcanza por el endpoint de puerta de
 * enlace gratuito (doc 12 §5.1, opción B):
 *
 *   API  ──put──▶ pendientes/{empresa}/{orden}.json ──evento──▶ Emisor
 *   API  ◀──get── resultados/{empresa}/{orden}.json ◀──put────  Emisor
 *                 documentos/{ruc}/...xml y R-...zip ◀──put────  Emisor
 *
 * El Emisor no ve la base: recibe un documento ya numerado y calculado, lo
 * convierte, lo firma, lo envía y devuelve un resultado. Es la separación que
 * CLAUDE.md exige («nada de firma XML en ondexia.api»), impuesta por IAM y no
 * por costumbre: ver las dos políticas de abajo.
 *
 * Lo que se compra tambien se configura (CLAUDE.md, regla 3):
 *   - El .pfx y la contraseña de cada cliente viven aquí, no en Secrets Manager
 *     (doc 12 §5.3): bucket privado, cifrado en reposo, versionado.
 *     La API los ESCRIBE (URL prefirmadas) y no los LEE: `politica_api_emision`.
 *   - El Emisor los lee y no puede escribir órdenes: `data.aws_iam_policy_document.facturacion`.
 *   - Se verifica con `terraform plan` sobre estas dos políticas; desde el
 *     repositorio no hay forma de ejercitar IAM y el doc 14 §4 lo dice.
 */

locals {
  hay_facturacion = var.artefacto_facturacion != ""

  # Los prefijos del bus. Tienen que coincidir letra por letra con
  # ClavesDelBus en el dominio: si la API escribe en un prefijo y el evento
  # escucha otro, nada falla y nada se emite.
  bus_pendientes   = "pendientes/"
  bus_resultados   = "resultados/"
  bus_errores      = "errores/"
  bus_documentos   = "documentos/"
  bus_certificados = "certificados/"
  bus_credenciales = "credenciales/"
}

# ── El bucket del bus ──────────────────────────────────────────────────────

resource "aws_s3_bucket" "emision" {
  bucket = "${local.nombre}-emision-${local.sufijo}"
  tags   = { Name = "${local.nombre}-emision" }
}

resource "aws_s3_bucket_public_access_block" "emision" {
  bucket                  = aws_s3_bucket.emision.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "emision" {
  bucket = aws_s3_bucket.emision.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

/**
 * Versionado: aquí viven XML firmados y CDR, que hay que conservar cinco años
 * (categoría D del DTE §5.8), y los certificados de los clientes, que se
 * reemplazan al renovar y conviene poder recuperar.
 */
resource "aws_s3_bucket_versioning" "emision" {
  bucket = aws_s3_bucket.emision.id

  versioning_configuration {
    status = "Enabled"
  }
}

/**
 * Las órdenes y los resultados son transitorios: la orden se borra al
 * procesarla y el resultado deja de importar cuando la API lo aplicó. Se
 * expiran solos para que el bucket no acumule basura; los documentos y los
 * certificados no tienen regla y se quedan.
 */
resource "aws_s3_bucket_lifecycle_configuration" "emision" {
  bucket = aws_s3_bucket.emision.id

  rule {
    id     = "ordenes-y-resultados"
    status = "Enabled"

    filter {
      prefix = local.bus_resultados
    }

    expiration {
      days = 30
    }

    noncurrent_version_expiration {
      noncurrent_days = 7
    }
  }

  rule {
    id     = "errores"
    status = "Enabled"

    filter {
      prefix = local.bus_errores
    }

    expiration {
      days = 90
    }
  }
}

# ── Artefacto ──────────────────────────────────────────────────────────────

resource "aws_s3_object" "facturacion" {
  count  = local.hay_facturacion ? 1 : 0
  bucket = aws_s3_bucket.artefactos.id
  key    = "facturacion/${filemd5(var.artefacto_facturacion)}.zip"
  source = var.artefacto_facturacion
  etag   = filemd5(var.artefacto_facturacion)

  tags = { Name = "${local.nombre}-artefacto-facturacion" }
}

# ── Permisos del Emisor ────────────────────────────────────────────────────

resource "aws_iam_role" "facturacion" {
  count                = local.hay_facturacion ? 1 : 0
  name                 = "${local.nombre}-facturacion"
  assume_role_policy   = data.aws_iam_policy_document.asumir_lambda.json
  permissions_boundary = aws_iam_policy.frontera_despliegue.arn

  tags = { Name = "${local.nombre}-facturacion" }
}

/**
 * Lee lo que la API escribió y los certificados; escribe lo que la API lee.
 * Ni una acción más: sin ListBucket, sin nada sobre otros buckets, sin base.
 *
 * Que este rol pueda leer `certificados/` y `credenciales/` es lo que lo
 * convierte en el único módulo capaz de firmar. El de la API, abajo, no puede.
 */
data "aws_iam_policy_document" "facturacion" {
  count = local.hay_facturacion ? 1 : 0

  statement {
    sid       = "Registros"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.facturacion[0].arn}:*"]
  }

  statement {
    sid     = "LeerOrdenesYCredenciales"
    actions = ["s3:GetObject"]
    resources = [
      "${aws_s3_bucket.emision.arn}/${local.bus_pendientes}*",
      "${aws_s3_bucket.emision.arn}/${local.bus_certificados}*",
      "${aws_s3_bucket.emision.arn}/${local.bus_credenciales}*",
    ]
  }

  statement {
    sid     = "EscribirDocumentosYResultados"
    actions = ["s3:PutObject"]
    resources = [
      "${aws_s3_bucket.emision.arn}/${local.bus_documentos}*",
      "${aws_s3_bucket.emision.arn}/${local.bus_resultados}*",
      "${aws_s3_bucket.emision.arn}/${local.bus_errores}*",
    ]
  }

  statement {
    sid       = "BorrarOrdenProcesada"
    actions   = ["s3:DeleteObject"]
    resources = ["${aws_s3_bucket.emision.arn}/${local.bus_pendientes}*"]
  }
}

resource "aws_iam_role_policy" "facturacion" {
  count  = local.hay_facturacion ? 1 : 0
  name   = "${local.nombre}-facturacion"
  role   = aws_iam_role.facturacion[0].id
  policy = data.aws_iam_policy_document.facturacion[0].json
}

resource "aws_cloudwatch_log_group" "facturacion" {
  count             = local.hay_facturacion ? 1 : 0
  name              = "/aws/lambda/${local.nombre}-facturacion"
  retention_in_days = var.retencion_logs_dias

  tags = { Name = "${local.nombre}-facturacion" }
}

# ── Lo que la API puede hacer en el bus ────────────────────────────────────

/**
 * Escribe órdenes; lee resultados y documentos (para las URL de descarga);
 * escribe certificados y credenciales (las URL prefirmadas de PUT ejecutan con
 * estas credenciales) y solo puede LISTAR esos dos prefijos, para confirmar
 * que el navegador subió los archivos sin poder leerlos.
 *
 * No hay `s3:GetObject` sobre certificados/ ni credenciales/, y esa ausencia es
 * la frontera: con el archivo y su contraseña la API podría firmar.
 */
data "aws_iam_policy_document" "api_emision" {
  statement {
    sid     = "EscribirEnElBus"
    actions = ["s3:PutObject"]
    resources = [
      "${aws_s3_bucket.emision.arn}/${local.bus_pendientes}*",
      "${aws_s3_bucket.emision.arn}/${local.bus_certificados}*",
      "${aws_s3_bucket.emision.arn}/${local.bus_credenciales}*",
    ]
  }

  statement {
    sid     = "LeerDelBus"
    actions = ["s3:GetObject"]
    resources = [
      "${aws_s3_bucket.emision.arn}/${local.bus_resultados}*",
      "${aws_s3_bucket.emision.arn}/${local.bus_documentos}*",
    ]
  }

  statement {
    sid       = "ConfirmarSubidas"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.emision.arn]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["${local.bus_certificados}*", "${local.bus_credenciales}*"]
    }
  }
}

resource "aws_iam_role_policy" "api_emision" {
  name   = "${local.nombre}-api-emision"
  role   = aws_iam_role.api.id
  policy = data.aws_iam_policy_document.api_emision.json
}

# ── Función ────────────────────────────────────────────────────────────────

resource "aws_lambda_function" "facturacion" {
  count         = local.hay_facturacion ? 1 : 0
  function_name = "${local.nombre}-facturacion"
  role          = aws_iam_role.facturacion[0].arn

  s3_bucket        = aws_s3_bucket.artefactos.id
  s3_key           = aws_s3_object.facturacion[0].key
  source_code_hash = filebase64sha256(var.artefacto_facturacion)

  runtime = var.runtime_api
  handler = "com.ondexia.facturacion.lambda.ManejadorDeOrdenes::handleRequest"

  /**
   * Más memoria que consultas: firmar XML y cargar las plantillas de xbuilder
   * pesa, y en Lambda la CPU va con la memoria. Un arranque en frío con 512 MB
   * se medía en más de diez segundos en el riesgo de doc 12 §11; SnapStart lo
   * tapa, pero la firma en sí también va más rápida con más CPU.
   */
  memory_size = var.memoria_facturacion_mb

  /**
   * Un envío a SUNAT tarda segundos y la beta a veces decenas. Sin la pasarela
   * detrás no hay 29 s que respetar; con 90 caben el arranque, la firma y una
   * SUNAT lenta, y si se agota Lambda reintenta el evento de S3 por su cuenta.
   */
  timeout = 90

  reserved_concurrent_executions = var.concurrencia_reservada_consultas

  snap_start {
    apply_on = "PublishedVersions"
  }

  publish = true

  environment {
    variables = {
      SPRING_PROFILES_ACTIVE = "aws,${var.entorno}"
      ONDEXIA_VERSION        = substr(filemd5(var.artefacto_facturacion), 0, 12)
      BUCKET_EMISION         = aws_s3_bucket.emision.id
    }
  }

  depends_on = [aws_cloudwatch_log_group.facturacion]

  tags = { Name = "${local.nombre}-facturacion" }
}

resource "aws_lambda_alias" "facturacion" {
  count            = local.hay_facturacion ? 1 : 0
  name             = "activo"
  description      = "Version que procesa las ordenes"
  function_name    = aws_lambda_function.facturacion[0].function_name
  function_version = aws_lambda_function.facturacion[0].version
}

/**
 * S3 invoca el ALIAS, nunca $LATEST: la instantánea de SnapStart pertenece a
 * la versión publicada. Solo los objetos de `pendientes/`: los demás prefijos
 * los escribe el propio Emisor, y escucharlos sería invocarse a sí mismo.
 */
resource "aws_lambda_permission" "facturacion_s3" {
  count         = local.hay_facturacion ? 1 : 0
  statement_id  = "AllowS3InvokeFacturacion"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.facturacion[0].function_name
  qualifier     = aws_lambda_alias.facturacion[0].name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.emision.arn
}

resource "aws_s3_bucket_notification" "emision" {
  count  = local.hay_facturacion ? 1 : 0
  bucket = aws_s3_bucket.emision.id

  lambda_function {
    lambda_function_arn = aws_lambda_alias.facturacion[0].arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = local.bus_pendientes
    filter_suffix       = ".json"
  }

  depends_on = [aws_lambda_permission.facturacion_s3]
}

/**
 * Una orden que revienta antes de producir resultado deja el comprobante en
 * cola hasta que alguien reintente. El Emisor escribe un resultado incluso en
 * ese caso; si ni eso pudo, la función falla y esta alarma lo dice. Mismo tema
 * de SNS que las demás.
 */
resource "aws_cloudwatch_metric_alarm" "errores_facturacion" {
  count               = local.hay_facturacion ? 1 : 0
  alarm_name          = "${local.nombre}-errores-facturacion"
  alarm_description   = "El Emisor esta fallando: hay ordenes de emision sin resultado"
  namespace           = "AWS/Lambda"
  metric_name         = "Errors"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = aws_lambda_function.facturacion[0].function_name
  }

  alarm_actions = [aws_sns_topic.alertas.arn]
  ok_actions    = [aws_sns_topic.alertas.arn]
}
