# Desarrollo.
#
# Ojo con levantar esto: una segunda instancia de RDS duplica la factura
# exacta, y es el 90 % de ella. Para desarrollar del día a día conviene
# PostgreSQL en contenedor local y dejar este entorno solo para ensayar
# migraciones o despliegues antes de tocar producción.

entorno = "dev"
region  = "us-east-1"

# Sin dominio: se sirve por los dominios predeterminados de CloudFront.
gestionar_dns = false

correo_alertas       = "luis26.ml143@gmail.com"
tope_presupuesto_usd = 15

# Sin respaldos largos: aquí no hay nada que perder.
retencion_respaldos_dias = 1
retencion_logs_dias      = 7

# Menos techo de concurrencia: en dev nadie compite, y acota el gasto.
concurrencia_reservada_api = 5

# ── Backend ────────────────────────────────────────────────────────────────
#
# El artefacto se construye antes de aplicar:
#
#   cd apps/backend && ./mvnw -pl ondexia.api -am package -DskipTests
#
# Es un .jar y no un .zip porque un jar YA es un zip, y Lambda lo acepta tal
# cual. Renombrarlo solo anadiria un paso que puede olvidarse.
#
# No es el fat jar de Spring Boot: el reempaquetado esta desactivado y lo genera
# maven-shade-plugin (ver apps/backend/ondexia.api/pom.xml, que explica por que
# el cargador de clases de Lambda no sabe leer un fat jar).
artefacto_api = "../apps/backend/ondexia.api/target/ondexia-api.jar"
runtime_api   = "java21"
handler_api   = "com.ondexia.infrastructure.entrada.lambda.ManejadorLambda::handleRequest"
