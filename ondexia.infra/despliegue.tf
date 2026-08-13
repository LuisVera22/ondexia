/**
 * Identidad de despliegue para GitHub Actions.
 *
 * Sustituye a la alternativa evidente —una clave de acceso guardada en los
 * secretos del repositorio— y conviene tener claro por qué es MENOS exposición y
 * no más:
 *
 *   · Una clave de acceso es permanente. Si se filtra, funciona hasta que
 *     alguien lo note y la rote.
 *   · Con OIDC no se guarda ninguna credencial. GitHub firma un token que dice
 *     «esta ejecución es del repositorio X, rama Y», AWS lo cambia por
 *     credenciales que duran lo que el trabajo, y no hay nada que filtrar.
 *
 * Es decir: la opción sin OIDC confía MÁS en GitHub, porque le entrega una llave
 * que no caduca. Aquí no se le entrega nada.
 *
 * Ambas confían en que GitHub controle quién ejecuta el repositorio, y eso ya
 * era cierto: quien controle el repositorio puede empujar código.
 */

# El proveedor no cuesta nada y solo se declara una vez por cuenta.
#
# Sobre las huellas: desde 2023 AWS valida el certificado de GitHub contra su
# propio almacén de confianza y NO usa esta lista para este emisor. Se dejan las
# dos conocidas porque la API sigue aceptando el campo y omitirlo depende de la
# versión del proveedor; son inertes.
resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [
    "6938fd4d98bab03faadb97b34396831e3780aea1",
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd",
  ]

  tags = { Name = "${var.proyecto}-github" }
}

/**
 * La política de confianza. Aquí está el único riesgo real de todo esto.
 *
 * El error clásico es dejar floja la condición sobre `sub` —o no ponerla—: con
 * un comodín, CUALQUIER repositorio de GitHub, incluido el de un desconocido,
 * puede asumir este rol. No es una exageración teórica; es la forma en que estas
 * configuraciones se rompen.
 *
 * Por eso se enumeran las ramas una por una en vez de usar `repo:…:*`:
 *
 *   · `refs/heads/main`    — despliegue a producción
 *   · `refs/heads/develop` — despliegue a dev
 *
 * Nada más. Una rama de trabajo no despliega, y un `pull_request` desde una
 * bifurcación tampoco: su `sub` es `repo:…:pull_request`, que no está en la
 * lista. Eso último importa más de lo que parece — sin ello, cualquiera que
 * abriera una PR podría ejecutar código con estas credenciales.
 */
data "aws_iam_policy_document" "asumir_despliegue" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    effect  = "Allow"

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "repo:${var.repositorio_github}:ref:refs/heads/main",
        "repo:${var.repositorio_github}:ref:refs/heads/develop",
      ]
    }
  }
}

resource "aws_iam_role" "despliegue" {
  name               = "${var.proyecto}-despliegue"
  description        = "Asumido por GitHub Actions para aplicar Terraform"
  assume_role_policy = data.aws_iam_policy_document.asumir_despliegue.json

  # Una hora. El despliegue completo —construir, migrar, publicar— tarda
  # minutos; darle margen de sobra sin llegar al máximo de doce horas.
  max_session_duration = 3600

  tags = { Name = "${var.proyecto}-despliegue" }
}

/**
 * Los permisos, y la parte honesta de esto.
 *
 * Un rol que ejecuta `terraform apply` sobre esta pila necesita crear y destruir
 * casi todo lo que el proyecto usa: funciones, buckets, la base, roles de IAM,
 * la pasarela, distribuciones, el grupo de usuarios. **Es un rol muy poderoso**,
 * y no hay forma de que no lo sea sin renunciar a que Terraform gestione la
 * infraestructura.
 *
 * Lo que de verdad lo acota no es esta política sino la de confianza: solo lo
 * asume una ejecución de GitHub Actions sobre `main` o `develop` de este
 * repositorio. La segunda barrera —la que hay que poner en GitHub, no aquí— es
 * proteger esas dos ramas para que nadie empuje a ellas sin revisión.
 *
 * Se usa PowerUserAccess más IAM acotado en lugar de AdministratorAccess: la
 * diferencia práctica es que este rol no puede tocar la facturación, ni la
 * organización, ni crear usuarios de IAM con claves permanentes — que es
 * justamente lo que haría alguien que quisiera dejarse una puerta abierta.
 */
resource "aws_iam_role_policy_attachment" "despliegue_poweruser" {
  role       = aws_iam_role.despliegue.name
  policy_arn = "arn:aws:iam::aws:policy/PowerUserAccess"
}

data "aws_iam_policy_document" "despliegue_iam" {
  # PowerUserAccess deja fuera IAM casi por completo, y Terraform necesita
  # gestionar los roles de las funciones. Se concede sobre roles y políticas,
  # no sobre usuarios ni claves de acceso.
  statement {
    sid = "GestionarRolesDelProyecto"
    actions = [
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:GetRole",
      "iam:UpdateRole",
      "iam:PassRole",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:ListRoleTags",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:GetRolePolicy",
      "iam:ListRolePolicies",
    ]
    resources = ["arn:aws:iam::${data.aws_caller_identity.actual.account_id}:role/${var.proyecto}-*"]
  }

  # Los roles vinculados a servicio los crea AWS sola la primera vez que se usa
  # un servicio. Sin esto, el primer apply que estrene uno falla con un
  # «not authorized to perform iam:CreateServiceLinkedRole» que no dice qué
  # servicio lo pedía.
  statement {
    sid       = "RolesVinculadosAServicio"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["*"]
  }

  # El proveedor OIDC y este mismo rol quedan fuera a propósito: los crea una
  # persona desde su equipo, una vez. Un rol que puede reescribir su propia
  # política de confianza no está acotado por ella.
  statement {
    sid    = "NoTocarSuPropiaIdentidad"
    effect = "Deny"
    actions = [
      "iam:*OpenIDConnectProvider*",
      "iam:UpdateAssumeRolePolicy",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "despliegue_iam" {
  name   = "${var.proyecto}-despliegue-iam"
  role   = aws_iam_role.despliegue.id
  policy = data.aws_iam_policy_document.despliegue_iam.json
}

output "rol_despliegue" {
  description = <<-TEXTO
    ARN del rol que asume GitHub Actions. Hay que guardarlo como secreto del
    repositorio con el nombre AWS_DEPLOY_ROLE_ARN, que es el que espera
    deploy.yml.

    No es un secreto de verdad —un ARN no autoriza nada por sí solo, lo que
    autoriza es la política de confianza— pero se guarda como tal para no
    publicar el identificador de la cuenta en el repositorio.
  TEXTO
  value       = aws_iam_role.despliegue.arn
}
