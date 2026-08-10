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
