variable "proyecto" {
  description = "Prefijo de todos los nombres de recurso."
  type        = string
  default     = "ondexia"
}

variable "entorno" {
  description = "Entorno desplegado. Forma parte del nombre de cada recurso."
  type        = string

  validation {
    condition     = contains(["dev", "prod"], var.entorno)
    error_message = "Solo hay dos entornos: dev y prod (DTE §10.1)."
  }
}

variable "region" {
  description = <<-TEXTO
    Región de AWS. La documentación fija us-east-1; la consola estaba en
    us-east-2 al escribir esto. Decidir antes del primer apply: los recursos de
    una región no se ven desde otra, y mover una RDS con datos después es una
    migración, no un cambio de variable.
  TEXTO
  type        = string
  default     = "us-east-1"
}

variable "dominio" {
  description = "Dominio raíz. Solo se usa si gestionar_dns está activo."
  type        = string
  default     = "ondexia.com"
}

variable "gestionar_dns" {
  description = <<-TEXTO
    Crea la zona de Route 53, los certificados de ACM y los dominios propios de
    CloudFront. Se deja en false para poder desplegar y probar sin tener el
    dominio apuntando todavía: CloudFront sirve igual por su dominio
    predeterminado.
  TEXTO
  type        = bool
  default     = false
}

variable "correo_alertas" {
  description = "Destinatario de la alerta de presupuesto. Obligatorio."
  type        = string

  validation {
    condition     = can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.correo_alertas))
    error_message = "Debe ser una dirección de correo válida."
  }
}

variable "tope_presupuesto_usd" {
  description = <<-TEXTO
    Tope mensual del presupuesto de AWS Budgets. La alerta salta al 80 %.
    La v1 estimada son ~15 USD/mes (DTE §4.7); 30 deja margen sin tapar una fuga.
  TEXTO
  type        = number
  default     = 30
}

# ── Base de datos ──────────────────────────────────────────────────────────

variable "clase_instancia_bd" {
  description = "Clase de la instancia RDS. Es el 90 % de la factura de la v1."
  type        = string
  default     = "db.t4g.micro"
}

variable "almacenamiento_bd_gb" {
  description = "Almacenamiento de RDS en GB. Crece solo si se activa el autoescalado."
  type        = number
  default     = 20
}

variable "retencion_respaldos_dias" {
  description = <<-TEXTO
    Días de retención de respaldos automáticos. Con 7 se cubre el RPO de 5 min
    del NFR-07, porque el respaldo continuo permite recuperación a un punto en
    el tiempo dentro de la ventana.
  TEXTO
  type        = number
  default     = 7
}

# ── API ────────────────────────────────────────────────────────────────────

variable "artefacto_api" {
  description = <<-TEXTO
    Ruta al artefacto desplegable de ondexia.api. Vacío mientras el backend no
    exista: se despliega una función de relleno que responde 501, para que la
    infraestructura sea aplicable y verificable de extremo a extremo desde el
    primer día.
  TEXTO
  type        = string
  default     = ""
}

variable "runtime_api" {
  description = "Runtime de la Lambda. Pasa a java21 cuando llegue el artefacto real."
  type        = string
  default     = "nodejs22.x"
}

variable "handler_api" {
  description = "Punto de entrada de la Lambda."
  type        = string
  default     = "index.handler"
}

variable "memoria_api_mb" {
  description = <<-TEXTO
    Memoria de la Lambda. En Lambda la CPU se asigna en proporción a la
    memoria, así que subirla acelera el arranque de Spring — que es el problema
    real (DTE §4.1), no el consumo de memoria.
  TEXTO
  type        = number
  default     = 1024
}

variable "concurrencia_reservada_api" {
  description = <<-TEXTO
    Tope de ejecuciones simultáneas. Sustituye al RDS Proxy: con HikariCP a 2
    conexiones por contenedor, 20 contenedores son 40 conexiones, dentro de lo
    que aguanta una db.t4g.micro. Además acota el gasto ante un bucle
    accidental (DTE §4.6).

    -1 significa «sin reserva»: la función usa el fondo común de la cuenta.

    OJO con las cuentas nuevas de AWS. El límite de concurrencia no arranca en
    los 1000 habituales sino en 10, y AWS exige que queden al menos 10 SIN
    reservar. Con ese techo ninguna función puede reservar nada, y cualquier
    valor positivo hace fallar el apply:

      InvalidParameterValueException: Specified ReservedConcurrentExecutions
      for function decreases account's UnreservedConcurrentExecution below its
      minimum value of [10]

    Se comprueba con `aws lambda get-account-settings`. Para poner un valor
    real hay que pedir antes una ampliación de cuota a AWS.
  TEXTO
  type        = number
  default     = -1

  validation {
    condition     = var.concurrencia_reservada_api == -1 || var.concurrencia_reservada_api >= 1
    error_message = "Usa -1 para no reservar, o un entero >= 1. El 0 existe y significa APAGAR la funcion: rechaza toda invocacion."
  }
}

variable "retencion_logs_dias" {
  description = "Retención de CloudWatch Logs. Los logs sin política son la fuga de costo más común."
  type        = number
  default     = 30
}
