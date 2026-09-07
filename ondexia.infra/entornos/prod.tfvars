# Producción.
#
# Aquí viven los datos tributarios de un cliente que paga. Las diferencias con
# dev no son de tamaño sino de qué pasa cuando algo sale mal: protección contra
# borrado, copia final antes de destruir y respaldos que cubren una semana.

entorno = "prod"
region  = "us-east-1"

# OBLIGATORIO en prod: variables.tf lo valida y el plan aborta con false. Sin
# dominio propio CloudFront acepta TLS 1.0 y la CSP queda con comodines (tabla
# de bajas de la auditoria 2026-09-01).
#
# Se deja en false A SABIENDAS mientras el dominio no este registrado: asi el
# primer plan de prod falla con un mensaje que dice exactamente que falta, en
# vez de desplegar un entorno de produccion que no deberia recibir clientes.
# Encenderlo antes de poder cargar los servidores de nombres deja certificados
# esperando una validacion que nunca llega.
gestionar_dns = false
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

# Se descomentan cuando el backend se haya probado en dev. Mientras sigan
# comentadas, prod despliega la función de relleno que responde 501.
#
# Las rutas y el handler ya son los definitivos: la ruta anterior apuntaba a
# apps/ondexia.api, de antes de agrupar el backend bajo apps/backend, y el
# handler a una clase que nunca existió.
# artefacto_api = "../apps/backend/ondexia.api/target/ondexia-api.jar"
# runtime_api   = "java21"
# handler_api   = "com.ondexia.infrastructure.entrada.lambda.ManejadorLambda::handleRequest"
