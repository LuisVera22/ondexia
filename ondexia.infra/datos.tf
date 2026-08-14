/**
 * Base de datos.
 *
 * PostgreSQL en RDS, zona única, sin acceso público. Es el 90 % de la factura
 * de la v1 (DTE §4.7), así que cada opción de aquí tiene efecto directo en el
 * costo.
 *
 * Sin Multi-AZ por la restricción de costo R-12: el NFR-07 acepta un RTO de
 * 4 horas restaurando, en lugar de conmutación automática.
 */

resource "aws_db_subnet_group" "principal" {
  name       = local.nombre
  subnet_ids = aws_subnet.privada[*].id

  description = "Subredes privadas. La base no es alcanzable desde internet"
}

resource "random_password" "bd" {
  length = 32
  # RDS rechaza estos caracteres en la contraseña maestra.
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "aws_db_parameter_group" "principal" {
  name        = "${local.nombre}-pg"
  family      = "postgres17"
  description = "Ondexia ${var.entorno}"

  # Registra las sentencias que tardan más de un segundo. Con una instancia
  # pequeña, una consulta sin índice es la primera causa de que la aplicación
  # se sienta lenta, y sin esto no hay forma de saber cuál es.
  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_db_instance" "principal" {
  identifier = local.nombre

  engine         = "postgres"
  engine_version = "17"
  instance_class = var.clase_instancia_bd

  # Las versiones menores se aplican solas en la ventana de mantenimiento; las
  # mayores, nunca. Una mayor puede romper compatibilidad y debe ser una
  # decisión, no una sorpresa de madrugada.
  auto_minor_version_upgrade  = true
  allow_major_version_upgrade = false

  allocated_storage     = var.almacenamiento_bd_gb
  max_allocated_storage = var.almacenamiento_bd_gb * 5
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = "ondexia"
  username = "ondexia_admin"
  password = random_password.bd.result

  /**
   * Autenticación por IAM, que es lo que quita la contraseña de la API.
   *
   * La Lambda se conecta como `ondexia_app` con un token que genera firmando
   * localmente con las credenciales de su rol — sin llamada de red, igual que
   * al firmar una subida a S3. Por eso funciona en una subred sin NAT y por eso
   * no cuesta nada: la alternativa era un endpoint de interfaz a ~7.30 USD/mes
   * para alcanzar Secrets Manager.
   *
   * Activarlo no obliga a nadie: `ondexia_admin` sigue entrando con contraseña,
   * y es a propósito. Conceder `rds_iam` al usuario maestro le quitaría ese
   * método, y si la ruta de IAM fallara no quedaría forma de entrar a
   * arreglarlo — un candado con la llave dentro. Ver la V8.
   */
  iam_database_authentication_enabled = true

  db_subnet_group_name   = aws_db_subnet_group.principal.name
  vpc_security_group_ids = [aws_security_group.base_datos.id]
  multi_az               = false

  # Solo en dev, y solo alcanzable desde la IP que aplicó. Ver acceso_bd_publico
  # en variables.tf para lo que esto implica.
  publicly_accessible = local.bd_publica

  parameter_group_name = aws_db_parameter_group.principal.name

  backup_retention_period = var.retencion_respaldos_dias
  backup_window           = "07:00-08:00" # 02:00-03:00 en Perú
  maintenance_window      = "sun:08:00-sun:09:00"
  copy_tags_to_snapshot   = true

  # En producción, borrar la base exige quitar esta protección a propósito.
  deletion_protection = var.entorno == "prod"

  # Y aun así deja una copia. Un `terraform destroy` mal apuntado no puede ser
  # el final de los datos tributarios de un cliente.
  skip_final_snapshot       = var.entorno != "prod"
  final_snapshot_identifier = var.entorno == "prod" ? "${local.nombre}-final" : null

  performance_insights_enabled = false # tiene costo; se activa si hace falta diagnosticar

  enabled_cloudwatch_logs_exports = ["postgresql"]

  /**
   * La puerta de enlace tiene que existir ANTES de hacer pública la instancia.
   *
   * Terraform no lo deduce solo: `publicly_accessible` recibe un booleano
   * calculado, no una referencia al recurso, así que no hay arista en el grafo
   * y ambos se planifican en paralelo. Si RDS va primero, AWS lo rechaza:
   *
   *   InvalidVPCNetworkStateFault: Cannot create a publicly accessible
   *   DBInstance. The specified VPC has no internet gateway attached.
   *
   * Y el mensaje despista, porque la puerta de enlace SÍ está en el código —
   * solo que todavía no se había creado. Pasó al encender acceso_bd_publico.
   *
   * La ruta va también en la lista: sin ella la instancia tendría dirección
   * pública y ningún camino de vuelta.
   *
   * Esto ordena el ENCENDIDO. El apagado no se puede ordenar así —`depends_on`
   * no sitúa una actualización en sitio antes de la destrucción de aquello de lo
   * que depende— y por eso la puerta de enlace dejó de colgar del interruptor.
   * El razonamiento completo está en red.tf, sobre `aws_internet_gateway`.
   */
  depends_on = [
    aws_internet_gateway.principal,
    aws_route.salida_internet,
  ]

  lifecycle {
    # `local.bd_publica` ya deja el valor en falso fuera de dev, así que sin
    # esto un `acceso_bd_publico = true` en prod.tfvars no haría nada y nadie se
    # enteraría del intento. Esto lo convierte en un apply que se detiene.
    precondition {
      condition     = !var.acceso_bd_publico || var.entorno == "dev"
      error_message = "acceso_bd_publico solo vale en dev. La base de prod guarda datos tributarios de clientes y no se expone a internet: usa un bastion con EC2 Instance Connect Endpoint."
    }
  }

  tags = { Name = local.nombre }
}
