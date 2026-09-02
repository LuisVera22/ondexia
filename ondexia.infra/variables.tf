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

    En prod es OBLIGATORIO (tabla de bajas de la auditoria 2026-09-01). Sin
    dominio propio, CloudFront sirve por su dominio predeterminado y ahi la
    version minima de TLS es la 1.0 —no se puede subir sin certificado propio—, y
    la CSP del SPA cae al comodin de execute-api (estatico.tf). Las dos cosas
    son aceptables en dev y no delante de un cliente. La validacion de abajo es lo
    que convierte «bloqueante antes de clientes» en algo que Terraform impone.
  TEXTO
  type        = bool
  default     = false

  validation {
    condition     = var.entorno != "prod" || var.gestionar_dns
    error_message = "En prod hace falta gestionar_dns = true: sin dominio propio CloudFront acepta TLS 1.0 y la CSP queda con comodines. Registra el dominio y carga los servidores de nombres antes de desplegar produccion."
  }
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

variable "acceso_bd_publico" {
  description = <<-TEXTO
    Da a la instancia RDS una IP pública y abre el 5432 **solo** a la IP
    saliente de quien aplica, para poder conectar psql o un cliente gráfico
    desde el equipo de desarrollo.

    Solo tiene efecto en dev: `local.bd_publica` lo cruza con el entorno, y la
    precondición de la instancia aborta el apply si alguien lo enciende en
    prod. La base de prod guarda datos tributarios de clientes y no se expone
    a internet por comodidad de nadie.

    Lo que hay que entender antes de encenderlo: con esto las subredes dejan de
    ser privadas de verdad —tienen ruta a la puerta de enlace— y lo único que
    separa la base de internet es el grupo de seguridad. La Lambda sigue sin
    salida, porque sus interfaces nunca reciben IP pública.

    La alternativa sin exposición es un bastión con EC2 Instance Connect
    Endpoint: cuesta ~3 USD/mes y deja la topología intacta. Es la vía a usar
    cuando dev tenga datos que importen.
  TEXTO
  type        = bool
  default     = false
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

variable "artefacto_panel" {
  description = <<-TEXTO
    Ruta al artefacto desplegable del panel administrativo interno.

    Vacio significa que el panel NO se despliega: no se crea ni su funcion, ni su
    API, ni su rol. Es lo que permite que la infraestructura siga siendo
    aplicable mientras la consola se construye, y tambien apagarla entera
    borrando una linea si algun dia hiciera falta.

    El sitio estatico si se crea siempre, porque cuelga del mapa `local.sitios`
    y un bucket vacio no cuesta nada.
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

variable "repositorio_github" {
  description = <<-TEXTO
    Repositorio en formato `propietario/nombre`, legible.

    No entra en la política de confianza: allí va la forma con identificadores
    numéricos, que es la que GitHub emite. Este valor existe para dos cosas —
    poder leer de qué repositorio hablamos sin descifrar números, y comprobar
    que los identificadores de `repositorio_github_inmutable` corresponden a
    este y no a otro.
  TEXTO
  type        = string
  default     = "LuisVera22/ondexia"

  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.repositorio_github))
    error_message = "Debe ser propietario/nombre, sin comodines ni barras de más."
  }
}

variable "repositorio_github_inmutable" {
  description = <<-TEXTO
    El mismo repositorio con los identificadores numéricos que GitHub pega al
    propietario y al nombre: `propietario@idPropietario/nombre@idRepositorio`.

    Hace falta porque GitHub emite el `sub` del token OIDC en esta forma, no en
    la legible. Se descubrió leyendo en CloudTrail el intento fallido: la
    política esperaba `repo:LuisVera22/ondexia:environment:dev` y lo que llegaba
    era `repo:LuisVera22@149976444/ondexia@1326100922:environment:dev`.

    Los identificadores son INMUTABLES, y eso es una ventaja: renombrar la cuenta
    o el repositorio no rompe la autorización, y nadie puede quedarse con un
    nombre liberado para suplantarlos.

    Se obtiene de la API de GitHub:
      curl -s https://api.github.com/repos/LuisVera22/ondexia | grep -E '"id"'
    o del propio CloudTrail cuando un intento falla.
  TEXTO
  type        = string
  default     = "LuisVera22@149976444/ondexia@1326100922"

  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+@[0-9]+/[A-Za-z0-9_.-]+@[0-9]+$", var.repositorio_github_inmutable))
    error_message = "Debe ser propietario@id/nombre@id, con los dos identificadores numéricos."
  }

  /*
   * Que los identificadores sean de ESTE repositorio y no de otro.
   *
   * Pegar aquí los números equivocados no rompe nada al aplicar: crea una
   * política que autoriza a un repositorio ajeno y niega al nuestro, y el
   * síntoma es un AccessDenied en el despliegue que no menciona los
   * identificadores. Esto lo convierte en un error de validación con nombre.
   */
  validation {
    condition = (
      startswith(var.repositorio_github_inmutable, "${split("/", var.repositorio_github)[0]}@")
      && strcontains(var.repositorio_github_inmutable, "/${split("/", var.repositorio_github)[1]}@")
    )
    error_message = "El propietario y el nombre no coinciden con repositorio_github."
  }
}

variable "retencion_logs_dias" {
  description = "Retención de CloudWatch Logs. Los logs sin política son la fuga de costo más común."
  type        = number
  default     = 30
}

/**
 * ondexia.consultas — la funcion que sale a internet (DT-19).
 *
 * Vacio la deja sin desplegar, igual que `artefacto_api`. Es lo que permite
 * aplicar Terraform antes de que el modulo se construya, y volver atras sin
 * borrar nada a mano.
 */
variable "artefacto_consultas" {
  description = "Ruta al jar de ondexia.consultas. Vacio: no se despliega."
  type        = string
  default     = ""
}

variable "memoria_consultas_mb" {
  description = "Memoria de la funcion de consultas"
  type        = number
  default     = 512

  validation {
    /*
     * En Lambda la CPU va atada a la memoria. Por debajo de 256 MB, arrancar la
     * JVM y negociar TLS con el proveedor tarda mas que la consulta misma —
     * espera que mira una persona en un formulario. Y por encima de 1024 no hay
     * nada que ganar: la funcion hace dos peticiones HTTP y una firma.
     */
    condition     = var.memoria_consultas_mb >= 256 && var.memoria_consultas_mb <= 1024
    error_message = "Entre 256 y 1024 MB: menos hace lento el arranque, mas no compra nada."
  }
}

variable "usar_apiperu" {
  description = "Incluir apiperu.dev como relevo en la cascada de consulta del RUC"
  type        = bool
  default     = false
}

/**
 * MFA del pool de PERSONAL. En prod no es negociable.
 *
 * La variable existe para que un entorno de pruebas pueda bajarlo si algun dia
 * hace falta; la precondicion de `aws_cognito_user_pool.personal` es la que
 * impide que ese permiso llegue a produccion. Hallazgo A4 de la auditoria
 * 2026-09-01: estaba en OPTIONAL con un comentario que decia que habia que
 * subirlo a mano.
 */
variable "mfa_personal" {
  description = "mfa_configuration del pool de personal. En prod tiene que ser ON."
  type        = string
  default     = "ON"

  validation {
    condition     = contains(["ON", "OPTIONAL", "OFF"], var.mfa_personal)
    error_message = "Valores admitidos: ON, OPTIONAL, OFF."
  }
}

/**
 * Autoservicio de alta en el pool de INQUILINOS.
 *
 * Cerrado desde el hallazgo C2 de la auditoria 2026-09-01: con el abierto,
 * cualquiera obtiene en dos minutos un token que abre la consulta de RUC —que
 * gasta clave de pago de un tercero— y que permite enumerar por el 409 del
 * registro que RUC del pais son clientes nuestros.
 *
 * Revierte una decision de producto del 2026-08-13 (venta con periodo de
 * prueba, alta como pantalla del producto). Ponerlo en true vuelve a abrirlo;
 * hacerlo sin haber arreglado antes la consulta de RUC reabre C2.
 */
variable "autoservicio_inquilinos" {
  description = "Permitir que cualquiera se registre solo en el pool de inquilinos"
  type        = bool
  default     = false
}
