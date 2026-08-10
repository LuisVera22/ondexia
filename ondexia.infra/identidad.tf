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

  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
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
