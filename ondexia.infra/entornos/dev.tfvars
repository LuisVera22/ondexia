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

# Apagado, y el motivo es que el apply ya no lo hace una persona.
#
# La regla se ata a la IP saliente de QUIEN APLICA. Desde un equipo eso es
# cómodo; desde GitHub Actions es otra cosa. El primer plan lanzado desde CI
# quiso hacer esto:
#
#   ~ cidr_ipv4 = "179.6.31.36/32" -> "40.76.119.208/32"
#
# Es decir: cambiar la IP del desarrollador por la del runner. Tres problemas, y
# el tercero es el que decide.
#
#   1. Se pierde el acceso desde pgAdmin en cada despliegue.
#   2. La IP del runner es efímera, así que la regla se reescribe en cada
#      ejecución y el plan nunca sale limpio — el ruido acaba tapando un cambio
#      que sí importe.
#   3. Queda autorizado el 5432 para una dirección de un rango compartido de
#      Azure que mañana es de la máquina de otra persona.
#
# Para volver a conectar con un cliente gráfico: ponerlo en true y aplicar DESDE
# EL EQUIPO, no desde CI, y devolverlo a false al terminar. Apagarlo puede
# necesitar dos applies seguidos: quitar la puerta de enlace y liberar la IP
# pública de RDS son dos cambios que Terraform no secuencia entre sí, y el
# primer intento falla con DependencyViolation.
#
# Lo estable, cuando dev tenga datos que importen, es el bastión con EC2
# Instance Connect Endpoint (~3 USD/mes) que describe la variable.
acceso_bd_publico = false

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
