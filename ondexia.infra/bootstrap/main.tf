/**
 * Arranque del estado remoto.
 *
 * Problema del huevo y la gallina: Terraform quiere guardar su estado en un
 * bucket de S3, pero ese bucket no existe hasta que alguien lo cree. Esta
 * carpeta lo crea, y **solo eso**, con estado local.
 *
 * Se ejecuta una vez en la vida del proyecto:
 *
 *     cd ondexia.infra/bootstrap
 *     terraform init
 *     terraform apply
 *
 * Después se vuelve a la carpeta de arriba y se hace `terraform init` con el
 * bucket que esto imprime. El `terraform.tfstate` que queda aquí no importa:
 * describe un bucket que nunca cambia, y si se pierde basta con importarlo.
 */

terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      proyecto       = "ondexia"
      gestionado_por = "terraform"
      proposito      = "estado-remoto"
    }
  }
}

variable "region" {
  description = "Debe ser la misma región donde vive el resto."
  type        = string
  default     = "us-east-1"
}

data "aws_caller_identity" "actual" {}

resource "aws_s3_bucket" "estado" {
  bucket = "ondexia-tfstate-${data.aws_caller_identity.actual.account_id}"

  # Sin esto, un `terraform destroy` distraído en esta carpeta se lleva el
  # estado de toda la infraestructura.
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_public_access_block" "estado" {
  bucket                  = aws_s3_bucket.estado.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

/**
 * Versionado. No es opcional.
 *
 * El estado es el mapa entre el código y lo que existe en AWS. Si se corrompe
 * —un apply interrumpido, dos ejecuciones a la vez— la versión anterior es lo
 * único que evita tener que reconstruirlo a mano recurso por recurso.
 */
resource "aws_s3_bucket_versioning" "estado" {
  bucket = aws_s3_bucket.estado.id

  versioning_configuration {
    status = "Enabled"
  }
}

/**
 * Cifrado. El estado contiene secretos en claro.
 *
 * Terraform guarda ahí la contraseña de la base de datos, entre otras cosas.
 * No es un archivo de configuración: es material sensible.
 */
resource "aws_s3_bucket_server_side_encryption_configuration" "estado" {
  bucket = aws_s3_bucket.estado.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Las versiones antiguas del estado no sirven para nada pasado un tiempo, pero
# ocupan. Se retiran solas a los 90 días.
resource "aws_s3_bucket_lifecycle_configuration" "estado" {
  bucket = aws_s3_bucket.estado.id

  rule {
    id     = "retirar-versiones-antiguas"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}

output "instrucciones" {
  value = <<-TEXTO

    Bucket de estado creado: ${aws_s3_bucket.estado.id}

    Ahora, desde ondexia.infra, con el entorno que vayas a desplegar:

      terraform init \
        -backend-config="bucket=${aws_s3_bucket.estado.id}" \
        -backend-config="key=ondexia/prod/terraform.tfstate" \
        -backend-config="region=${var.region}"

    La clave LLEVA EL ENTORNO, y no es un detalle de orden. Con una clave fija,
    dev y prod compartirian estado: el segundo apply creeria que los recursos
    del primero son suyos, y el primer despliegue de dev se llevaria por delante
    produccion.

    Para cambiar de entorno hay que reinicializar con la otra clave:

      terraform init -reconfigure \
        -backend-config="bucket=${aws_s3_bucket.estado.id}" \
        -backend-config="key=ondexia/dev/terraform.tfstate" \
        -backend-config="region=${var.region}"

  TEXTO
}
