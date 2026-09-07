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

variable "principales_con_acceso_total" {
  description = <<-TEXTO
    ARN de las identidades humanas que pueden leer y escribir el estado de
    TODOS los entornos —normalmente el usuario o rol con el que se administra
    la cuenta—. Los roles de despliegue y de plan de cada entorno no van aqui:
    ya tienen acceso a su propio prefijo por nombre. El usuario raiz de la
    cuenta siempre queda dentro, para que ninguna combinacion de valores deje
    el estado inalcanzable.
  TEXTO
  type        = list(string)
  default     = []
}

# Los dos entornos y los roles que la configuracion principal crea para cada
# uno (despliegue.tf). Se nombran por ARN y no por referencia: este bucket
# existe antes que esos roles, y una politica por ARN no necesita que existan.
locals {
  entornos    = toset(["dev", "prod"])
  cuenta      = data.aws_caller_identity.actual.account_id
  raiz_cuenta = "arn:aws:iam::${local.cuenta}:root"
  roles_de = { for e in local.entornos : e => [
    "arn:aws:iam::${local.cuenta}:role/ondexia-despliegue-${e}",
    "arn:aws:iam::${local.cuenta}:role/ondexia-plan-${e}",
  ] }
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

/**
 * Nada sin TLS, ni siquiera para quien tenga permiso (tabla de bajas).
 *
 * El estado lleva la contraseña de la base en claro. Sin esta politica, un
 * cliente mal configurado —o un `aws s3 cp` con `--endpoint-url http://`— la
 * bajaria por HTTP y quedaria legible para cualquiera en el camino.
 *
 * Y cada entorno lee solo su prefijo. La version anterior lo dejaba «dicho en
 * vez de hecho» con el argumento de que los roles de despliegue no existen
 * cuando se crea este bucket. El argumento era cierto para una politica por
 * REFERENCIA y falso para una por NOMBRE: los ARN de esos roles son
 * deterministas (despliegue.tf los fija), y un Deny que los nombre se aplica
 * hoy y surte efecto el dia que existan. La validacion de la auditoria del
 * 2026-09-07 mostro lo que costaba no hacerlo: el rol de solo lectura de dev,
 * en un entorno de GitHub sin revisor, podia bajar el estado de prod con la
 * contraseña maestra dentro.
 *
 * Lo que sigue sin hacerse, y por que: SSE-KMS con llave propia cuesta
 * 1 USD/mes por una mejora que, con acceso publico bloqueado, TLS obligatorio
 * y prefijos por rol, no cierra ningun camino concreto.
 */
data "aws_iam_policy_document" "estado" {
  # Un Deny por entorno: nadie que no sea de ese entorno —o administrador, o
  # el usuario raiz— toca los objetos bajo su prefijo. Solo objetos: listar el
  # bucket sigue permitido, porque el nombre de la clave no es secreto y el
  # backend de Terraform lo necesita.
  dynamic "statement" {
    for_each = local.entornos
    content {
      sid    = "SoloSuEntorno${title(statement.value)}"
      effect = "Deny"

      principals {
        type        = "AWS"
        identifiers = ["*"]
      }

      actions   = ["s3:GetObject*", "s3:PutObject*", "s3:DeleteObject*"]
      resources = ["${aws_s3_bucket.estado.arn}/ondexia/${statement.value}/*"]

      condition {
        test     = "ArnNotLike"
        variable = "aws:PrincipalArn"
        values = concat(
          [local.raiz_cuenta],
          local.roles_de[statement.value],
          var.principales_con_acceso_total,
        )
      }
    }
  }

  statement {
    sid    = "NadaSinTls"
    effect = "Deny"

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }

    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.estado.arn,
      "${aws_s3_bucket.estado.arn}/*",
    ]

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "estado" {
  bucket = aws_s3_bucket.estado.id
  policy = data.aws_iam_policy_document.estado.json

  # Despues del bloqueo de acceso publico: S3 rechaza poner una politica en un
  # bucket mientras `block_public_policy` se esta aplicando.
  depends_on = [aws_s3_bucket_public_access_block.estado]
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
