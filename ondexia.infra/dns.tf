/**
 * DNS y certificados.
 *
 * Todo este archivo está condicionado a `gestionar_dns`. Se deja apagado por
 * defecto para poder desplegar y probar sin tener el dominio apuntando
 * todavía: CloudFront sirve igual por su dominio predeterminado.
 *
 * El certificado se emite en us-east-1 por el proveedor `edge`, porque
 * CloudFront no acepta certificados de ninguna otra región. Esa es la razón
 * por la que existe ese alias.
 */

resource "aws_route53_zone" "principal" {
  count = var.gestionar_dns ? 1 : 0

  name    = var.dominio
  comment = "Ondexia ${var.entorno}"
}

resource "aws_acm_certificate" "principal" {
  count    = var.gestionar_dns ? 1 : 0
  provider = aws.edge

  domain_name = var.dominio
  subject_alternative_names = [
    "www.${var.dominio}",
    "app.${var.dominio}",
    "api.${var.dominio}",
    "cdn.${var.dominio}",
    # Faltaba (tabla de bajas): el sitio del panel tiene subdominio propio en
    # estatico.tf y sin este SAN su distribucion no podria usar el certificado.
    "panel.${var.dominio}",
  ]
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "validacion" {
  for_each = var.gestionar_dns ? {
    for opcion in aws_acm_certificate.principal[0].domain_validation_options :
    opcion.domain_name => opcion
  } : {}

  zone_id = aws_route53_zone.principal[0].zone_id
  name    = each.value.resource_record_name
  type    = each.value.resource_record_type
  records = [each.value.resource_record_value]
  ttl     = 60

  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "principal" {
  count    = var.gestionar_dns ? 1 : 0
  provider = aws.edge

  certificate_arn         = aws_acm_certificate.principal[0].arn
  validation_record_fqdns = [for r in aws_route53_record.validacion : r.fqdn]
}

# ── Registros ──────────────────────────────────────────────────────────────

resource "aws_route53_record" "app" {
  count = var.gestionar_dns ? 1 : 0

  zone_id = aws_route53_zone.principal[0].zone_id
  name    = "app.${var.dominio}"
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.sitio["app"].domain_name
    zone_id                = aws_cloudfront_distribution.sitio["app"].hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "raiz" {
  count = var.gestionar_dns ? 1 : 0

  zone_id = aws_route53_zone.principal[0].zone_id
  name    = var.dominio
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.sitio["landing"].domain_name
    zone_id                = aws_cloudfront_distribution.sitio["landing"].hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "cdn" {
  count = var.gestionar_dns ? 1 : 0

  zone_id = aws_route53_zone.principal[0].zone_id
  name    = "cdn.${var.dominio}"
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.marca.domain_name
    zone_id                = aws_cloudfront_distribution.marca.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "www" {
  count   = var.gestionar_dns ? 1 : 0
  zone_id = aws_route53_zone.principal[0].zone_id
  name    = "www.${var.dominio}"
  type    = "A"
  alias {
    name                   = aws_cloudfront_distribution.sitio["landing"].domain_name
    zone_id                = aws_cloudfront_distribution.sitio["landing"].hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "panel" {
  count   = var.gestionar_dns ? 1 : 0
  zone_id = aws_route53_zone.principal[0].zone_id
  name    = "panel.${var.dominio}"
  type    = "A"
  alias {
    name                   = aws_cloudfront_distribution.sitio["panel"].domain_name
    zone_id                = aws_cloudfront_distribution.sitio["panel"].hosted_zone_id
    evaluate_target_health = false
  }
}

# ── Dominio propio de la API ───────────────────────────────────────────────

resource "aws_apigatewayv2_domain_name" "api" {
  count = var.gestionar_dns ? 1 : 0

  domain_name = "api.${var.dominio}"

  domain_name_configuration {
    # Este certificado sí es de la región de la API, no del proveedor edge.
    certificate_arn = aws_acm_certificate_validation.api[0].certificate_arn
    endpoint_type   = "REGIONAL"
    security_policy = "TLS_1_2"
  }
}

resource "aws_acm_certificate" "api" {
  count = var.gestionar_dns ? 1 : 0

  domain_name       = "api.${var.dominio}"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "validacion_api" {
  for_each = var.gestionar_dns ? {
    for opcion in aws_acm_certificate.api[0].domain_validation_options :
    opcion.domain_name => opcion
  } : {}

  zone_id = aws_route53_zone.principal[0].zone_id
  name    = each.value.resource_record_name
  type    = each.value.resource_record_type
  records = [each.value.resource_record_value]
  ttl     = 60

  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "api" {
  count = var.gestionar_dns ? 1 : 0

  certificate_arn         = aws_acm_certificate.api[0].arn
  validation_record_fqdns = [for r in aws_route53_record.validacion_api : r.fqdn]
}

resource "aws_apigatewayv2_api_mapping" "api" {
  count = var.gestionar_dns ? 1 : 0

  api_id      = aws_apigatewayv2_api.principal.id
  domain_name = aws_apigatewayv2_domain_name.api[0].id
  stage       = aws_apigatewayv2_stage.principal.id
}

resource "aws_route53_record" "api" {
  count = var.gestionar_dns ? 1 : 0

  zone_id = aws_route53_zone.principal[0].zone_id
  name    = "api.${var.dominio}"
  type    = "A"

  alias {
    name                   = aws_apigatewayv2_domain_name.api[0].domain_name_configuration[0].target_domain_name
    zone_id                = aws_apigatewayv2_domain_name.api[0].domain_name_configuration[0].hosted_zone_id
    evaluate_target_health = false
  }
}

# ── El nombre de la API del panel ───────────────────────────────────────────
#
# POR QUE UN CERTIFICADO APARTE Y NO UN SAN MAS
#
# Anadir `api-panel` a `subject_alternative_names` del certificado principal
# parece lo barato, y es lo caro: ese atributo fuerza el REEMPLAZO del
# certificado. Eso significa emitir otro, revalidar sus seis nombres por DNS y
# cambiar el certificado de las cuatro distribuciones de CloudFront, con la
# aplicacion servida mientras tanto por uno que esta a punto de desaparecer.
#
# El certificado de `api.${var.dominio}` ya es independiente por el mismo
# motivo por el que este lo es: un dominio de pasarela necesita el certificado
# en la REGION de la API, mientras que CloudFront lo exige en us-east-1. Aqui
# coinciden, pero la separacion es la que hace que esto sea un `create` y no un
# reemplazo. Se replica ese patron en vez de inventar otro.
#
# La consola interna quedo sin nombre propio desde el primer apply (cc62356).
# No fue una decision: la auditoria detecto que faltaba `panel` en el SAN del
# certificado principal, lo anadio para el SITIO estatico, y nadie miro que el
# panel tambien tiene pasarela. El mismo descuido una capa mas abajo.

resource "aws_acm_certificate" "api_panel" {
  count = var.gestionar_dns && local.hay_panel ? 1 : 0

  domain_name       = "api-panel.${var.dominio}"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "validacion_api_panel" {
  for_each = var.gestionar_dns && local.hay_panel ? {
    for opcion in aws_acm_certificate.api_panel[0].domain_validation_options :
    opcion.domain_name => opcion
  } : {}

  zone_id = aws_route53_zone.principal[0].zone_id
  name    = each.value.resource_record_name
  type    = each.value.resource_record_type
  records = [each.value.resource_record_value]
  ttl     = 60

  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "api_panel" {
  count = var.gestionar_dns && local.hay_panel ? 1 : 0

  certificate_arn         = aws_acm_certificate.api_panel[0].arn
  validation_record_fqdns = [for r in aws_route53_record.validacion_api_panel : r.fqdn]
}

resource "aws_apigatewayv2_domain_name" "api_panel" {
  count = var.gestionar_dns && local.hay_panel ? 1 : 0

  domain_name = "api-panel.${var.dominio}"

  domain_name_configuration {
    certificate_arn = aws_acm_certificate_validation.api_panel[0].certificate_arn
    endpoint_type   = "REGIONAL"
    security_policy = "TLS_1_2"
  }
}

resource "aws_apigatewayv2_api_mapping" "api_panel" {
  count = var.gestionar_dns && local.hay_panel ? 1 : 0

  api_id      = aws_apigatewayv2_api.panel[0].id
  domain_name = aws_apigatewayv2_domain_name.api_panel[0].id
  stage       = aws_apigatewayv2_stage.panel[0].id
}

resource "aws_route53_record" "api_panel" {
  count = var.gestionar_dns && local.hay_panel ? 1 : 0

  zone_id = aws_route53_zone.principal[0].zone_id
  name    = "api-panel.${var.dominio}"
  type    = "A"

  alias {
    name                   = aws_apigatewayv2_domain_name.api_panel[0].domain_name_configuration[0].target_domain_name
    zone_id                = aws_apigatewayv2_domain_name.api_panel[0].domain_name_configuration[0].hosted_zone_id
    evaluate_target_health = false
  }
}
