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
    "app.${var.dominio}",
    "api.${var.dominio}",
    "cdn.${var.dominio}",
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
