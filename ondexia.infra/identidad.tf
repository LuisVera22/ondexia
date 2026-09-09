/**
 * Identidad.
 *
 * Dos grupos de usuarios separados: uno para los clientes y otro para el
 * personal de Ondexia. Es la decisión del DTE §8.1 y se crea desde el primer
 * día aunque el back-office no exista todavía, porque migrar identidades en
 * producción es caro.
 *
 * Con un grupo compartido, un usuario nuestro y uno de un cliente se
 * diferenciarían solo por un claim, y un error al comprobarlo expondría los
 * datos de todos los clientes. Con grupos separados ese error deja de ser
 * posible: no hay token del back-office que la API de inquilinos sepa validar.
 *
 * Los permisos finos no viven aquí. El token porta identidad y nada más.
 */

locals {
  politica_contrasena = {
    minimum_length                   = 12
    require_lowercase                = true
    require_uppercase                = true
    require_numbers                  = true
    require_symbols                  = false
    temporary_password_validity_days = 3
  }
}

resource "aws_cognito_user_pool" "inquilinos" {
  name = "${local.nombre}-inquilinos"

  # El correo es el identificador. Nadie recuerda un nombre de usuario
  # inventado para un sistema que usa tres veces por semana.
  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length                   = local.politica_contrasena.minimum_length
    require_lowercase                = local.politica_contrasena.require_lowercase
    require_uppercase                = local.politica_contrasena.require_uppercase
    require_numbers                  = local.politica_contrasena.require_numbers
    require_symbols                  = local.politica_contrasena.require_symbols
    temporary_password_validity_days = local.politica_contrasena.temporary_password_validity_days
  }

  /**
   * Autoservicio CERRADO. Y hay una decisión de producto en contra que conviene
   * leer antes de tocar esta línea.
   *
   * Historia, en orden:
   *
   *   1. Estuvo cerrado, con este argumento: «un ERP no tiene registro abierto;
   *      quien entra es empleado de una empresa que ya contrató».
   *   2. Se abrió el 2026-08-13 porque la segunda mitad de esa frase es cierta
   *      y la primera no: el empleado lo da de alta su administrador, pero ese
   *      administrador tiene que llegar de algún sitio. Cerrado, cada cliente
   *      nuevo exigía crear a mano cuenta, usuario y empresa. Ondexia se vende
   *      con período de prueba, así que el alta es una pantalla del producto.
   *   3. Se vuelve a cerrar el 2026-09-01 por la cadena crítica C2 de la
   *      auditoría.
   *
   * QUÉ ENCONTRÓ LA AUDITORÍA. Con el autoservicio abierto, cualquiera obtiene
   * un token válido de este pool en dos minutos. Ese token abre la consulta de
   * RUC, que es una ruta autenticada pero que NO comprueba que quien llama
   * tenga cuenta en Ondexia: unas 180.000 consultas por hora contra la clave de
   * pago de Decolecta. Con el mismo token, `POST /api/v1/registro` responde
   * `409 ruc_ya_registrado` y enumera qué contribuyentes del país son clientes
   * nuestros.
   *
   * Lo que se pierde es real: el alta deja de ser una pantalla y vuelve a ser
   * trabajo de operaciones. Decisión del responsable del producto, tomada con
   * esa consecuencia sobre la mesa.
   *
   * Se deja como variable porque revertirlo es legítimo el día que la consulta
   * de RUC exija fila en `usuario` —que es el otro arreglo posible de C2, y el
   * bueno— y no una línea que haya que volver a discutir.
   */
  admin_create_user_config {
    allow_admin_create_user_only = !var.autoservicio_inquilinos
  }

  # No revela si un correo existe cuando falla el acceso.
  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  mfa_configuration = "OPTIONAL"

  software_token_mfa_configuration {
    enabled = true
  }

  # Cognito envía el correo. Suficiente para el volumen de la v1; migrar a SES
  # cuando el límite diario estorbe, y verificar antes el dominio.
  email_configuration {
    email_sending_account = "COGNITO_DEFAULT"
  }

  deletion_protection = var.entorno == "prod" ? "ACTIVE" : "INACTIVE"

  tags = { Name = "${local.nombre}-inquilinos" }
}

/**
 * Cliente del SPA.
 *
 * Sin secreto de cliente: una aplicación que corre en el navegador no puede
 * guardar un secreto, y fingir que sí es peor que no tenerlo. La seguridad la
 * da PKCE, que es obligatorio en este flujo.
 */
resource "aws_cognito_user_pool_client" "spa" {
  name         = "${local.nombre}-spa"
  user_pool_id = aws_cognito_user_pool.inquilinos.id

  generate_secret = false

  /**
   * SRP y renovación. Las dos hacen falta, y la primera no es evidente.
   *
   * Es tentador quitar SRP razonando que el SPA nunca ve la contraseña —cierto,
   * la pide la interfaz alojada—. Pero quien comprueba esa contraseña contra el
   * pool es la propia interfaz alojada, usando SRP y **este mismo cliente**.
   * Sin el flujo habilitado no puede validar a nadie, y con
   * prevent_user_existence_errors activado el fallo sale como «Incorrect
   * username or password» aunque el usuario exista y la contraseña sea la
   * correcta. Dos horas de buscar en el sitio equivocado.
   *
   * Lo que NO se habilita es ALLOW_USER_PASSWORD_AUTH, que aceptaría la
   * contraseña en claro desde cualquier cliente. Con SRP, la contraseña no
   * viaja: viaja una prueba de que se conoce.
   *
   * NO se lista ALLOW_REFRESH_TOKEN_AUTH. Con `refresh_token_rotation` en
   * ENABLED, Cognito RECHAZA el cliente:
   *
   *   ALLOW_REFRESH_TOKEN_AUTH is not a permitted ExplicitAuthFlow
   *   when refresh token rotation is enabled.
   *
   * No es que se pierda el refresco: con la rotacion encendida, canjear un
   * refresco es parte del propio mecanismo y no necesita declararse. Listarlo
   * ademas seria pedir las dos cosas a la vez —rotar y no rotar—, y por eso la
   * API se niega en vez de elegir una.
   */
  explicit_auth_flows = ["ALLOW_USER_SRP_AUTH"]

  /**
   * Flujo de código de autorización con PKCE.
   *
   * `code` y no `implicit`: el flujo implícito devuelve el token en el
   * fragmento de la URL, donde acaba en el historial del navegador y en los
   * registros de cualquier intermediario. Está desaconsejado desde 2019.
   *
   * PKCE lo aplica Cognito por su cuenta cuando el cliente no tiene secreto,
   * que es este caso. Sin él, cualquiera que interceptase el código podría
   * canjearlo; con él hace falta además el verificador, que solo conoce la
   * pestaña que inició la sesión.
   */
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]

  # `openid` es obligatorio para recibir un id_token. `email` y `profile` son
  # los que hacen que el token traiga el correo y el nombre — sin ellos, la
  # pantalla de perfil no tendría qué mostrar hasta llamar a la API.
  allowed_oauth_scopes         = ["openid", "email", "profile"]
  supported_identity_providers = ["COGNITO"]

  /**
   * Cognito solo redirige a URLs de esta lista, comparadas de forma exacta.
   *
   * Es la defensa contra el robo del código: sin ella, un atacante podría
   * lanzar el flujo con una `redirect_uri` propia y recibir él el código de
   * autorización de la víctima.
   *
   * localhost está permitido para desarrollar —es la única excepción de http
   * que acepta Cognito— y SOLO fuera de prod. Antes estaba en la lista de todos
   * los entornos con la nota «en prod conviene quitarlo» (tabla de bajas de la
   * auditoría 2026-09-01): en producción significaba que un código de
   * autorización podía terminar en el localhost de quien tuviera a la víctima en
   * su red. Una obligación que se codifica, no que se recuerda.
   */
  callback_urls = concat(
    ["${local.origen_app}/acceso/retorno"],
    var.entorno == "prod" ? [] : ["http://localhost:4200/acceso/retorno"],
  )

  logout_urls = concat(
    ["${local.origen_app}/acceso/ingresar"],
    var.entorno == "prod" ? [] : ["http://localhost:4200/acceso/ingresar"],
  )

  # Una hora de token de acceso limita cuánto sobrevive uno robado. Los
  # permisos no viajan en él, así que revocar un permiso surte efecto de
  # inmediato: se resuelve en la base en cada petición.
  access_token_validity = 1
  id_token_validity     = 1

  /**
   * Siete dias de refresco, no treinta (hallazgo C3).
   *
   * El SPA guarda el refresco en sessionStorage, que es legible por cualquier
   * JavaScript de la pagina. Treinta dias significa que uno robado da acceso un
   * mes entero. Siete es el compromiso: no obliga a volver a entrar cada dia y
   * acota el dano.
   *
   * Lo que de verdad lo cierra es no tenerlo en sessionStorage —refresco en
   * memoria, o un BFF con cookie HttpOnly—. Es un rediseno del acceso y no
   * entra aqui; queda dicho para que no se confunda esto con la solucion.
   */
  refresh_token_validity = 7

  token_validity_units {
    access_token  = "hours"
    id_token      = "hours"
    refresh_token = "days"
  }

  /**
   * Habilita /oauth2/revoke. NO rota nada: eso es lo de abajo.
   *
   * El comentario que habia aqui decia «al renovar se emite un refresh token
   * nuevo y el anterior deja de valer», que describe la ROTACION y no lo que
   * hace esta linea. Es el hallazgo C3, y su origen esta en el DTE §8.1, donde
   * la rotacion figura entre lo que «se compra» con Cognito: se compra la
   * opcion, no el comportamiento.
   *
   * Ahora si hace falta de verdad: sin esto, el `cerrar()` del SPA no tendria
   * a donde llamar para revocar.
   */
  enable_token_revocation = true

  /**
   * La rotacion, esta vez activada.
   *
   * Cada renovacion emite un refresco nuevo e invalida el anterior. Lo que compra
   * no es solo acortar la vida del robado: si el ladron lo usa, el legitimo deja
   * de funcionar y la persona lo nota. Un refresco que no rota se puede usar en
   * paralelo durante semanas sin que nadie se entere.
   *
   * La gracia de 60 s cubre la carrera real: dos pestanas que renuevan a la vez,
   * o una respuesta que se pierde despues de que el servidor rotara. Sin ella, el
   * sintoma es cerrar sesion sola de vez en cuando y sin patron.
   */
  refresh_token_rotation {
    feature                    = "ENABLED"
    retry_grace_period_seconds = 60
  }

  prevent_user_existence_errors = "ENABLED"

  # Lo que el SPA puede leer y escribir del perfil. Sin esto podría modificar
  # atributos que no le corresponden.
  read_attributes  = ["email", "email_verified", "name"]
  write_attributes = ["name"]
}

/**
 * Dominio de la interfaz alojada.
 *
 * Es la que pide el correo y la contraseña, resuelve el segundo factor, la
 * verificación por correo y el «olvidé mi contraseña». Nada de eso vive en
 * nuestro código, y por tanto tampoco su mantenimiento: cada reto de esa
 * negociación —MFA_SETUP, SOFTWARE_TOKEN_MFA, NEW_PASSWORD_REQUIRED— sería una
 * pantalla propia que construir y probar. Que el pool de personal tenga el MFA
 * en ON es justamente lo que hace que MFA_SETUP ocurra en el primer acceso, y
 * lo resuelve la interfaz alojada.
 *
 * El prefijo es único en toda la región de AWS, de ahí el identificador de
 * cuenta. Queda como:
 *
 *     https://ondexia-dev-370930247103.auth.us-east-1.amazoncognito.com
 */
resource "aws_cognito_user_pool_domain" "inquilinos" {
  domain       = "${local.nombre}-${local.sufijo}"
  user_pool_id = aws_cognito_user_pool.inquilinos.id
}

resource "aws_cognito_user_pool" "personal" {
  name = "${local.nombre}-personal"

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length                   = 16 # más exigente: estas cuentas ven todas las cuentas
    require_lowercase                = true
    require_uppercase                = true
    require_numbers                  = true
    require_symbols                  = true
    temporary_password_validity_days = 1
  }

  admin_create_user_config {
    allow_admin_create_user_only = true
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  /**
   * Obligatorio, y en prod no es negociable — lo impone la precondicion de mas
   * abajo (hallazgo A4).
   *
   * Estuvo en "OPTIONAL" con el comentario «debe pasar a ON en cuanto el primer
   * usuario tenga su TOTP configurado». Esa frase es el hallazgo: una obligacion
   * previa al despliegue escrita como recordatorio no la cumple nadie, y estas
   * cuentas ven TODAS las cuentas de TODOS los clientes.
   *
   * El miedo a quedarse fuera en el primer acceso no aplica: con "ON" y el token
   * de software habilitado, Cognito lanza el reto MFA_SETUP en el primer inicio
   * de sesion y la interfaz alojada guia el alta del TOTP. No hay ventana en la
   * que la cuenta exista sin segundo factor.
   */
  mfa_configuration = var.mfa_personal

  software_token_mfa_configuration {
    enabled = true
  }

  lifecycle {
    /**
     * La obligacion, codificada.
     *
     * `var.mfa_personal` existe para que un entorno de pruebas pueda bajarlo si
     * algun dia hace falta; esta precondicion es lo que impide que ese permiso
     * llegue a produccion. Se evalua ANTES de crear o modificar nada: un
     * `terraform plan` con entorno prod y el MFA bajado aborta sin tocar la
     * cuenta.
     *
     * Deliberadamente sobre la variable y no sobre `self.mfa_configuration`: un
     * postcondition sobre `self` se evaluaria cuando el pool ya esta creado, que
     * es tarde.
     */
    precondition {
      condition     = var.entorno != "prod" || var.mfa_personal == "ON"
      error_message = "El pool de personal exige mfa_configuration = ON en prod: estas cuentas ven los datos de todos los clientes. Cambia var.mfa_personal o no despliegues a prod."
    }
  }

  email_configuration {
    email_sending_account = "COGNITO_DEFAULT"
  }

  deletion_protection = var.entorno == "prod" ? "ACTIVE" : "INACTIVE"

  tags = {
    Name = "${local.nombre}-personal"
    nota = "Personal de Ondexia. Separado de los inquilinos a proposito"
  }
}

# ── Grupos del personal (hallazgo A5) ──────────────────────────────────────

/**
 * Dos grupos, porque «todo el personal es superadministrador» no es un modelo.
 *
 * Hasta el hallazgo A5, cualquier cuenta del grupo de personal que pasara el
 * autorizador podia cambiar el plan de un cliente, suspenderle el servicio o
 * activarle modulos. No habia grupos, ni autorizacion por endpoint: la unica
 * frontera era estar dentro o fuera.
 *
 * Eso es tolerable con una persona y deja de serlo con dos, porque la primera
 * cuenta que se le crea a alguien —para que consulte algo— viene con la
 * capacidad de suspender a un cliente en produccion.
 *
 *   soporte      mira. Es lo que necesita quien atiende una consulta.
 *   operaciones  cambia planes, suspende y activa modulos.
 *
 * Dos y no cinco: el reparto tiene que corresponderse con trabajos que existen
 * hoy. Inventar niveles que nadie ocupa produce grupos que acaban teniendo a
 * todo el mundo dentro, que es donde estabamos.
 *
 * La pertenencia se asigna a mano en la consola de Cognito. NO se codifica aqui
 * a proposito: quien esta en cada grupo es un dato de personal, no de
 * infraestructura, y ponerlo en Terraform significaria que dar de baja a alguien
 * exige un despliegue.
 */
resource "aws_cognito_user_group" "soporte" {
  name         = "soporte"
  user_pool_id = aws_cognito_user_pool.personal.id
  description  = "Solo lectura: consultar cuentas y sus modulos"
}

resource "aws_cognito_user_group" "operaciones" {
  name         = "operaciones"
  user_pool_id = aws_cognito_user_pool.personal.id
  description  = "Cambiar plan, suspender y decidir modulos"
}
