output "url_app" {
  description = "Donde queda servida la SPA."
  value       = local.origen_app
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
    # Base de la interfaz alojada: de aquí cuelgan /oauth2/authorize,
    # /oauth2/token y /logout. Es lo que el SPA escribe en su config.json.
    dominio = "https://${aws_cognito_user_pool_domain.inquilinos.domain}.auth.${var.region}.amazoncognito.com"
  }
}

output "configuracion_spa" {
  description = <<-TEXTO
    Contenido de public/config.json para este entorno. El SPA lo lee al
    arrancar, así que el mismo artefacto vale para dev y para prod sin
    reconstruirlo — que es justo lo que permite promocionar a producción
    exactamente lo que se probó.

    Se genera con:
      terraform output -json configuracion_spa > ../apps/frontend/ondexia.web/dist/ondexia-web/browser/config.json
  TEXTO
  value = {
    api = var.gestionar_dns ? "https://api.${var.dominio}" : aws_apigatewayv2_api.principal.api_endpoint
    cognito = {
      dominio   = "https://${aws_cognito_user_pool_domain.inquilinos.domain}.auth.${var.region}.amazoncognito.com"
      clienteId = aws_cognito_user_pool_client.spa.id
    }
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

output "funcion_migraciones" {
  description = <<-TEXTO
    Función que aplica las migraciones de Flyway. Hay que invocarla después de
    cada despliegue que traiga migraciones nuevas — la API ya no migra al
    arrancar:

      aws lambda invoke --function-name <este valor> --payload '{}' salida.json

    Vacío mientras no haya artefacto de backend desplegado.
  TEXTO
  value       = local.hay_backend ? aws_lambda_function.migraciones[0].function_name : ""
}

output "recordatorios" {
  description = "Lo que Terraform no puede hacer por ti."
  value = [
    "Confirmar la suscripción de correo al tema de SNS: llega un mensaje de AWS y hay que pulsar el enlace, o las alarmas no avisan a nadie.",
    local.hay_backend ? "Invocar la funcion de migraciones (output funcion_migraciones) antes de probar la API: el esquema no existe hasta entonces." : "Sin artefacto de backend: se despliega la funcion de relleno que responde 501.",
    "Poner el MFA del grupo de personal en ON cuando el primer usuario tenga su TOTP configurado.",
    "Pasar la cuenta al plan de pago antes de que venza el periodo gratuito: el plan gratuito cierra la cuenta sola.",
    var.gestionar_dns ? "Cargar los servidores de nombres en el registrador del dominio." : "gestionar_dns esta apagado: se sirve por los dominios predeterminados de CloudFront.",
  ]
}
