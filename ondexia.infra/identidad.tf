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

  # Solo un administrador crea usuarios. Un ERP no tiene registro abierto:
  # quien entra es empleado de una empresa que ya contrató.
  admin_create_user_config {
    allow_admin_create_user_only = true
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
   */
  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
  ]

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
   * localhost está permitido a propósito para desarrollar, y es la única
   * excepción de http que acepta Cognito. En prod conviene quitarlo.
   */
  callback_urls = [
    "${local.origen_app}/acceso/retorno",
    "http://localhost:4200/acceso/retorno",
  ]

  logout_urls = [
    "${local.origen_app}/acceso/ingresar",
    "http://localhost:4200/acceso/ingresar",
  ]

  # Una hora de token de acceso limita cuánto sobrevive uno robado. Los
  # permisos no viajan en él, así que revocar un permiso surte efecto de
  # inmediato: se resuelve en la base en cada petición.
  access_token_validity  = 1
  id_token_validity      = 1
  refresh_token_validity = 30

  token_validity_units {
    access_token  = "hours"
    id_token      = "hours"
    refresh_token = "days"
  }

  # Al renovar se emite un refresh token nuevo y el anterior deja de valer.
  enable_token_revocation = true

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
 * nuestro código, y por tanto tampoco su mantenimiento: el pool tiene el MFA
 * en OPTIONAL, y cada reto de esa negociación —MFA_SETUP, SOFTWARE_TOKEN_MFA,
 * NEW_PASSWORD_REQUIRED— sería una pantalla propia que construir y probar.
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

  # Debe pasar a "ON" en cuanto el primer usuario tenga su TOTP configurado.
  # Se deja opcional para no quedarse fuera en el primer acceso.
  mfa_configuration = "OPTIONAL"

  software_token_mfa_configuration {
    enabled = true
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
