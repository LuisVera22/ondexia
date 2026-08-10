# Producción.
#
# Aquí viven los datos tributarios de un cliente que paga. Las diferencias con
# dev no son de tamaño sino de qué pasa cuando algo sale mal: protección contra
# borrado, copia final antes de destruir y respaldos que cubren una semana.

entorno = "prod"
region  = "us-east-1"

# Activar cuando el dominio esté registrado y se puedan cargar los servidores
# de nombres en el registrador. Antes de eso, encenderlo deja certificados
# esperando una validación que nunca llega.
gestionar_dns = false
dominio       = "ondexia.com"

correo_alertas       = "luis.vera@wirbi.com"
tope_presupuesto_usd = 30

retencion_respaldos_dias = 7
retencion_logs_dias      = 30

concurrencia_reservada_api = 20

# Se rellenan cuando exista el backend:
# artefacto_api = "../apps/ondexia.api/target/ondexia-api.zip"
# runtime_api   = "java21"
# handler_api   = "com.ondexia.api.Manejador::handleRequest"
