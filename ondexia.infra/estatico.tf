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


# -- Cabeceras de seguridad, con CSP ----------------------------------------

/**
 * Politica propia en vez de la gestionada de AWS (hallazgos M9 y C3).
 *
 * `SecurityHeadersPolicy` —67f7725c…— pone HSTS, X-Content-Type-Options,
 * X-Frame-Options y Referrer-Policy, y NO pone Content-Security-Policy: AWS no
 * puede adivinar de donde carga recursos cada sitio. El resultado era que las
 * cuatro distribuciones se servian sin CSP.
 *
 * Lo que compra una CSP aqui: un XSS en el SPA no puede exfiltrar el refresh
 * token a un dominio del atacante, porque `connect-src` solo admite la API y
 * Cognito. Sin ella, un solo script inyectado que llegara a ejecutarse se lleva
 * la sesion entera — que es lo que hace grave la cadena C3.
 *
 * `unsafe-inline` en `style-src` y no en `script-src`. Angular inyecta estilos
 * en linea en tiempo de ejecucion y sin eso la aplicacion sale sin formato; los
 * scripts, en cambio, son todos archivos con hash servidos desde el mismo
 * origen, asi que ahi no hace falta y no se concede. Un CSS inyectado puede
 * afear la pagina; un script inyectado se lleva la sesion.
 */
locals {
  # De donde puede hablar cada SPA. La API y Cognito viven en dominios distintos
  # del sitio, asi que sin nombrarlos aqui el navegador bloquea toda llamada — el
  # sintoma seria una aplicacion que carga y no hace nada.
  cognito_idp    = "https://cognito-idp.${var.region}.amazonaws.com"
  cognito_hosted = "https://${aws_cognito_user_pool_domain.inquilinos.domain}.auth.${var.region}.amazoncognito.com"
  /**
   * A donde deja hablar la CSP, SIN referirse a los recursos.
   *
   * Nombrar `aws_apigatewayv2_api.principal.api_endpoint` aqui crea un ciclo, y
   * el ciclo es real, no un tecnicismo de Terraform: la distribucion necesita la
   * CSP, la CSP necesita la URL de la API, y la API necesita el dominio de la
   * distribucion para su `cors_configuration`. Alguien tiene que ceder.
   *
   * Cede la CSP, y solo cuando no hay dominio propio:
   *
   *   · Con `gestionar_dns`, los dos son nombres fijos —api.<dominio>,
   *     cdn.<dominio>— y la politica es exacta.
   *   · Sin el, se usa el comodin de execute-api de la region. Es mas debil: un
   *     atacante con XSS podria exfiltrar a SU pasarela si la tiene en la misma
   *     region. Se acepta porque sin dominio propio esto es dev, y el dia que
   *     haya clientes hay dominio.
   *
   * Es la misma frontera que ya marca `prod.tfvars` con TLS 1.0: varias cosas
   * mejoran a la vez al activar el dominio.
   */
  origen_api       = var.gestionar_dns ? "https://api.${var.dominio}" : "https://*.execute-api.${var.region}.amazonaws.com"
  origen_cdn_marca = var.gestionar_dns ? "https://cdn.${var.dominio}" : "https://*.cloudfront.net"

  csp_app = join("; ", [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: ${local.origen_cdn_marca}",
    "font-src 'self'",
    "connect-src 'self' ${local.origen_api} ${local.cognito_idp} ${local.cognito_hosted}",
    "frame-ancestors 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "object-src 'none'",
  ])

  csp_panel = join("; ", [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data:",
    "font-src 'self'",
    # El comodin de execute-api SIEMPRE, tambien con dominio propio: la pasarela
    # del panel no tiene nombre a medida —solo lo tienen la app, la API de
    # clientes y el CDN de marca—, asi que no hay un valor exacto que poner. Es
    # una consola interna de dos o tres personas; el dia que se le ponga dominio,
    # esta linea se estrecha.
    "connect-src 'self' https://*.execute-api.${var.region}.amazonaws.com ${local.cognito_idp}",
    "frame-ancestors 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "object-src 'none'",
  ])

  # La landing no tiene JavaScript ni habla con nadie (doc 03 §3). Su CSP puede
  # ser la mas estricta de las cuatro, y que lo sea es ademas una prueba de que
  # sigue siendo estatica: el dia que alguien le meta un script de analitica,
  # deja de cargar.
  csp_landing = join("; ", [
    "default-src 'none'",
    "img-src 'self' data:",
    "style-src 'self' 'unsafe-inline'",
    "font-src 'self'",
    "frame-ancestors 'none'",
    "base-uri 'none'",
    "form-action 'none'",
  ])

  csp_por_sitio = {
    app     = local.csp_app
    panel   = local.csp_panel
    landing = local.csp_landing
  }
}

resource "aws_cloudfront_response_headers_policy" "sitio" {
  for_each = local.sitios

  name    = "${local.nombre}-${each.key}"
  comment = "Cabeceras de seguridad con CSP para ${each.value.descripcion}"

  security_headers_config {
    content_security_policy {
      content_security_policy = local.csp_por_sitio[each.key]
      override                = true
    }

    content_type_options {
      override = true
    }

    frame_options {
      frame_option = "DENY"
      override     = true
    }

    referrer_policy {
      referrer_policy = "strict-origin-when-cross-origin"
      override        = true
    }

    # Dos anos y subdominios. `preload` NO: entrar en la lista de precarga de los
    # navegadores es practicamente irreversible, y este dominio todavia no esta
    # en produccion.
    strict_transport_security {
      access_control_max_age_sec = 63072000
      include_subdomains         = true
      override                   = true
    }
  }
}

/**
 * El CDN de marca: contenido que SUBEN LOS CLIENTES.
 *
 * Politica aparte y mucho mas dura, porque el riesgo es distinto: aqui no
 * servimos codigo nuestro sino archivos de terceros. Sin esto, quien suba un
 * HTML con `Content-Type: text/html` —el tipo lo declara el cliente al pedir la
 * URL firmada— consigue que ese HTML se ejecute en un dominio nuestro.
 *
 * `default-src 'none'` mas `sandbox` deja el documento sin scripts, sin
 * formularios y sin origen: aunque se sirva HTML, no puede hacer nada. El
 * `Content-Disposition: attachment` remata cerrando la visualizacion en linea.
 */
resource "aws_cloudfront_response_headers_policy" "marca" {
  name    = "${local.nombre}-marca"
  comment = "Contenido subido por clientes: sin ejecucion posible"

  security_headers_config {
    content_security_policy {
      content_security_policy = "default-src 'none'; sandbox; frame-ancestors 'none'"
      override                = true
    }

    content_type_options {
      override = true
    }

    frame_options {
      frame_option = "DENY"
      override     = true
    }

    referrer_policy {
      referrer_policy = "no-referrer"
      override        = true
    }

    strict_transport_security {
      access_control_max_age_sec = 63072000
      include_subdomains         = true
      override                   = true
    }
  }

  custom_headers_config {
    items {
      header   = "Content-Disposition"
      value    = "attachment"
      override = true
    }
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

    # La cache, gestionada por AWS (CachingOptimized). Las cabeceras, propias:
    # la gestionada de seguridad no incluye CSP y AWS no puede adivinarla.
    cache_policy_id            = "658327ea-f89d-4fab-a63d-7e88639e58f6"
    response_headers_policy_id = aws_cloudfront_response_headers_policy.sitio[each.key].id
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
    response_headers_policy_id = aws_cloudfront_response_headers_policy.marca.id
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
