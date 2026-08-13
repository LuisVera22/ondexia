terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.6"
    }
    # Solo para averiguar la IP saliente de quien aplica, cuando dev abre la
    # base de datos al exterior. Ver acceso_bd_publico en variables.tf.
    http = {
      source  = "hashicorp/http"
      version = "~> 3.4"
    }
  }

  # El estado se guarda en S3, no en disco. Con el estado local, perder el
  # portátil significa perder el mapa entre el código y lo que existe en AWS:
  # Terraform ya no sabría qué recursos gestiona y habría que importarlos a
  # mano uno por uno.
  #
  # El bucket hay que crearlo antes — ver bootstrap/ y el README.
  #
  # Configuracion parcial a proposito: ni `bucket` ni `key` se fijan aqui.
  #
  #   bucket — su nombre lleva el identificador de la cuenta, que no se conoce
  #            al escribir el codigo.
  #   key    — **debe llevar el entorno**. Con un valor fijo, `dev` y `prod`
  #            compartirian estado: el segundo `apply` creeria que los recursos
  #            del primero son suyos y los reconfiguraria o los destruiria. Es
  #            decir, el primer despliegue de `dev` se llevaria por delante
  #            produccion.
  #
  # Ambos se pasan en `terraform init -backend-config=...`. Ver el README y
  # .github/workflows/deploy.yml.
  backend "s3" {
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      proyecto       = var.proyecto
      entorno        = var.entorno
      gestionado_por = "terraform"
    }
  }
}

# CloudFront solo acepta certificados de ACM emitidos en us-east-1, sea cual
# sea la región del resto. Este alias existe para eso y solo para eso, y es lo
# que permite que la infraestructura funcione igual si mañana se decide operar
# desde Ohio.
provider "aws" {
  alias  = "edge"
  region = "us-east-1"

  default_tags {
    tags = {
      proyecto       = var.proyecto
      entorno        = var.entorno
      gestionado_por = "terraform"
    }
  }
}
