# Producción.
#
# Aquí viven los datos tributarios de un cliente que paga. Las diferencias con
# dev no son de tamaño sino de qué pasa cuando algo sale mal: protección contra
# borrado, copia final antes de destruir y respaldos que cubren una semana.

entorno = "prod"
region  = "us-east-1"

# Promoción de la única pila existente. Migrar antes su titularidad al estado
# prod; nunca aplicar sobre un estado vacío ni compartirlo con otro despliegue.
entorno_recursos = "dev"

# OBLIGATORIO en prod: variables.tf lo valida y el plan aborta con false. Sin
# dominio propio CloudFront acepta TLS 1.0 y la CSP queda con comodines (tabla
# de bajas de la auditoria 2026-09-01).
#
# Dominio adquirido en Namecheap. La zona y certificados se preparan por
# etapas antes de delegar los servidores de nombres; ver README, promoción.
gestionar_dns = true
dominio       = "ondexia.com"

correo_alertas = "luis26.ml143@gmail.com"

# El registro por autoservicio es el producto: un negocio se da de alta solo
# (doc 12 §6.2). La cadena critica 2 de la auditoria lo habia cerrado; vuelve
# con sus tres capas puestas —cuota por identidad en ondexia.consultas,
# throttling de la ruta y alarma de invocaciones— y no antes.
autoservicio_inquilinos = true
tope_presupuesto_usd    = 30

retencion_respaldos_dias = 7
retencion_logs_dias      = 30

# Este valor EXIGE haber pedido antes una ampliacion de cuota de concurrencia:
# la cuenta arranca con un limite total de 10 y AWS obliga a dejar 10 sin
# reservar, asi que cualquier valor positivo hace fallar el apply. Comprobar con
# `aws lambda get-account-settings` antes de desplegar prod.
concurrencia_reservada_api = 20

artefacto_api         = "../apps/backend/ondexia.api/target/ondexia-api.jar"
runtime_api           = "java21"
handler_api           = "com.ondexia.infrastructure.entrada.lambda.ManejadorLambda::handleRequest"
artefacto_panel       = "../apps/backend/ondexia.admin/target/ondexia-admin.jar"
artefacto_consultas   = "../apps/backend/ondexia.consultas/target/ondexia-consultas.jar"
artefacto_facturacion = "../apps/backend/ondexia.facturacion/target/ondexia-facturacion.jar"
usar_apiperu          = true
acceso_bd_publico     = false
