/**
 * Red.
 *
 * La VPC no tiene puerta de enlace a internet ni instancia NAT, y es
 * deliberado: en la v1 nada necesita salir (DTE §4.8). Eso ahorra la NAT y su
 * IP pública —unos 6.65 USD/mes— pero impone tres reglas que están anotadas en
 * el DTE y que hay que respetar, o el ahorro se convierte en un endpoint de
 * interfaz más caro que la NAT que se evitó.
 *
 * Cuando entre SUNAT habrá que añadir subred pública, puerta de enlace e
 * instancia NAT con IP fija, porque SUNAT exige un origen estable.
 */

data "aws_availability_zones" "disponibles" {
  state = "available"
}

locals {
  nombre = "${var.proyecto}-${var.entorno}"

  # Dos zonas. RDS exige un grupo de subredes que abarque al menos dos aunque
  # la instancia sea de zona única: es un requisito del servicio, no una
  # decisión de alta disponibilidad.
  zonas = slice(data.aws_availability_zones.disponibles.names, 0, 2)

  # El `&&` no es redundante con la precondición de la instancia: esta línea
  # hace que el valor sea falso en prod aunque alguien lo encienda, y la
  # precondición hace que además se entere. Silencioso y seguro, o ruidoso e
  # inseguro, son dos formas de fallar; esto es ruidoso y seguro.
  bd_publica = var.acceso_bd_publico && var.entorno == "dev"
}

# La IP saliente de quien ejecuta el apply. Se consulta a un servicio de AWS
# que devuelve la dirección en texto plano y nada más.
#
# Ojo con la consecuencia: la regla del grupo de seguridad queda atada a la red
# desde la que se aplicó. Aplicar desde otra red —o que el proveedor rote la IP
# doméstica— reescribe la regla en el siguiente plan. Eso es visible en el diff,
# que es donde debe verse.
#
# Y de ahí se sigue algo que no era evidente al escribirlo: «quien aplica» dejó
# de ser una persona. Aplicado desde GitHub Actions, esto abre el 5432 a la IP
# efímera del runner —una dirección de Azure que después es de otro— y la
# reescribe en cada ejecución. Por eso `acceso_bd_publico` está en false en
# dev.tfvars, con el detalle allí: es una variable que solo se enciende para un
# apply desde el equipo.
data "http" "ip_de_quien_aplica" {
  count = local.bd_publica ? 1 : 0
  url   = "https://checkip.amazonaws.com"
}

resource "aws_vpc" "principal" {
  cidr_block           = "10.20.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = local.nombre }
}

resource "aws_subnet" "privada" {
  count = 2

  vpc_id            = aws_vpc.principal.id
  cidr_block        = cidrsubnet(aws_vpc.principal.cidr_block, 8, count.index)
  availability_zone = local.zonas[count.index]

  # Sin IP pública: nada en esta subred debe ser alcanzable desde internet.
  map_public_ip_on_launch = false

  tags = { Name = "${local.nombre}-privada-${count.index + 1}" }
}

resource "aws_route_table" "privada" {
  vpc_id = aws_vpc.principal.id

  # Sin rutas. La única salida es el endpoint de S3, que se asocia abajo.
  tags = { Name = "${local.nombre}-privada" }
}

resource "aws_route_table_association" "privada" {
  count = 2

  subnet_id      = aws_subnet.privada[count.index].id
  route_table_id = aws_route_table.privada.id
}

/**
 * Puerta de enlace a internet — en dev siempre, en prod nunca.
 *
 * No contradice la cabecera de este archivo: sigue sin haber NAT, y la Lambda
 * sigue sin poder salir. Una función en subred privada no recibe IP pública en
 * sus interfaces, así que una ruta por defecto no le sirve de nada; hace falta
 * NAT, y no la hay. Lo único que la ruta de abajo habilita es el tráfico de
 * vuelta de la RDS, que sí tiene IP pública cuando `publicly_accessible` está
 * activo.
 *
 * La puerta de enlace no cuesta nada por existir. Lo que se paga es la
 * transferencia de salida, y consultar una base de datos de dev mueve
 * kilobytes.
 *
 * POR QUÉ NO CUELGA DE `local.bd_publica`, QUE ES LO QUE PARECERÍA CORRECTO
 *
 * Porque desatarla es una operación que Terraform no sabe secuenciar. Al apagar
 * `acceso_bd_publico`, la instancia pasa a `publicly_accessible = false` —una
 * actualización en sitio— y la puerta de enlace se destruye. El `depends_on` de
 * la instancia ordena creaciones y destrucciones, pero NO ordena una
 * actualización en sitio frente a la destrucción de su dependencia: Terraform
 * lanzó el `detach` sin haber soltado antes la dirección pública, y AWS lo
 * rechaza durante veinte minutos de reintentos antes de rendirse:
 *
 *   DependencyViolation: Network vpc-… has some mapped public address(es).
 *   Please unmap those public address(es) before detaching the gateway.
 *
 * Dejarla presente en dev elimina la carrera entera: nunca hay `detach`, y el
 * interruptor se queda con las tres cosas que sí se pueden apagar sin orden
 * —la ruta, la regla del 5432 y la dirección pública de RDS—. De paso desaparece
 * también el problema inverso, el `InvalidVPCNetworkStateFault` al encenderlo.
 *
 * Y lo que se conserva es lo que importa: en prod NO hay puerta de enlace, y esa
 * sigue siendo una afirmación absoluta. En dev, una puerta de enlace adjunta sin
 * ruta hacia ella no habilita nada — alcanzar algo desde internet necesita las
 * tres a la vez: ruta, dirección pública y regla del grupo de seguridad.
 */
resource "aws_internet_gateway" "principal" {
  count = var.entorno == "dev" ? 1 : 0

  vpc_id = aws_vpc.principal.id

  tags = { Name = "${local.nombre}-igw" }
}

# Se declara suelta y no como bloque `route` dentro de la tabla: los bloques
# inline y los recursos `aws_route` sobre la misma tabla se pisan entre sí, y
# aquí la ruta tiene que poder desaparecer sin tocar la tabla.
resource "aws_route" "salida_internet" {
  count = local.bd_publica ? 1 : 0

  route_table_id         = aws_route_table.privada.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.principal[0].id
}

/**
 * Endpoint de S3, de tipo puerta de enlace.
 *
 * Obligatorio y gratuito. Sin él, una Lambda en subred privada sin NAT no
 * tiene ninguna ruta hacia S3 y las llamadas fallan por tiempo de espera, que
 * es un síntoma bastante confuso de diagnosticar.
 *
 * Los de tipo interfaz —los que harían falta para alcanzar Secrets Manager o
 * la API de Cognito— cuestan ~7.30 USD/mes cada uno. Por eso las credenciales
 * van en variables de entorno cifradas y la administración de usuarios la hace
 * el SPA contra Cognito.
 */
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.principal.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.privada.id]

  tags = { Name = "${local.nombre}-s3" }
}

# ── Grupos de seguridad ────────────────────────────────────────────────────

resource "aws_security_group" "lambda" {
  name        = "${local.nombre}-lambda"
  description = "Lambda de la API"
  vpc_id      = aws_vpc.principal.id

  tags = { Name = "${local.nombre}-lambda" }
}

resource "aws_security_group" "base_datos" {
  name        = "${local.nombre}-bd"
  description = "PostgreSQL. Solo alcanzable desde la Lambda"
  vpc_id      = aws_vpc.principal.id

  tags = { Name = "${local.nombre}-bd" }
}

# La regla se define por grupo de origen y no por rango de direcciones: así el
# permiso sigue a la Lambda aunque cambie de subred o de IP.
resource "aws_vpc_security_group_ingress_rule" "bd_desde_lambda" {
  security_group_id            = aws_security_group.base_datos.id
  referenced_security_group_id = aws_security_group.lambda.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
  description                  = "PostgreSQL desde la Lambda de la API"
}

# La única puerta que separa la base de internet cuando dev está abierto. Un
# /32: no un rango de la operadora, no 0.0.0.0/0 «mientras pruebo».
resource "aws_vpc_security_group_ingress_rule" "bd_desde_desarrollo" {
  count = local.bd_publica ? 1 : 0

  security_group_id = aws_security_group.base_datos.id
  cidr_ipv4         = "${chomp(data.http.ip_de_quien_aplica[0].response_body)}/32"
  from_port         = 5432
  to_port           = 5432
  ip_protocol       = "tcp"
  description       = "PostgreSQL desde el equipo que aplico Terraform (solo dev)"
}

resource "aws_vpc_security_group_egress_rule" "lambda_hacia_bd" {
  security_group_id            = aws_security_group.lambda.id
  referenced_security_group_id = aws_security_group.base_datos.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
  description                  = "PostgreSQL"
}

# S3 se alcanza por el endpoint, cuyo destino es un rango propio de AWS que
# cambia. La lista gestionada lo resuelve sin fijar direcciones a mano.
data "aws_prefix_list" "s3" {
  name = "com.amazonaws.${var.region}.s3"
}

resource "aws_vpc_security_group_egress_rule" "lambda_hacia_s3" {
  security_group_id = aws_security_group.lambda.id
  prefix_list_id    = data.aws_prefix_list.s3.id
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
  description       = "S3 por el endpoint de puerta de enlace"
}
