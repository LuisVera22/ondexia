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
 *
 * ══════════════════════════════════════════════════════════════════════════
 * CUATRO ROLES, NO UNO (hallazgo C1 de la auditoría 2026-09-01)
 * ══════════════════════════════════════════════════════════════════════════
 *
 * La versión anterior tenía UN rol con PowerUserAccess cuya política de
 * confianza aceptaba `environment:dev` Y `environment:prod`. El entorno `dev` de
 * GitHub no exige revisor, así que la aprobación manual de prod no protegía
 * nada: bastaba lanzar el despliegue con `entorno=dev`, y con las credenciales
 * del runner —que son las mismas para los dos entornos— apuntar al estado de
 * prod o usar la CLI directamente.
 *
 * Peor: ese rol podía adjuntarse `AdministratorAccess` a sí mismo, porque
 * `iam:AttachRolePolicy` estaba concedido sobre `role/ondexia-*` sin condición
 * sobre QUÉ política se adjunta. Un push a cualquier rama —o una dependencia de
 * Maven o pnpm comprometida, o una acción de GitHub fijada por tag— terminaba en
 * administrador de la cuenta.
 *
 * Ahora hay cuatro roles y ninguno puede lo anterior:
 *
 *   ondexia-despliegue-dev    aplica  · confía SOLO en environment:dev
 *   ondexia-despliegue-prod   aplica  · confía SOLO en environment:prod
 *   ondexia-plan-dev          lee     · confía SOLO en environment:dev-plan
 *   ondexia-plan-prod         lee     · confía SOLO en environment:prod-plan
 *
 * Los de plan existen para que el revisor de prod vea el plan ANTES de aprobar
 * (hallazgo M14): el job que planifica usa un entorno sin revisor y un rol que
 * no puede escribir; el que aplica usa el entorno protegido. Sin esa separación
 * la aprobación llega al principio del job, cuando todavía no hay nada que
 * revisar.
 */

locals {
  # Los dos entornos, y el nombre del entorno de GitHub que planifica cada uno.
  # `for_each` sobre esto es lo que hace que añadir un tercer entorno sea una
  # línea y no una copia del archivo — que era el argumento con el que se
  # justificó fusionarlos.
  entornos_despliegue = toset(["dev", "prod"])
}

# El proveedor no cuesta nada y solo se declara una vez por cuenta.
#
# Sobre las huellas: desde 2023 AWS valida el certificado de GitHub contra su
# propio almacén de confianza y NO usa esta lista para este emisor. Se dejan las
# dos conocidas porque la API sigue aceptando el campo y omitirlo depende de la
# versión del proveedor; son inertes.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  thumbprint_list = [
    "6938fd4d98bab03faadb97b34396831e3780aea1",
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd",
  ]

  tags = { Name = "${var.proyecto}-github" }
}

# ── La frontera de permisos ────────────────────────────────────────────────

/**
 * El techo de todo rol que cree el despliegue.
 *
 * Una frontera de permisos no concede nada: limita lo máximo que un rol puede
 * llegar a tener, por mucho que después se le adjunten políticas. Es lo que
 * convierte «el despliegue puede crear roles» en algo acotado: aunque lograra
 * adjuntar `AdministratorAccess` a un rol nuevo, ese rol seguiría sin poder
 * tocar IAM ni la organización.
 *
 * Se escribe como «todo menos», y esa forma es deliberada. La lista de lo que
 * las Lambdas necesitan —logs, SSM, KMS, RDS, S3, interfaces de red— crece con
 * cada funcionalidad, y una frontera que hay que ampliar en cada despliegue se
 * acaba ampliando con un comodín. Lo que no crece es la lista de lo que ninguna
 * función de este proyecto necesitará jamás.
 *
 * `iam:*` entero, incluidas las lecturas: una función que puede listar roles y
 * políticas le entrega a quien la comprometa el mapa para buscar el siguiente
 * paso. Ninguna Lambda de Ondexia lee IAM.
 */
data "aws_iam_policy_document" "frontera_despliegue" {
  statement {
    sid       = "TechoGeneral"
    actions   = ["*"]
    resources = ["*"]
  }

  statement {
    sid    = "NuncaIdentidadNiCuenta"
    effect = "Deny"
    actions = [
      "iam:*",
      "sts:AssumeRole",
      "organizations:*",
      "account:*",
      "aws-portal:*",
      "billing:*",
      "ce:*",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "frontera_despliegue" {
  name        = "${var.proyecto}-frontera-despliegue"
  description = "Techo de permisos de todo rol creado por el despliegue"
  policy      = data.aws_iam_policy_document.frontera_despliegue.json

  tags = { Name = "${var.proyecto}-frontera-despliegue" }
}

# ── Confianza ──────────────────────────────────────────────────────────────

/**
 * La política de confianza. Aquí está el único riesgo real de todo esto.
 *
 * El error clásico es dejar floja la condición sobre `sub` —o no ponerla—: con
 * un comodín, CUALQUIER repositorio de GitHub, incluido el de un desconocido,
 * puede asumir este rol. No es una exageración teórica; es la forma en que estas
 * configuraciones se rompen.
 *
 * SE ACOTA POR ENVIRONMENT Y NO POR RAMA, y no es lo que parecía al principio.
 *
 * Cuando un job declara `environment:` —y deploy.yml lo hace, para poder exigir
 * aprobación manual en prod— GitHub cambia la forma del `sub`:
 *
 *   sin environment:  repo:…:ref:refs/heads/develop
 *   con environment:  repo:…:environment:dev
 *
 * Las dos formas son excluyentes: llega una o la otra, nunca las dos. Escribir
 * las de rama aquí sería dejar en el código una restricción que no se evalúa
 * jamás, lo que es peor que no tenerla — parece que acota y no acota nada.
 *
 * Sigue fuera `repo:…:pull_request`, que es el que importa: sin esa exclusión,
 * cualquiera que abriera una PR desde una bifurcación ejecutaría código con
 * estas credenciales.
 *
 * UN SOLO `sub` POR ROL. Esa es la corrección de C1: mientras la lista tenía dos
 * entradas, el entorno sin revisor abría el mismo rol que el entorno con
 * revisor, y la aprobación manual de prod era decorativa.
 *
 * QUÉ SE PIERDE Y DÓNDE SE RECUPERA
 *
 * El `sub` por environment no dice de qué rama viene la ejecución, así que esta
 * política ya no limita la rama. Ese límite se pone en GitHub, en las
 * «deployment branches» del propio environment: `prod` solo desde `main`,
 * `dev` solo desde `develop`.
 *
 * Y ahí está el hueco de hoy: esa opción, igual que la protección de ramas,
 * exige plan Pro o Team en repositorios privados. Mientras no lo haya, quien
 * tenga permiso de escritura puede lanzar el despliegue desde cualquier rama.
 * Con un solo desarrollador es una cuestión de disciplina; con dos, deja de
 * serlo.
 */
data "aws_iam_policy_document" "asumir_despliegue" {
  for_each = local.entornos_despliegue

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
      /*
       * Solo la forma con identificadores, que es la que GitHub emite.
       *
       * Estuvo también la legible —`repo:LuisVera22/ondexia:environment:dev`— y
       * se quitó por dos motivos. No se evalúa nunca, así que aparentaba acotar
       * sin acotar; y es por NOMBRE: el día que se libere ese nombre y otra
       * persona lo tome, esa entrada la autorizaría si GitHub volviera a la
       * forma corta. Los identificadores no tienen ese problema, que es
       * exactamente para lo que son inmutables.
       *
       * Nada de `StringLike` con asteriscos. `repo:LuisVera22*` casaría también
       * con una cuenta llamada `LuisVera22Falsa`, que es la clase de atajo por
       * el que estas políticas acaban abiertas.
       *
       * Si GitHub cambiara el formato, esto fallaría con AccessDenied. Es
       * recuperable en minutos: el `sub` recibido está en CloudTrail, en
       * `userIdentity.userName` del intento rechazado.
       */
      values = ["repo:${var.repositorio_github_inmutable}:environment:${each.key}"]
    }
  }
}

resource "aws_iam_role" "despliegue" {
  for_each = local.entornos_despliegue

  name               = "${var.proyecto}-despliegue-${each.key}"
  description        = "Asumido por GitHub Actions para aplicar Terraform en ${each.key}"
  assume_role_policy = data.aws_iam_policy_document.asumir_despliegue[each.key].json

  # Una hora. El despliegue completo —construir, migrar, publicar— tarda
  # minutos; darle margen de sobra sin llegar al máximo de doce horas.
  max_session_duration = 3600

  tags = { Name = "${var.proyecto}-despliegue-${each.key}" }
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
 * Lo que lo acota no es esta política sino tres cosas juntas: la de confianza
 * —que ahora admite un solo entorno—, la frontera de permisos que obliga a poner
 * en todo rol que cree, y la lista cerrada de políticas gestionadas que puede
 * adjuntar.
 *
 * Se usa PowerUserAccess más IAM acotado en lugar de AdministratorAccess: la
 * diferencia práctica es que este rol no puede tocar la facturación, ni la
 * organización, ni crear usuarios de IAM con claves permanentes — que es
 * justamente lo que haría alguien que quisiera dejarse una puerta abierta.
 */
resource "aws_iam_role_policy_attachment" "despliegue_poweruser" {
  for_each = aws_iam_role.despliegue

  role       = each.value.name
  policy_arn = "arn:aws:iam::aws:policy/PowerUserAccess"
}

data "aws_iam_policy_document" "despliegue_iam" {
  # PowerUserAccess deja fuera IAM casi por completo, y Terraform necesita
  # gestionar los roles de las funciones. Se concede sobre roles y políticas,
  # no sobre usuarios ni claves de acceso.
  #
  # `CreateRole` va aparte, más abajo, porque lleva condición.
  statement {
    sid = "GestionarRolesDelProyecto"
    actions = [
      "iam:DeleteRole",
      "iam:GetRole",
      "iam:UpdateRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:ListRoleTags",
      "iam:ListAttachedRolePolicies",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:GetRolePolicy",
      "iam:ListRolePolicies",
      "iam:ListInstanceProfilesForRole",
    ]
    resources = ["arn:aws:iam::${data.aws_caller_identity.actual.account_id}:role/${var.proyecto}-*"]
  }

  /**
   * Crear roles, SOLO con la frontera puesta.
   *
   * Es la mitad que hace útil a la frontera. Sin esta condición, el despliegue
   * crea un rol sin techo y le adjunta lo que quiera; con ella, todo rol que
   * nazca de este pipeline lleva el límite dentro y no hay forma de quitárselo
   * después —`PutRolePermissionsBoundary` y `DeleteRolePermissionsBoundary` no
   * están concedidos en ninguna parte de este documento—.
   *
   * Consecuencia práctica: todo `aws_iam_role` de esta configuración tiene que
   * declarar `permissions_boundary`. Si se olvida en uno nuevo, el apply falla
   * con AccessDenied sobre `iam:CreateRole` — ruidoso y en el sitio correcto.
   */
  statement {
    sid       = "CrearRolesSoloConFrontera"
    actions   = ["iam:CreateRole"]
    resources = ["arn:aws:iam::${data.aws_caller_identity.actual.account_id}:role/${var.proyecto}-*"]

    condition {
      test     = "StringEquals"
      variable = "iam:PermissionsBoundary"
      values   = [aws_iam_policy.frontera_despliegue.arn]
    }
  }

  /**
   * Adjuntar políticas gestionadas: lista cerrada.
   *
   * La versión anterior concedía `iam:AttachRolePolicy` sobre `role/ondexia-*`
   * sin decir QUÉ política. Eso incluye `AdministratorAccess`, y el propio rol
   * de despliegue se llama `ondexia-despliegue`: podía adjuntársela a sí mismo.
   * Esa era la última pieza de la cadena C1.
   *
   * La lista es exactamente lo que la infraestructura adjunta hoy. Añadir una
   * política gestionada nueva exige tocar esta lista, que es justo la revisión
   * que se quiere forzar.
   */
  statement {
    sid = "AdjuntarSoloPoliticasDeLambda"
    actions = [
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
    ]
    resources = ["arn:aws:iam::${data.aws_caller_identity.actual.account_id}:role/${var.proyecto}-*"]

    condition {
      test     = "ArnEquals"
      variable = "iam:PolicyARN"
      values = [
        "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole",
        "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole",
      ]
    }
  }

  /**
   * Ceder un rol solo a Lambda.
   *
   * `iam:PassRole` es el permiso que se pasa por alto en estas políticas: sin
   * condición, permite entregarle un rol existente a cualquier servicio que
   * ejecute código —EC2, ECS, Glue— y así usar sus permisos desde algo que el
   * atacante controla. Aquí todo rol que se cede es de una Lambda.
   */
  statement {
    sid       = "CederRolesSoloALambda"
    actions   = ["iam:PassRole"]
    resources = ["arn:aws:iam::${data.aws_caller_identity.actual.account_id}:role/${var.proyecto}-*"]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["lambda.amazonaws.com"]
    }
  }

  # Leer las políticas gestionadas del proyecto —la frontera es una— para que el
  # refresco del plan no muera con AccessDenied. Sin escritura: crear o cambiar
  # una política gestionada es cambiar la frontera, y eso se hace desde un
  # equipo, no desde CI.
  statement {
    sid = "LeerPoliticasDelProyecto"
    actions = [
      "iam:GetPolicy",
      "iam:GetPolicyVersion",
      "iam:ListPolicyVersions",
      "iam:ListEntitiesForPolicy",
    ]
    resources = ["arn:aws:iam::${data.aws_caller_identity.actual.account_id}:policy/${var.proyecto}-*"]
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

  # LEER el proveedor OIDC. Terraform lo tiene en su estado, así que lo refresca
  # en cada plan, y PowerUserAccess excluye IAM por completo. Sin esta concesión
  # el plan se calcula entero y luego aborta con exit 1 y este error:
  #
  #   Error: reading IAM OIDC Provider (...): AccessDenied: User:
  #   .../ondexia-despliegue is not authorized to perform:
  #   iam:GetOpenIDConnectProvider ... with an explicit deny in an
  #   identity-based policy
  #
  # El «explicit deny» era de aquí: la primera versión denegaba
  # `iam:*OpenIDConnectProvider*`, y ese comodín abarca también el Get. Una
  # denegación explícita gana sobre cualquier permiso, así que la protección
  # contra reescribir la propia identidad impedía además calcular el plan.
  statement {
    sid       = "LeerProveedorOidc"
    actions   = ["iam:GetOpenIDConnectProvider"]
    resources = ["*"]
  }

  # El proveedor OIDC no se modifica desde el despliegue: lo crea una persona
  # desde su equipo, una vez.
  #
  # La lista es cerrada en vez de un comodín, y eso tiene un coste honesto: si
  # AWS añadiera mañana una acción de escritura sobre proveedores OIDC, no
  # quedaría denegada. Se acepta porque el comodín tapa también las lecturas
  # —era el fallo de arriba— y porque para aprovechar ese hueco habría que estar
  # ya ejecutando código en este repositorio.
  statement {
    sid    = "NoTocarElProveedorOidc"
    effect = "Deny"
    actions = [
      "iam:CreateOpenIDConnectProvider",
      "iam:DeleteOpenIDConnectProvider",
      "iam:UpdateOpenIDConnectProviderThumbprint",
      "iam:AddClientIDToOpenIDConnectProvider",
      "iam:RemoveClientIDFromOpenIDConnectProvider",
      "iam:TagOpenIDConnectProvider",
      "iam:UntagOpenIDConnectProvider",
    ]
    resources = ["*"]
  }

  /**
   * Ningún rol de despliegue se toca a sí mismo ni a sus hermanos.
   *
   * Un rol que puede reescribir su propia política de confianza no está acotado
   * por ella: se concedería a sí mismo cualquier `sub`. Y uno que puede
   * adjuntarse políticas no está acotado por las suyas.
   *
   * La denegación cubre los cuatro roles de este archivo, no solo el que actúa:
   * si `ondexia-despliegue-dev` pudiera reescribir la confianza de
   * `ondexia-despliegue-prod`, la separación por entorno duraría un apply.
   *
   * El coste es real y se asume: Terraform ya no puede cambiar estos cuatro
   * roles desde CI. Un apply que no los modifica solo los lee, así que el
   * pipeline funciona; cambiar los permisos del propio despliegue exige aplicar
   * desde un equipo con credenciales de administrador, igual que crear el
   * proveedor OIDC o el bucket del estado. Es la propiedad que se quiere: la
   * identidad del pipeline no se modifica desde el pipeline.
   */
  statement {
    sid    = "NoTocarLaPropiaIdentidad"
    effect = "Deny"
    actions = [
      "iam:UpdateAssumeRolePolicy",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:DeleteRole",
      "iam:PutRolePermissionsBoundary",
      "iam:DeleteRolePermissionsBoundary",
    ]
    resources = concat(
      [for rol in aws_iam_role.despliegue : rol.arn],
      [for rol in aws_iam_role.plan : rol.arn],
    )
  }
}

resource "aws_iam_role_policy" "despliegue_iam" {
  for_each = aws_iam_role.despliegue

  name   = "${var.proyecto}-despliegue-iam"
  role   = each.value.id
  policy = data.aws_iam_policy_document.despliegue_iam.json
}

# ── Los roles que solo planifican ──────────────────────────────────────────

/**
 * Solo lectura, para que el plan se calcule ANTES de la aprobación.
 *
 * El entorno de GitHub que los usa —`dev-plan`, `prod-plan`— no lleva revisor:
 * si lo llevara, volveríamos al problema de M14, aprobar sin haber visto nada.
 * Que ese entorno sea el desprotegido es aceptable justamente porque el rol que
 * cuelga de él no puede escribir.
 */
data "aws_iam_policy_document" "asumir_plan" {
  for_each = local.entornos_despliegue

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
      values   = ["repo:${var.repositorio_github_inmutable}:environment:${each.key}-plan"]
    }
  }
}

resource "aws_iam_role" "plan" {
  for_each = local.entornos_despliegue

  name                 = "${var.proyecto}-plan-${each.key}"
  description          = "Asumido por GitHub Actions para calcular el plan de ${each.key}"
  assume_role_policy   = data.aws_iam_policy_document.asumir_plan[each.key].json
  max_session_duration = 3600

  tags = { Name = "${var.proyecto}-plan-${each.key}" }
}

# ReadOnlyAccess y nada más. `terraform plan` lee el estado del bucket y refresca
# los recursos; no escribe. El bloqueo del estado se desactiva en el workflow
# (`-lock=false`) precisamente para no necesitar escritura aquí: un plan que no
# aplica nada no necesita impedir que otro planifique a la vez.
resource "aws_iam_role_policy_attachment" "plan_lectura" {
  for_each = aws_iam_role.plan

  role       = each.value.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

output "roles_despliegue" {
  description = <<-TEXTO
    ARN de cada rol. Van como secretos DE ENTORNO en GitHub, no del repositorio:

      environment dev        AWS_DEPLOY_ROLE_ARN = <despliegue.dev>
      environment prod       AWS_DEPLOY_ROLE_ARN = <despliegue.prod>
      environment dev-plan   AWS_PLAN_ROLE_ARN   = <plan.dev>
      environment prod-plan  AWS_PLAN_ROLE_ARN   = <plan.prod>

    Que sean de entorno es parte del control: un secreto de repositorio lo lee
    cualquier job, y entonces el job de plan podría pedir el rol que aplica.

    No son secretos de verdad —un ARN no autoriza nada por sí solo, lo que
    autoriza es la política de confianza— pero se guardan como tales para no
    publicar el identificador de la cuenta en el repositorio.

    Además, en GitHub: `prod` con revisor requerido; `dev-plan` y `prod-plan`
    SIN revisor, que es lo que permite ver el plan antes de aprobar.
  TEXTO
  value = {
    despliegue = { for entorno, rol in aws_iam_role.despliegue : entorno => rol.arn }
    plan       = { for entorno, rol in aws_iam_role.plan : entorno => rol.arn }
  }
}
