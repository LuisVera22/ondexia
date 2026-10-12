mock_provider "aws" {
  mock_resource "aws_acm_certificate_validation" {
    defaults = { certificate_arn = "arn:aws:acm:us-east-1:123456789012:certificate/00000000-0000-0000-0000-000000000000" }
  }
  mock_data "aws_caller_identity" {
    defaults = { account_id = "123456789012" }
  }
  mock_data "aws_availability_zones" {
    defaults = { names = ["us-east-1a", "us-east-1b"] }
  }
  mock_data "aws_iam_policy_document" {
    defaults = { json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}" }
  }
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/sintetico" }
  }
  mock_resource "aws_iam_policy" {
    defaults = { arn = "arn:aws:iam::123456789012:policy/sintetica" }
  }
  mock_resource "aws_cognito_user_pool" {
    defaults = { id = "us-east-1_sintetico", endpoint = "cognito-idp.us-east-1.amazonaws.com/us-east-1_sintetico" }
  }
}
mock_provider "aws" {
  mock_resource "aws_acm_certificate_validation" {
    defaults = { certificate_arn = "arn:aws:acm:us-east-1:123456789012:certificate/00000000-0000-0000-0000-000000000000" }
  }
  alias = "edge"
}
mock_provider "random" {}
mock_provider "archive" {}
mock_provider "http" {}

variables {
  entorno                  = "prod"
  entorno_recursos         = "dev"
  artefacto_api            = "tests/artefacto.txt"
  correo_alertas           = "pruebas@example.com"
  gestionar_dns            = true
  autoservicio_inquilinos  = true
  retencion_respaldos_dias = 7
  retencion_logs_dias      = 30
}

run "conserva_identificadores_y_endurece" {
  command = plan
  assert {
    condition     = aws_db_instance.principal.identifier == "ondexia-dev"
    error_message = "La promoción debe conservar la RDS existente."
  }
  assert {
    condition     = aws_cognito_user_pool.inquilinos.name == "ondexia-dev-inquilinos"
    error_message = "La promoción debe conservar el pool de clientes."
  }
  assert {
    condition     = local.ssm_prefijo == "/ondexia/dev/consultas"
    error_message = "Las rutas de secretos existentes no deben cambiar."
  }
  assert {
    condition     = aws_db_instance.principal.deletion_protection && !aws_db_instance.principal.skip_final_snapshot && !aws_db_instance.principal.apply_immediately && aws_db_instance.principal.backup_retention_period == 7
    error_message = "Conservar nombres no debe conservar políticas de desarrollo."
  }
  assert {
    condition     = aws_cognito_user_pool.inquilinos.deletion_protection == "ACTIVE" && aws_cognito_user_pool.personal.mfa_configuration == "ON"
    error_message = "Las identidades productivas deben quedar protegidas."
  }
  assert {
    condition     = local.efecto_confianza["dev"] == "Deny" && local.efecto_confianza["prod"] == "Allow"
    error_message = "La entrada heredada dev debe quedar denegada sin fusionar roles."
  }
  assert {
    condition     = contains(aws_cloudfront_distribution.sitio["landing"].aliases, "www.ondexia.com") && aws_route53_record.panel[0].name == "panel.ondexia.com"
    error_message = "La configuración debe incluir www y DNS del panel."
  }
  assert {
    condition     = !contains(aws_cognito_user_pool_client.spa.callback_urls, "http://localhost:4200/acceso/retorno")
    error_message = "Producción no admite retorno al desarrollo local."
  }
}

run "rechaza_produccion_sin_dns" {
  command = plan
  variables { gestionar_dns = false }
  expect_failures = [var.gestionar_dns]
}

run "rechaza_base_publica" {
  command = plan
  variables { acceso_bd_publico = true }
  expect_failures = [aws_db_instance.principal]
}

run "rechaza_personal_sin_mfa" {
  command = plan
  variables { mfa_personal = "OFF" }
  expect_failures = [aws_cognito_user_pool.personal]
}

override_resource {
  target          = aws_acm_certificate.principal[0]
  override_during = plan
  values = {
    arn = "arn:aws:acm:us-east-1:123456789012:certificate/00000000-0000-0000-0000-000000000000"
    domain_validation_options = [
      { domain_name = "ondexia.com", resource_record_name = "_prueba.ondexia.com", resource_record_type = "CNAME", resource_record_value = "_sintetico.acm-validations.aws" },
      { domain_name = "www.ondexia.com", resource_record_name = "_prueba.www.ondexia.com", resource_record_type = "CNAME", resource_record_value = "_sintetico.acm-validations.aws" },
      { domain_name = "app.ondexia.com", resource_record_name = "_prueba.app.ondexia.com", resource_record_type = "CNAME", resource_record_value = "_sintetico.acm-validations.aws" },
      { domain_name = "api.ondexia.com", resource_record_name = "_prueba.api.ondexia.com", resource_record_type = "CNAME", resource_record_value = "_sintetico.acm-validations.aws" },
      { domain_name = "cdn.ondexia.com", resource_record_name = "_prueba.cdn.ondexia.com", resource_record_type = "CNAME", resource_record_value = "_sintetico.acm-validations.aws" },
      { domain_name = "panel.ondexia.com", resource_record_name = "_prueba.panel.ondexia.com", resource_record_type = "CNAME", resource_record_value = "_sintetico.acm-validations.aws" },
    ]
  }
}

override_resource {
  target          = aws_acm_certificate.api[0]
  override_during = plan
  values = {
    arn = "arn:aws:acm:us-east-1:123456789012:certificate/00000000-0000-0000-0000-000000000000"
    domain_validation_options = [
      { domain_name = "api.ondexia.com", resource_record_name = "_prueba.api.ondexia.com", resource_record_type = "CNAME", resource_record_value = "_sintetico.acm-validations.aws" },
    ]
  }
}

override_resource {
  target          = aws_acm_certificate_validation.principal[0]
  override_during = plan
  values          = { certificate_arn = "arn:aws:acm:us-east-1:123456789012:certificate/00000000-0000-0000-0000-000000000000" }
}

override_resource {
  target          = aws_acm_certificate_validation.api[0]
  override_during = plan
  values          = { certificate_arn = "arn:aws:acm:us-east-1:123456789012:certificate/00000000-0000-0000-0000-000000000000" }
}
