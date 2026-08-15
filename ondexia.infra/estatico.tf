/**
 * Sitios estáticos y entrega de contenido.
 *
 * Tres buckets, ninguno público. CloudFront los lee por control de acceso de
 * origen, que es lo que permite que el bucket siga cerrado y aun así el
 * contenido se sirva.
 *
 * El bucket de marca —logos e imágenes por empresa— es el único que recibe
 * archivos subidos por el usuario, y se sirve desde un dominio distinto al de
 * la aplicación a propósito: un SVG puede contener JavaScript, y servido desde
 * el mismo origen sería XSS almacenado con el vector «suba su logo»
 * (DTE §5.8).
 */

data "aws_caller_identity" "actual" {}

locals {
  # Los nombres de bucket son globales en todo AWS, así que llevan el id de
  # cuenta como sufijo para no chocar con los de otro.
  sufijo = data.aws_caller_identity.actual.account_id

  # Origen del SPA. Se define aquí, junto a la distribución que lo sirve, y no
  # repetido en cada sitio que lo necesita: lo usan la configuración de CORS de
  # la API, las URL de retorno de Cognito y las variables de entorno de la
  # Lambda. Tres copias de la misma expresión es una que se queda atrás.
  origen_app = var.gestionar_dns ? "https://app.${var.dominio}" : "https://${aws_cloudfront_distribution.sitio["app"].domain_name}"

  # El de la consola interna. Lo usan las URL de retorno de su cliente de Cognito
  # y el CORS de su API, que no admite comodines: esa API responde con datos de
  # todas las cuentas cliente.
  origen_panel = var.gestionar_dns ? "https://panel.${var.dominio}" : "https://${aws_cloudfront_distribution.sitio["panel"].domain_name}"

  sitios = {
    app = {
      descripcion = "SPA de Angular"
      subdominio  = "app"
      es_spa      = true
    }
    landing = {
      descripcion = "Landing de Astro"
      subdominio  = ""
      es_spa      = false
    }
    # La consola interna. Mismo tratamiento que el SPA de clientes —bucket
    # privado detras de CloudFront, con las rutas resueltas en el cliente— y
    # separada a proposito: otro bucket y otra distribucion, de modo que una
    # politica mal puesta en uno no alcanza al otro.
    #
    # Que este publicada en internet no la abre: entrar exige un token del grupo
    # de personal, con MFA. Lo que se sirve aqui es HTML y JavaScript, no datos.
    panel = {
      descripcion = "Panel administrativo interno"
      subdominio  = "panel"
      es_spa      = true
    }
  }
}

# ── Buckets de los sitios ──────────────────────────────────────────────────

resource "aws_s3_bucket" "sitio" {
  for_each = local.sitios

  bucket = "${local.nombre}-${each.key}-${local.sufijo}"
  tags   = { Name = "${local.nombre}-${each.key}" }
}

resource "aws_s3_bucket_public_access_block" "sitio" {
  for_each = local.sitios

  bucket                  = aws_s3_bucket.sitio[each.key].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "sitio" {
  for_each = local.sitios

  bucket = aws_s3_bucket.sitio[each.key].id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# ── Bucket de marca ────────────────────────────────────────────────────────

resource "aws_s3_bucket" "marca" {
  bucket = "${local.nombre}-marca-${local.sufijo}"
  tags   = { Name = "${local.nombre}-marca" }
}

resource "aws_s3_bucket_public_access_block" "marca" {
  bucket                  = aws_s3_bucket.marca.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "marca" {
  bucket = aws_s3_bucket.marca.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

/**
 * Versionado obligatorio en el bucket de marca.
 *
 * Es la categoría D del DTE §5.8: insumo para reproducir documentos. Si un
 * cliente reemplaza su logo y se regenera el PDF de una cotización del año
 * pasado, sin versionado se reescribe la historia en silencio — sale un
 * documento que nunca existió así. Cada documento guarda con qué versión se
 * compuso.
 */
resource "aws_s3_bucket_versioning" "marca" {
  bucket = aws_s3_bucket.marca.id

  versioning_configuration {
    status = "Enabled"
  }
}

/**
 * CORS del bucket de marca.
 *
 * El navegador sube el logo DIRECTO aquí con una URL firmada, sin pasar por la
 * API. Eso es una petición entre orígenes, así que sin esta configuración el
 * navegador la bloquea antes de enviarla y el síntoma es una subida que falla
 * sin ningún error en nuestros registros —porque nunca llegó a ocurrir.
 *
 * Solo PUT, y solo desde el origen de la aplicación. GET no hace falta: los
 * logos se leen por CloudFront, que es otro dominio y no pasa por aquí.
 *
 * `ETag` en los expuestos porque es lo que devuelve S3 al terminar la subida y
 * lo único que el navegador puede leer para confirmar que fue bien.
 */
resource "aws_s3_bucket_cors_configuration" "marca" {
  bucket = aws_s3_bucket.marca.id

  cors_rule {
    allowed_methods = ["PUT"]
    allowed_origins = [local.origen_app]
    allowed_headers = ["content-type"]
    expose_headers  = ["ETag"]

    # Cachea el preflight una hora: la subida son dos peticiones y sin esto la
    # mitad de ellas son OPTIONS.
    max_age_seconds = 3600
  }
}

# ── CloudFront ─────────────────────────────────────────────────────────────

resource "aws_cloudfront_origin_access_control" "sitio" {
  for_each = local.sitios

  name                              = "${local.nombre}-${each.key}"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_origin_access_control" "marca" {
  name                              = "${local.nombre}-marca"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "sitio" {
  for_each = local.sitios

  enabled             = true
  default_root_object = "index.html"
  comment             = "${local.nombre} ${each.value.descripcion}"
  http_version        = "http2and3"

  # Todas las ubicaciones: hay un punto de presencia en Lima, y los usuarios
  # están en Perú. Dentro del nivel gratuito la diferencia de precio es nula y
  # la de latencia no.
  price_class = "PriceClass_All"

  aliases = var.gestionar_dns ? [
    each.value.subdominio == "" ? var.dominio : "${each.value.subdominio}.${var.dominio}"
  ] : []

  origin {
    origin_id                = "s3"
    domain_name              = aws_s3_bucket.sitio[each.key].bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.sitio[each.key].id
  }

  default_cache_behavior {
    target_origin_id       = "s3"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    # Políticas gestionadas por AWS: CachingOptimized y la de cabeceras de
    # seguridad. Mantenerlas propias solo añade algo que revisar.
    cache_policy_id            = "658327ea-f89d-4fab-a63d-7e88639e58f6"
    response_headers_policy_id = "67f7725c-6f97-4210-82d7-5512b31e9d03"
  }

  /**
   * Enrutado del lado del cliente.
   *
   * Angular resuelve las rutas en el navegador, así que S3 devuelve 403 o 404
   * para /ventas/facturas —ese objeto no existe— y hay que responder con el
   * index para que el enrutador tome el control. Sin esto, recargar cualquier
   * pantalla que no sea la raíz da un error.
   *
   * La landing es estática de verdad y no lo necesita: ahí un 404 es un 404.
   */
  dynamic "custom_error_response" {
    for_each = each.value.es_spa ? [403, 404] : []

    content {
      error_code            = custom_error_response.value
      response_code         = 200
      response_page_path    = "/index.html"
      error_caching_min_ttl = 10
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = !var.gestionar_dns
    acm_certificate_arn            = var.gestionar_dns ? aws_acm_certificate_validation.principal[0].certificate_arn : null
    ssl_support_method             = var.gestionar_dns ? "sni-only" : null
    minimum_protocol_version       = var.gestionar_dns ? "TLSv1.2_2021" : null
  }

  tags = { Name = "${local.nombre}-${each.key}" }
}

resource "aws_cloudfront_distribution" "marca" {
  enabled      = true
  comment      = "${local.nombre} logos e imagenes de marca"
  http_version = "http2and3"
  price_class  = "PriceClass_All"

  aliases = var.gestionar_dns ? ["cdn.${var.dominio}"] : []

  origin {
    origin_id                = "s3"
    domain_name              = aws_s3_bucket.marca.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.marca.id
  }

  default_cache_behavior {
    target_origin_id       = "s3"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    cache_policy_id            = "658327ea-f89d-4fab-a63d-7e88639e58f6"
    response_headers_policy_id = "67f7725c-6f97-4210-82d7-5512b31e9d03"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = !var.gestionar_dns
    acm_certificate_arn            = var.gestionar_dns ? aws_acm_certificate_validation.principal[0].certificate_arn : null
    ssl_support_method             = var.gestionar_dns ? "sni-only" : null
    minimum_protocol_version       = var.gestionar_dns ? "TLSv1.2_2021" : null
  }

  tags = { Name = "${local.nombre}-marca" }
}

# ── Políticas de bucket ────────────────────────────────────────────────────

data "aws_iam_policy_document" "sitio" {
  for_each = local.sitios

  statement {
    sid       = "LecturaDesdeCloudFront"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.sitio[each.key].arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    # Sin esta condición, cualquier distribución de cualquier cuenta de AWS
    # podría leer el bucket.
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.sitio[each.key].arn]
    }
  }
}

resource "aws_s3_bucket_policy" "sitio" {
  for_each = local.sitios

  bucket = aws_s3_bucket.sitio[each.key].id
  policy = data.aws_iam_policy_document.sitio[each.key].json
}

data "aws_iam_policy_document" "marca" {
  statement {
    sid       = "LecturaDesdeCloudFront"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.marca.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.marca.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "marca" {
  bucket = aws_s3_bucket.marca.id
  policy = data.aws_iam_policy_document.marca.json
}
