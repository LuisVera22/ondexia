output "url_app" {
  description = "Donde queda servida la SPA."
  value       = var.gestionar_dns ? "https://app.${var.dominio}" : "https://${aws_cloudfront_distribution.sitio["app"].domain_name}"
}

output "url_landing" {
  description = "Donde queda servida la landing."
  value       = var.gestionar_dns ? "https://${var.dominio}" : "https://${aws_cloudfront_distribution.sitio["landing"].domain_name}"
}

output "url_api" {
  description = "Base de la API. La sonda de vida cuelga de /salud."
  value       = var.gestionar_dns ? "https://api.${var.dominio}" : aws_apigatewayv2_api.principal.api_endpoint
}

output "url_cdn" {
  description = "Origen de logos e imágenes de marca."
  value       = var.gestionar_dns ? "https://cdn.${var.dominio}" : "https://${aws_cloudfront_distribution.marca.domain_name}"
}

output "buckets" {
  description = "Destinos de despliegue del contenido estático."
  value = {
    app     = aws_s3_bucket.sitio["app"].id
    landing = aws_s3_bucket.sitio["landing"].id
    marca   = aws_s3_bucket.marca.id
  }
}

output "distribuciones_cloudfront" {
  description = "Identificadores para invalidar la caché tras desplegar."
  value = {
    app     = aws_cloudfront_distribution.sitio["app"].id
    landing = aws_cloudfront_distribution.sitio["landing"].id
    marca   = aws_cloudfront_distribution.marca.id
  }
}

output "cognito" {
  description = "Lo que el SPA necesita para configurar el acceso."
  value = {
    pool_inquilinos = aws_cognito_user_pool.inquilinos.id
    cliente_spa     = aws_cognito_user_pool_client.spa.id
    emisor          = "https://${aws_cognito_user_pool.inquilinos.endpoint}"
    pool_personal   = aws_cognito_user_pool.personal.id
  }
}

output "servidores_dns" {
  description = <<-TEXTO
    Servidores de nombres de la zona. Hay que cargarlos en el registrador del
    dominio o nada resuelve. Vacío mientras gestionar_dns esté apagado.
  TEXTO
  value       = var.gestionar_dns ? aws_route53_zone.principal[0].name_servers : []
}

output "base_datos" {
  description = "Punto de conexión. Solo alcanzable desde dentro de la VPC."
  value = {
    host   = aws_db_instance.principal.address
    puerto = aws_db_instance.principal.port
    nombre = aws_db_instance.principal.db_name
  }
}

output "recordatorios" {
  description = "Lo que Terraform no puede hacer por ti."
  value = [
    "Confirmar la suscripción de correo al tema de SNS: llega un mensaje de AWS y hay que pulsar el enlace, o las alarmas no avisan a nadie.",
    "Poner el MFA del grupo de personal en ON cuando el primer usuario tenga su TOTP configurado.",
    "Pasar la cuenta al plan de pago antes de que venza el periodo gratuito: el plan gratuito cierra la cuenta sola.",
    var.gestionar_dns ? "Cargar los servidores de nombres en el registrador del dominio." : "gestionar_dns esta apagado: se sirve por los dominios predeterminados de CloudFront.",
  ]
}
