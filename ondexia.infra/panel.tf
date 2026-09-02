/**
 * El panel administrativo interno: su Lambda, su API y su identidad.
 *
 * Ver ondexia.docs/09-panel-administrativo.md §6.
 *
 * TODO SEPARADO DE LA API DE CLIENTES, Y ESE ES EL PUNTO
 *
 * Otra función, otra API, otro rol de IAM, otro rol de base de datos y otro
 * grupo de usuarios de Cognito. La alternativa —rutas nuevas en la API de
 * inquilinos— era menos trabajo y tenía un fallo que no se puede acotar después:
 * un error en el autorizador dejaría de ser un error para pasar a ser escalada de
 * privilegios entre clientes.
 *
 * Separado, la API de clientes no tiene credencial con la que leer otras cuentas
 * aunque su código se equivoque, y el panel no puede leer un comprobante aunque
 * el suyo se equivoque. No es una regla que se cumple: es una que no se puede
 * incumplir.
 */

locals {
  hay_panel = var.artefacto_panel != ""
}

# ── El artefacto ───────────────────────────────────────────────────────────

resource "aws_s3_object" "panel" {
  count = local.hay_panel ? 1 : 0

  bucket = aws_s3_bucket.artefactos.id
  # El hash en la clave hace que subir código nuevo sea un objeto nuevo, y que
  # Lambda se entere sin depender de que alguien recuerde cambiar una versión.
  key    = "panel/${filemd5(var.artefacto_panel)}.zip"
  source = var.artefacto_panel
  etag   = filemd5(var.artefacto_panel)

  tags = { Name = "${local.nombre}-artefacto-panel" }
}

# ── Identidad de ejecución ─────────────────────────────────────────────────

resource "aws_iam_role" "panel" {
  count = local.hay_panel ? 1 : 0

  name               = "${local.nombre}-panel"
  description        = "Ejecucion de la consola interna"
  assume_role_policy = data.aws_iam_policy_document.asumir_lambda.json

  /**
   * El techo, obligatorio: `iam:CreateRole` esta condicionado a que este rol
   * lleve exactamente esta frontera (ver despliegue.tf). Sin la linea, el apply
   * desde CI falla con AccessDenied al crear el rol.
   */
  permissions_boundary = aws_iam_policy.frontera_despliegue.arn

  tags = { Name = "${local.nombre}-panel" }
}

resource "aws_iam_role_policy_attachment" "panel_vpc" {
  count = local.hay_panel ? 1 : 0

  role       = aws_iam_role.panel[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

data "aws_iam_policy_document" "panel" {
  statement {
    sid     = "Logs"
    actions = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = [
      "arn:aws:logs:${var.region}:${data.aws_caller_identity.actual.account_id}:log-group:/aws/lambda/${local.nombre}-panel:*",
    ]
  }

  /**
   * Conectar a la base como `ondexia_panel`, y solo como ese.
   *
   * El nombre del usuario forma parte del recurso: esta política NO permite
   * conectarse como `ondexia_app` ni como el maestro. Junto con las concesiones
   * de la V9 —que no incluyen las tablas de comprobantes— es lo que hace que la
   * consola no pueda leer documentos tributarios de un cliente aunque su código
   * lo intentara.
   */
  statement {
    sid     = "ConectarBaseConIam"
    actions = ["rds-db:connect"]
    resources = [
      "arn:aws:rds-db:${var.region}:${data.aws_caller_identity.actual.account_id}:dbuser:${aws_db_instance.principal.resource_id}/ondexia_panel",
    ]
  }
}

resource "aws_iam_role_policy" "panel" {
  count = local.hay_panel ? 1 : 0

  name   = "${local.nombre}-panel"
  role   = aws_iam_role.panel[0].id
  policy = data.aws_iam_policy_document.panel.json
}

resource "aws_cloudwatch_log_group" "panel" {
  count = local.hay_panel ? 1 : 0

  name              = "/aws/lambda/${local.nombre}-panel"
  retention_in_days = var.retencion_logs_dias

  tags = { Name = "${local.nombre}-panel" }
}

# ── La función ─────────────────────────────────────────────────────────────

resource "aws_lambda_function" "panel" {
  count = local.hay_panel ? 1 : 0

  function_name = "${local.nombre}-panel"
  role          = aws_iam_role.panel[0].arn
  runtime       = "java21"
  handler       = "com.ondexia.admin.ManejadorLambda::handleRequest"

  s3_bucket        = aws_s3_bucket.artefactos.id
  s3_key           = aws_s3_object.panel[0].key
  source_code_hash = filebase64sha256(var.artefacto_panel)

  # Más memoria que la mínima porque en Lambda la CPU se asigna en proporción, y
  # aquí lo que duele es el arranque de Spring. Menos que la API porque no hay
  # Hibernate que inicializar: el panel usa SQL directo.
  memory_size = 768
  timeout     = 30

  /**
   * SIN SnapStart, a propósito. Con versiones publicadas, también a propósito.
   *
   * Son dos decisiones distintas y conviene no confundirlas, porque en la API
   * van juntas:
   *
   *   - Sin SnapStart porque son pocas invocaciones al día de gente que trabaja
   *     aquí, un arranque en frío de segundos es tolerable en una consola
   *     interna, y a cambio se evita lo que más ha costado en los despliegues de
   *     la API: la instantánea tarda minutos y cualquier fallo de arranque deja
   *     la versión en `Failed` en vez de dar un error legible.
   *
   *   - Con `publish` y alias porque servir $LATEST es servir un blanco móvil:
   *     lo que corre cambia con cada despliegue sin que quede constancia de qué
   *     había antes, y volver atrás obliga a reconstruir el artefacto. Una
   *     versión es inmutable y el alias dice cuál sirve, así que revertir es
   *     mover el alias — segundos, sin pipeline.
   *
   * Que $LATEST siga existiendo no es un problema: nadie lo invoca, porque la
   * integración apunta al alias y el permiso de invocación lleva su cualificador.
   */
  publish = true

  vpc_config {
    subnet_ids         = aws_subnet.privada[*].id
    security_group_ids = [aws_security_group.lambda.id]
  }

  environment {
    variables = {
      SPRING_PROFILES_ACTIVE = "aws"

      BD_HOST   = aws_db_instance.principal.address
      BD_PUERTO = tostring(aws_db_instance.principal.port)
      BD_NOMBRE = aws_db_instance.principal.db_name
      # Sin contraseña: la credencial es un token de IAM firmado en cada
      # conexión. El nombre tiene que coincidir letra por letra con el rol que
      # crea la V9 y con el que autoriza la política de arriba.
      BD_USUARIO = "ondexia_panel"

      # El grupo de PERSONAL. Un token de cliente está firmado por el otro pool y
      # el autorizador de esta API lo rechaza: la separación es criptográfica, no
      # de configuración.
      COGNITO_EMISOR_PERSONAL = "https://${aws_cognito_user_pool.personal.endpoint}"

      # Que el token sea para ESTA consola y no para otra aplicación del mismo
      # grupo. Es la misma audiencia que valida el autorizador de la pasarela; la
      # aplicación la vuelve a mirar porque comprobarlo no cuesta una salida a
      # internet, y lo que sí la costaba —la firma— se dejó en la pasarela
      # (ver TokenDeLaPasarela).
      COGNITO_CLIENTE_PANEL = aws_cognito_user_pool_client.panel.id

      # Las claves publicas del pool de PERSONAL, para verificar la firma dentro
      # de la funcion (hallazgo A2). Mismo mecanismo que en la API; el data
      # source esta al final de este archivo.
      COGNITO_JWKS = data.http.jwks_personal.response_body

      # El preflight lo responde la pasarela desde el hallazgo A2. Esto sigue
      # haciendo falta para las peticiones REALES: Spring comprueba el Origin en
      # cada una y sin el valor correcto responde 403 a todas.
      CORS_ORIGENES = local.origen_panel

      ONDEXIA_VERSION = substr(filemd5(var.artefacto_panel), 0, 12)
    }
  }

  depends_on = [aws_cloudwatch_log_group.panel]

  tags = { Name = "${local.nombre}-panel" }
}

/**
 * El alias es lo que se invoca. Nunca $LATEST.
 *
 * Sigue a la última versión publicada, que Terraform actualiza en cada
 * despliegue porque ONDEXIA_VERSION cambia con el hash del artefacto. El valor
 * de tener el alias en medio es poder deshacer: si una versión sale mal, apuntar
 * el alias a la anterior devuelve el servicio sin volver a construir nada.
 *
 * Es además donde se repartiría el tráfico entre dos versiones el día que un
 * despliegue gradual haga falta.
 */
resource "aws_lambda_alias" "panel" {
  count = local.hay_panel ? 1 : 0

  name             = "activo"
  description      = "Version que sirve la consola interna"
  function_name    = aws_lambda_function.panel[0].function_name
  function_version = aws_lambda_function.panel[0].version
}

# ── Cliente de Cognito para la consola ─────────────────────────────────────

resource "aws_cognito_user_pool_client" "panel" {
  name         = "${local.nombre}-panel"
  user_pool_id = aws_cognito_user_pool.personal.id

  generate_secret = false

  # SRP igual que el cliente de inquilinos: la interfaz alojada valida la
  # contraseña con este flujo y este mismo cliente. Sin él, un acceso correcto
  # sale como «Incorrect username or password».
  #
  # ALLOW_USER_PASSWORD_AUTH sigue fuera: aceptaría la contraseña en claro.
  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
  ]

  supported_identity_providers         = ["COGNITO"]
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]

  callback_urls = [local.origen_panel]
  logout_urls   = [local.origen_panel]

  # Lo que este cliente puede leer y escribir del perfil, igual que el del SPA
  # (tabla de bajas). Sin la restriccion, el token del panel podia modificar
  # cualquier atributo del usuario de personal — incluido el correo, que desde
  # A6 ya no identifica a nadie en la bitacora pero sigue siendo la via de
  # recuperacion de la cuenta.
  read_attributes  = ["email", "email_verified", "name"]
  write_attributes = ["name"]

  # Una hora de acceso y ocho de sesión. Más corto que el de clientes: una
  # sesión abierta aquí ve las cuentas de todos.
  access_token_validity  = 60
  id_token_validity      = 60
  refresh_token_validity = 8
  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "hours"
  }

  # Igual que el cliente de inquilinos (hallazgo C3): revocacion para que
  # `cerrar()` tenga a donde llamar, y rotacion para que un refresco robado deje
  # de servir en cuanto el legitimo renueve. Aqui importa mas que en ninguna
  # parte: una sesion de esta consola ve las cuentas de todos los clientes.
  enable_token_revocation = true

  refresh_token_rotation {
    feature                    = "ENABLED"
    retry_grace_period_seconds = 60
  }

  prevent_user_existence_errors = "ENABLED"
}

/**
 * Interfaz alojada del grupo de personal.
 *
 * Sin dominio no hay `/oauth2/authorize` ni `/login`, es decir: no hay donde
 * escribir la contraseña ni donde resolver el segundo factor. El SPA de la
 * consola redirige aquí y vuelve con un código.
 *
 * Que la pantalla de acceso la sirva Cognito y no nosotros es lo que mantiene la
 * contraseña y el TOTP fuera de nuestro código — no podemos filtrar lo que nunca
 * pasa por nuestras manos.
 */
resource "aws_cognito_user_pool_domain" "personal" {
  domain       = "${local.nombre}-panel-${local.sufijo}"
  user_pool_id = aws_cognito_user_pool.personal.id
}

# ── La API de la consola ───────────────────────────────────────────────────

resource "aws_apigatewayv2_api" "panel" {
  count = local.hay_panel ? 1 : 0

  name          = "${local.nombre}-panel"
  protocol_type = "HTTP"
  description   = "Consola interna de Ondexia"

  cors_configuration {
    # Solo el propio panel. Nada de comodines: esta API responde con datos de
    # todas las cuentas cliente.
    allow_origins = [local.origen_panel]
    allow_methods = ["GET", "PUT", "OPTIONS"]
    allow_headers = ["authorization", "content-type"]
    max_age       = 3600
  }

  tags = { Name = "${local.nombre}-panel" }
}

resource "aws_apigatewayv2_authorizer" "personal" {
  count = local.hay_panel ? 1 : 0

  api_id           = aws_apigatewayv2_api.panel[0].id
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]
  name             = "cognito-personal"

  jwt_configuration {
    audience = [aws_cognito_user_pool_client.panel.id]
    issuer   = "https://${aws_cognito_user_pool.personal.endpoint}"
  }
}

resource "aws_apigatewayv2_integration" "panel" {
  count = local.hay_panel ? 1 : 0

  api_id           = aws_apigatewayv2_api.panel[0].id
  integration_type = "AWS_PROXY"
  # El alias, no la función: invocar la función a secas ejecuta $LATEST, que es
  # justo lo que el alias existe para evitar.
  integration_uri        = aws_lambda_alias.panel[0].invoke_arn
  payload_format_version = "2.0"
  timeout_milliseconds   = 30000
}

# La señal de vida, sin autorizador: la consulta el despliegue.
resource "aws_apigatewayv2_route" "panel_salud" {
  count = local.hay_panel ? 1 : 0

  api_id    = aws_apigatewayv2_api.panel[0].id
  route_key = "GET /salud"
  target    = "integrations/${aws_apigatewayv2_integration.panel[0].id}"
}

# Todo lo demás exige token del grupo de personal. El autorizador de la pasarela
# rechaza antes de invocar la función, así que un token inválido ni siquiera
# arranca un contenedor.
resource "aws_apigatewayv2_route" "panel_todo" {
  count = local.hay_panel ? 1 : 0

  api_id             = aws_apigatewayv2_api.panel[0].id
  route_key          = "$default"
  target             = "integrations/${aws_apigatewayv2_integration.panel[0].id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.personal[0].id
}

/**
 * NO hay ruta `OPTIONS /{{proxy+}}`, y esa ausencia es el arreglo (hallazgo A2).
 *
 * La habia, sin autorizador, apuntando a la misma integracion que todo lo demas:
 * una puerta por la que se llegaba a la funcion sin presentar ningun token. Se
 * habia anadido por un motivo real —el navegador manda el preflight SIN
 * credenciales, asi que caia en la ruta protegida y el autorizador respondia 401,
 * y el sintoma engana: la sesion se abre bien, el token es valido, y aun asi toda
 * llamada falla con un error de red sin cuerpo—.
 *
 * Pero la solucion era otra. API Gateway responde el preflight POR SU CUENTA en
 * cuanto la API declara `cors_configuration`, sin invocar la integracion:
 *
 *   «If you configure CORS for an HTTP API, API Gateway automatically sends a
 *   response to preflight OPTIONS requests, even if there isn't an OPTIONS route
 *   configured.»
 *
 * Es decir, la ruta no hacia falta ni siquiera para lo que la motivo. Quitarla
 * cierra la unica via de invocacion sin autorizador que existia — la premisa
 * «la pasarela es la unica puerta» pasa a ser cierta.
 *
 * Consecuencia para la aplicacion: Spring ya no responde ningun preflight, asi
 * que las cabeceras las pone entera la pasarela. `CORS_ORIGENES` sigue
 * importando para las peticiones reales.
 */

resource "aws_cloudwatch_log_group" "panel_api_gateway" {
  count = local.hay_panel ? 1 : 0

  name              = "/aws/apigateway/${local.nombre}-panel"
  retention_in_days = var.retencion_logs_dias

  tags = { Name = "${local.nombre}-panel-api-gateway" }
}

resource "aws_apigatewayv2_stage" "panel" {
  count = local.hay_panel ? 1 : 0

  api_id      = aws_apigatewayv2_api.panel[0].id
  name        = "$default"
  auto_deploy = true

  /**
   * Registro de acceso, que no tenia (hallazgo A5).
   *
   * La API de clientes lo lleva desde el principio y esta no. Es exactamente al
   * reves de lo que conviene: aqui cada peticion la hace una persona de dentro
   * sobre los datos de un cliente, que es el acceso que mas falta hace poder
   * reconstruir. Sin esto, la unica huella de quien entro al panel y a que era
   * la bitacora de la aplicacion — que solo registra lo que CAMBIA, no lo que se
   * mira.
   *
   * Se registra el sub del autorizador, no el correo: el correo lo cambia su
   * dueno. Mismo motivo que A6.
   */
  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.panel_api_gateway[0].arn
    format = jsonencode({
      solicitudId = "$context.requestId"
      ip          = "$context.identity.sourceIp"
      metodo      = "$context.httpMethod"
      ruta        = "$context.path"
      estado      = "$context.status"
      latenciaMs  = "$context.responseLatency"
      operador    = "$context.authorizer.claims.sub"
      momento     = "$context.requestTime"
    })
  }

  /**
   * Techo bajo, porque el uso real es bajo (hallazgo A5).
   *
   * Esta consola la usan dos o tres personas. Cinco por segundo con rafagas de
   * veinte sobra para eso, y convierte en ruidoso cualquier recorrido automatico
   * de las cuentas: sin techo, un token de operador robado permite volcar la
   * lista entera de clientes tan rapido como aguante la Lambda.
   */
  default_route_settings {
    throttling_burst_limit = 20
    throttling_rate_limit  = 5
  }

  tags = { Name = "${local.nombre}-panel" }
}

resource "aws_lambda_permission" "panel" {
  count = local.hay_panel ? 1 : 0

  statement_id  = "AllowAPIGatewayInvokePanel"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.panel[0].function_name
  principal     = "apigateway.amazonaws.com"

  # El permiso es sobre el alias, no sobre la función entera. Sin el
  # cualificador, la pasarela podría invocar $LATEST además del alias.
  qualifier = aws_lambda_alias.panel[0].name

  /**
   * Esta línea es la que sostiene la decisión de no revalidar la firma dentro
   * de la aplicación.
   *
   * El único principal autorizado es esta API, y solo esta: no hay URL de
   * función, ni otro disparador, ni invocación directa. Todo lo que llega al
   * código pasó antes por el autorizador JWT de la ruta $default.
   *
   * Si alguna vez se añade otro disparador aquí, hay que volver a
   * TokenDeLaPasarela: dejaría de ser cierto que alguien comprobó la firma.
   */
  source_arn = "${aws_apigatewayv2_api.panel[0].execution_arn}/*/*"
}

/**
 * El JWKS del pool de PERSONAL, descargado al aplicar (hallazgo A2).
 *
 * Aqui el hallazgo era mas grave que en la API: `TokenDeLaPasarela` aceptaba
 * cualquier firma, incluida `alg: none`, y la ruta `OPTIONS /{proxy+}` llega a
 * esta misma funcion SIN pasar por el autorizador. Hoy no ejecuta ningun
 * controlador porque todos son GET o PUT, pero un `@RequestMapping` sin metodo
 * bastaria para convertirlo en toma total de la consola.
 *
 * Ver la nota equivalente en api.tf sobre la rotacion de claves.
 */
data "http" "jwks_personal" {
  url = "https://cognito-idp.${var.region}.amazonaws.com/${aws_cognito_user_pool.personal.id}/.well-known/jwks.json"

  lifecycle {
    postcondition {
      condition     = self.status_code == 200
      error_message = "No se pudo descargar el JWKS del pool de personal (HTTP ${self.status_code})."
    }
  }
}
