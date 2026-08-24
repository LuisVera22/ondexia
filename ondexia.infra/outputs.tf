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
    panel   = aws_s3_bucket.sitio["panel"].id
    marca   = aws_s3_bucket.marca.id
  }
}

output "distribuciones_cloudfront" {
  description = "Identificadores para invalidar la caché tras desplegar."
  value = {
    app     = aws_cloudfront_distribution.sitio["app"].id
    landing = aws_cloudfront_distribution.sitio["landing"].id
    panel   = aws_cloudfront_distribution.sitio["panel"].id
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

    /**
     * El MISMO origen que la API, y no es redundante.
     *
     * Desplegado, la consulta del padrón es una ruta más de la misma pasarela
     * (consultas.tf), así que la URL coincide. En local no: la sirve
     * ondexia.consultas en otro puerto —el 8081—, porque la API de Spring no
     * tiene esa ruta ni debe tenerla — desplegada no puede salir a internet.
     *
     * Con la clave siempre presente, el SPA lee una sola cosa y no distingue los
     * dos casos. Sin ella, el código tendría que decidir cuándo usar `api` y
     * cuándo otra cosa, que es la clase de rama que se prueba en un entorno y
     * falla en el otro.
     */
    consultas = var.gestionar_dns ? "https://api.${var.dominio}" : aws_apigatewayv2_api.principal.api_endpoint

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
  description = <<-TEXTO
    Punto de conexión. `alcanzable_desde_internet` dice si se puede conectar un
    cliente desde fuera de la VPC; cuando es falso, solo la Lambda llega.

    La contraseña no va aquí para que no aparezca en cualquier `terraform
    output`. Se pide a propósito:

      terraform output -raw contrasena_bd
  TEXTO
  value = {
    host                      = aws_db_instance.principal.address
    puerto                    = aws_db_instance.principal.port
    nombre                    = aws_db_instance.principal.db_name
    usuario                   = aws_db_instance.principal.username
    alcanzable_desde_internet = local.bd_publica
  }
}

output "contrasena_bd" {
  description = <<-TEXTO
    Contraseña del usuario maestro. Generada por Terraform y guardada solo en
    el estado, que vive cifrado en S3.

    Deuda conocida: la Lambda la recibe en una variable de entorno, donde la ve
    cualquiera con lambda:GetFunctionConfiguration. Antes de prod debe pasar a
    manage_master_user_password, que la mueve a Secrets Manager con rotación.
  TEXTO
  value       = random_password.bd.result
  sensitive   = true
}

output "funcion_migraciones" {
  description = <<-TEXTO
    Función que aplica las migraciones de Flyway, porque la API ya no migra al
    arrancar.

    El workflow de despliegue la invoca solo, y lo hace ANTES de publicar la
    versión nueva de la API: al revés, la API arrancaría con ddl-auto=validate
    contra un esquema viejo, fallaría la validación y —como SnapStart toma la
    instantánea durante ese arranque— la versión quedaría en Failed.

    Este valor hace falta cuando se aplica a mano desde un equipo:

      aws lambda invoke --function-name <este valor> --payload '{}' salida.json

    Vacío mientras no haya artefacto de backend desplegado.
  TEXTO
  value       = local.hay_backend ? aws_lambda_function.migraciones[0].function_name : ""
}

output "recordatorios" {
  description = "Lo que Terraform no puede hacer por ti."
  value = [
    "Confirmar la suscripción de correo al tema de SNS: llega un mensaje de AWS y hay que pulsar el enlace, o las alarmas no avisan a nadie.",
    local.hay_backend ? "Si aplicaste a mano: invocar la función de migraciones (output funcion_migraciones) antes de probar la API, porque el esquema no existe hasta entonces. Desde el workflow de despliegue NO hace falta — ya la invoca, y antes de publicar la API a propósito." : "Sin artefacto de backend: se despliega la función de relleno que responde 501.",
    "Poner el MFA del grupo de personal en ON cuando el primer usuario tenga su TOTP configurado.",
    "Pasar la cuenta al plan de pago antes de que venza el periodo gratuito: el plan gratuito cierra la cuenta sola.",
    var.gestionar_dns ? "Cargar los servidores de nombres en el registrador del dominio." : "gestionar_dns esta apagado: se sirve por los dominios predeterminados de CloudFront.",
  ]
}

output "url_panel" {
  description = "Consola interna. Entrar exige un usuario del grupo de personal, con MFA."
  value       = local.origen_panel
}

output "api_panel" {
  description = <<-TEXTO
    API de la consola interna. Vacia mientras `artefacto_panel` no apunte a un
    jar: el sitio estatico existe siempre, la funcion solo cuando hay que
    desplegarla.
  TEXTO
  value       = local.hay_panel ? aws_apigatewayv2_api.panel[0].api_endpoint : ""
}

output "configuracion_panel" {
  description = <<-TEXTO
    Contenido de config.json del SPA de la consola. Mismo mecanismo que el de
    clientes: el artefacto no lleva dentro ninguna URL, asi que el mismo build
    vale para dev y para prod.

      terraform output -json configuracion_panel > .../dist/ondexia-admin/browser/config.json
  TEXTO
  value = {
    api = local.hay_panel ? aws_apigatewayv2_api.panel[0].api_endpoint : ""
    cognito = {
      dominio   = "https://${aws_cognito_user_pool_domain.personal.domain}.auth.${var.region}.amazoncognito.com"
      clienteId = aws_cognito_user_pool_client.panel.id
    }
  }
}
