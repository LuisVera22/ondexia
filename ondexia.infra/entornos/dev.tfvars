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

# Sin reserva de concurrencia. No es lo que se queria, es lo que la cuenta
# permite: su limite total son 10 ejecuciones simultaneas —el de una cuenta
# nueva de AWS, no los 1000 habituales— y AWS exige dejar 10 sin reservar. Con
# ese techo ninguna funcion puede reservar nada.
#
# El efecto practico es tolerable justo porque el limite es bajo: 10
# contenedores a 2 conexiones de Hikari son 20 conexiones, dentro de lo que
# aguanta una db.t4g.micro. Es decir, el limite de la cuenta esta haciendo de
# tope en lugar de la reserva.
#
# Lo que SI se pierde es el aislamiento entre funciones: un bucle en la API
# puede consumir las 10 y dejar sin sitio a la de migraciones. En dev se acepta;
# antes de prod hay que pedir la ampliacion de cuota.
concurrencia_reservada_api = -1

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
