# ondexia.infra

Infraestructura de Ondexia en AWS, con Terraform. Corresponde a la **v1** del
diagrama de arquitectura: un cliente, dos usuarios, **sin integración SUNAT**.

Ver [`../ondexia.docs/arquitectura-aws.drawio`](../ondexia.docs/arquitectura-aws.drawio),
página «v1 — primera versión».

## Requisitos

- Terraform >= 1.9
- Credenciales de AWS con permisos de administrador para el primer despliegue

## Primer despliegue

**Una sola vez en la vida del proyecto**, crear el bucket donde vive el estado:

```bash
cd bootstrap
terraform init
terraform apply -var='principales_con_acceso_total=["arn:aws:iam::TU_ID_DE_CUENTA:user/TU_USUARIO"]'
```

La variable lista a las personas que pueden leer y escribir el estado de
**todos** los entornos. Sin ella, cada prefijo del bucket (`ondexia/dev/`,
`ondexia/prod/`) solo lo tocan los roles de despliegue y de plan de ese entorno
y el usuario raíz de la cuenta: es lo que impide que el rol de solo lectura de
`dev`, que corre en un entorno de GitHub sin revisor, baje el estado de `prod`
con la contraseña maestra dentro. Si vas a ejecutar `terraform plan` o `apply`
desde tu máquina, tu identidad tiene que estar en la lista; los roles del CI no,
que ya entran por nombre.

Después, desde esta carpeta, apuntar el backend al bucket que imprimió:

```bash
terraform init -backend-config="bucket=ondexia-tfstate-TU_ID_DE_CUENTA" -backend-config="key=ondexia/prod/terraform.tfstate" -backend-config="region=us-east-1"
```

Y desplegar:

```bash
terraform apply -var-file=entornos/prod.tfvars
```

> **La clave del estado lleva el entorno, y no es un detalle de orden.** Ni
> `bucket` ni `key` están fijados en `versions.tf`: es configuración parcial a
> propósito. Con una clave fija, `dev` y `prod` compartirían estado — el segundo
> `apply` creería que los recursos del primero son suyos y los reconfiguraría o
> los destruiría. Dicho de otro modo: el primer despliegue de `dev` se llevaría
> por delante producción.
>
> Para cambiar de entorno hay que reinicializar con la otra clave y
> `-reconfigure`.

## Despliegue desde CI

Lo normal no es aplicar desde tu máquina, sino desde
[`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml), que se
dispara a mano eligiendo entorno. Hace lo mismo que los comandos de arriba, más
construir el frontend y publicarlo en S3 con invalidación de CloudFront.

Tiene una casilla **«Solo mostrar el plan, sin aplicar»**: es la forma de leer
un plan contra la cuenta real sin tocar nada, que conviene usar antes del primer
`apply` de verdad.

Requisitos en GitHub:

| | |
|---|---|
| Secreto `AWS_DEPLOY_ROLE_ARN` | ARN del rol que asume el workflow por OIDC. Su política de confianza debe restringirse **a este repositorio**, o cualquier otro de la organización podría asumirlo |
| Entorno `prod` con revisor requerido | Es la aprobación manual que exige el DTE §10.2 |
| Secreto `NVD_API_KEY` | Opcional pero recomendado. Sin él se omite el análisis de dependencias con un aviso |

## Solo la landing

Hay un huevo y una gallina que conviene ver antes de intentarlo: **el rol que
usa GitHub Actions lo crea Terraform**, así que el primer `apply` no puede venir
de GitHub Actions. Sale de tu equipo, una vez.

Y hay un segundo motivo para empezar por aquí. La landing es lo único
desplegable que no depende del backend (doc 01 §8), pero un `apply` completo
levanta además RDS, que es el **90 % de la factura**. Para ver una página
estática eso no tiene sentido, así que `deploy.yml` trae la casilla **«Solo la
landing»**: acota el plan con `-target` al sitio estático y se salta el backend,
las migraciones, la SPA y el panel.

### Lo que hay que hacer una vez, en este orden

**1. El bucket del estado** — «Primer despliegue», más arriba.

**2. La identidad de GitHub, desde tu equipo.** Es el paso que rompe el círculo,
y el único que crea los roles del pipeline. Que se aplique desde un equipo no es
comodidad: el propio pipeline tiene DENEGADO modificar estos cuatro roles, así
que cambiarlos desde CI no funcionaría aunque se intentara (ver
`despliegue.tf`, statement `NoTocarLaPropiaIdentidad`).

```bash
terraform apply -var-file=entornos/dev.tfvars -target=aws_iam_policy.frontera_despliegue -target=aws_iam_role.despliegue -target=aws_iam_role_policy.despliegue_iam -target=aws_iam_role_policy_attachment.despliegue_poweruser -target=aws_iam_role.plan -target=aws_iam_role_policy_attachment.plan_lectura
```

**3. Guardar los cuatro ARN como secretos DE ENTORNO.** No del repositorio: un
secreto de repositorio lo lee cualquier job, y entonces el job que solo
planifica podría pedir el rol que aplica. `terraform output -json
roles_despliegue` da los cuatro.

| Environment | Secreto | Valor |
|---|---|---|
| `dev` | `AWS_DEPLOY_ROLE_ARN` | `despliegue.dev` |
| `prod` | `AWS_DEPLOY_ROLE_ARN` | `despliegue.prod` |
| `dev-plan` | `AWS_PLAN_ROLE_ARN` | `plan.dev` |
| `prod-plan` | `AWS_PLAN_ROLE_ARN` | `plan.prod` |

**4. Crear los cuatro entornos en GitHub.** Sin el entorno, el `sub` del token
OIDC no tiene la forma que espera la política de confianza y AWS responde
`AccessDenied` — ver el comentario largo de `despliegue.tf`.

A `prod` ponerle **revisor requerido** (DTE §10.2). A `dev-plan` y `prod-plan`,
NO: son los que calculan el plan que el revisor lee antes de aprobar, y pedirles
aprobación devolvería el problema que resuelven. El rol que usan es de solo
lectura, que es lo que hace aceptable que estén desprotegidos.

**5. El rol de las migraciones, una vez por entorno.** La Lambda que migra se
conecta como `ondexia_migraciones` con un token de IAM, no con la contraseña
maestra (hallazgo A3). Ese rol de PostgreSQL tiene que existir antes del primer
despliegue, y no lo puede crear la propia migración: es el rol con el que se
conecta la función que la ejecuta.

El SQL, con las instrucciones dentro, está en
`bootstrap/rol-migraciones.sql`. Tampoco lo puede crear Terraform — el proveedor
de PostgreSQL necesitaría alcanzar la instancia por red, y RDS no es pública.

**6. Lanzar `deploy.yml`** con entorno `dev`, «Solo la landing» marcado y «Solo
mostrar el plan» **también marcado**. Lee el plan en el resumen de la ejecución:
deberían salir cuatro recursos y ninguno de ellos una base de datos.

**7. Repetir sin «solo plan».** La URL sale en el resumen, en la fila `Landing`.

### Si el despliegue muere en «Asumir rol de solo lectura»

```
Credentials could not be loaded, please check your action inputs:
Could not load credentials from any providers
```

**Significa que los pasos 2, 3 y 4 no están hechos**, no que haya un fallo en el
workflow. Y conviene saber leerlo, porque el mensaje no lo dice: ese texto es de
`configure-aws-credentials` cuando **no recibe ningún rol que asumir** y cae a la
cadena de credenciales por omisión, que en un runner está vacía. Es decir,
`secrets.AWS_PLAN_ROLE_ARN` llegó vacío.

Se distingue de los otros dos fallos de la misma zona por el texto:

| Lo que dice | Qué falta |
|---|---|
| `Could not load credentials from any providers` | El secreto está vacío: falta el entorno `<entorno>-plan` en GitHub, o el secreto dentro de él (pasos 3 y 4) |
| `Not authorized to perform sts:AssumeRoleWithWebIdentity` | El secreto llega, pero la política de confianza del rol no acepta ese `sub`: el entorno de GitHub no se llama como espera, o el rol es de otro repositorio |
| `terraform output -json roles_despliegue` no devuelve nada | El paso 2 no se ha aplicado todavía: el output existe (`despliegue.tf`), pero sin roles en el estado no tiene valor |

El orden no es decorativo. El pipeline **no puede crear sus propios roles** —su
política se lo deniega explícitamente, ver el statement
`NoTocarLaPropiaIdentidad` de `despliegue.tf`—, así que el paso 2 se aplica desde
un equipo o no se aplica.

### Otros dos que salen una sola vez, en un entorno que ya existía

**`ResourceAlreadyExistsException` en el grupo de logs de la base.**

```
creating CloudWatch Logs Log Group (/aws/rds/instance/ondexia-dev/postgresql):
ResourceAlreadyExistsException: The specified log group already exists
```

Lo creó RDS solo, al activar la exportación de logs, antes de que la V-M12
pusiera el grupo bajo Terraform para fijarle la retención. No se arregla en
código —el recurso está bien— sino trayéndolo al estado:

```bash
terraform import "-var-file=entornos/dev.tfvars" aws_cloudwatch_log_group.bd_postgresql "/aws/rds/instance/ondexia-dev/postgresql"
```

En un entorno nuevo no pasa: ahí Terraform crea el grupo antes de que RDS
tenga nada que escribir.

**`Unable to validate the following destination configurations` en el bus de
emisión.** S3 comprueba que puede invocar al Emisor en el momento de guardar la
notificación, y el permiso que se lo concede se acababa de crear. Hay
`depends_on`, pero eso ordena las llamadas, no espera a que IAM propague. Se
resuelve volviendo a aplicar. Si persiste en un segundo intento ya no es la
carrera y hay que mirar el `qualifier` del permiso contra el ARN del alias.

### Qué vas a ver, y dónde

Con `gestionar_dns = false` —lo que trae `dev.tfvars`— **no hace falta tener el
dominio**: CloudFront sirve por el suyo, algo como
`https://d111111abcdef8.cloudfront.net`. Es la forma de comprobar la cadena
entera —bucket, control de acceso de origen, distribución, caché— antes de
gastar en un dominio.

Para que responda en `ondexia.com` hay que poner `gestionar_dns = true`, y eso
crea los certificados de ACM y la zona DNS en Route 53. El registro del dominio
puede permanecer en Namecheap: allí se delegan los servidores de nombres de la
zona AWS. La zona DNS tiene costo aparte; no hay que comprar otro dominio.

> **La distribución tarda.** Entre 5 y 15 minutos la primera vez. La sonda del
> workflow reintenta diez veces con diez segundos de pausa, así que puede
> fallar en rojo con la landing perfectamente creada. Si pasa, no vuelvas a
> aplicar: espera y abre la URL.

### Lo que este atajo deja a medias

`-target` deja el estado **parcial a propósito**, y Terraform lo avisa en cada
ejecución. Es correcto aquí y conviene saber lo que significa: el resto de la
plataforma sigue sin existir, y el primer `apply` completo la creará entera de
golpe. Ese plan hay que leerlo con calma — es el que enciende RDS.

Mientras tanto, `terraform plan` sin `-target` va a mostrar siempre esos
recursos como pendientes de crear. No es deriva ni un error: es que todavía no
están.

## Qué se crea

| | |
|---|---|
| **Red** | VPC con dos subredes privadas, sin puerta de enlace a internet ni NAT. Endpoint de S3 de puerta de enlace |
| **Datos** | RDS PostgreSQL 17, `db.t4g.micro`, zona única, cifrada, sin acceso público |
| **Estático** | Tres buckets (SPA, landing, marca) con tres distribuciones de CloudFront. Ninguno público |
| **Identidad** | Dos grupos de usuarios de Cognito: inquilinos y personal |
| **API** | API Gateway HTTP con autorizador JWT nativo, y una Lambda en la VPC |
| **Control** | Presupuesto con alerta, alarmas de errores y de espacio, logs con retención |

Cuesta **~15 USD/mes**, de los que RDS es el 90 % (DTE §4.7).

## Decisiones que conviene entender antes de tocar nada

**No hay NAT, y es deliberado.** En la v1 nada necesita salir a internet. Eso
ahorra la instancia y su IP pública —unos 6.65 USD/mes— pero impone tres reglas:

1. El endpoint de S3 es **obligatorio**. Sin él la Lambda no alcanza S3 y las
   llamadas fallan por tiempo de espera, que es un síntoma confuso.
2. Las credenciales de la base van en **variables de entorno cifradas**, no en
   Secrets Manager ni Parameter Store.
3. La administración de usuarios la hace el **SPA contra Cognito**, no el
   backend.

Cualquiera de las tres resuelta por la vía «natural» exige un endpoint de
interfaz a ~7.30 USD/mes — más caro que la NAT que se evitó.

**Dos grupos de usuarios desde el primer día.** El personal de Ondexia no vive
en el mismo grupo que los clientes, aunque el back-office no exista todavía.
Migrar identidades en producción es caro (DTE §8.1).

**El certificado de CloudFront se emite en `us-east-1`** sea cual sea la región
del resto. Por eso existe el proveedor con alias `edge`, y por eso esto funciona
igual si se decide operar desde Ohio.

## El backend

`dev.tfvars` conserva la configuración histórica; el desarrollo vigente es
local. `prod.tfvars` ya apunta a los artefactos reales para promover la única
pila AWS. Leer el procedimiento de promoción antes de cualquier plan o apply.

Antes de aplicar hay que construirlo:

```bash
cd apps/backend && ./mvnw -pl ondexia.api -am package -DskipTests
```

Es un `.jar`, no un `.zip`, porque un jar ya **es** un zip y Lambda lo acepta tal
cual. Y no es el fat jar de Spring Boot: el reempaquetado está desactivado y lo
genera `maven-shade-plugin`, porque el cargador de clases de Lambda no sabe leer
los jars anidados de un fat jar.

### Tres cosas de este despliegue que no son evidentes

**El artefacto viaja por S3, no directo.** Pesa ~64 MB y el límite de la subida
directa son 50 MB. Por el camino directo, `apply` falla con
`RequestEntityTooLargeException` y no hay parámetro que lo arregle.

**El tráfico entra por un alias, nunca por `$LATEST`.** SnapStart solo actúa
sobre versiones publicadas: apuntar la integración a `$LATEST` deja la
instantánea sin usar, sin ningún aviso — solo arranques en frío lentos.

**Las migraciones no corren al arrancar la API.** Hay una función aparte que se
invoca después de cada despliegue, y hasta entonces el esquema no existe:

```bash
aws lambda invoke --function-name $(terraform output -raw funcion_migraciones) --payload '{}' salida.json
```

El porqué está en `ManejadorMigraciones`: en Lambda, «al arrancar» significa
*en cada arranque en frío*, y varios a la vez cuando llega tráfico.

## Lo que Terraform no hace por ti

- **Confirmar la suscripción de correo al tema de SNS.** Llega un mensaje de AWS
  y hay que pulsar el enlace, o las alarmas no avisan a nadie.
- **Cargar los servidores de nombres en el registrador**, si se activa
  `gestionar_dns`.
- **Pasar la cuenta al plan de pago** antes de que venza el periodo gratuito:
  el plan gratuito cierra la cuenta sola.
- **Crear los parametros de la consulta de RUC.** Cuatro, y sin ellos el `plan`
  falla antes de tocar nada: Terraform lee la clave publica con un *data source*,
  y un *data source* se resuelve al planificar. Ver mas abajo.
- **Meter a cada persona del equipo en un grupo del pool de personal**:
  `soporte` (lee) u `operaciones` (escribe). Sin grupo, la cuenta autentica y no
  puede hacer nada en el panel (hallazgo A5). La pertenencia no esta en Terraform
  a proposito: dar de baja a alguien no debe exigir un despliegue.
- **Dar de alta a los clientes.** El autoservicio del pool de inquilinos esta
  cerrado desde el hallazgo C2 (`autoservicio_inquilinos = false`): un cliente
  nuevo es un usuario creado a mano en Cognito, y desde ahi completa el registro
  en la aplicacion. Reabrirlo es una linea, y reabre C2.
- **Registrar el dominio antes de prod.** `gestionar_dns = true` es obligatorio
  en produccion y lo valida Terraform: sin dominio propio CloudFront acepta TLS
  1.0 y la CSP queda con comodines.

## Las claves de la consulta de RUC

Terraform **nombra** estos parametros y no crea sus valores. Un parametro creado
desde aqui guarda su valor en el estado, y el estado vive en S3 — que este
cifrado no cambia que la clave pasaria a estar en dos sitios en lugar de uno.

Cuatro parametros bajo `/ondexia/<entorno>/consultas/`:

| Parametro | Tipo | Que es |
|---|---|---|
| `firma-privada` | `SecureString` | Con la que `ondexia.consultas` firma las atestaciones |
| `firma-publica` | `String` | Con la que la API las verifica. No es un secreto |
| `decolecta` | `SecureString` | Token del proveedor principal |
| `apiperu` | `SecureString` | Token del relevo. Solo si `usar_apiperu = true` |

El par de firma se genera una vez **por entorno**, y que sea por entorno importa:
si dev y prod comparten par, una atestacion emitida en dev la acepta prod.

```bash
openssl genpkey -algorithm ed25519 -out firma-dev.pem
openssl pkey -in firma-dev.pem -pubout -out firma-dev.pub
```

Y se suben leyendo del archivo, sin que el valor pase por el historial de la
consola. La clave va **sin el envoltorio PEM**: solo la linea de base64, que es
lo que `Atestacion.clavePrivada` espera.

```bash
ENT=dev
aws ssm put-parameter --name "/ondexia/$ENT/consultas/firma-privada"   --type SecureString --overwrite   --value "$(sed -n '2p' firma-dev.pem)"

aws ssm put-parameter --name "/ondexia/$ENT/consultas/firma-publica"   --type String --overwrite   --value "$(sed -n '2p' firma-dev.pub)"

aws ssm put-parameter --name "/ondexia/$ENT/consultas/decolecta"   --type SecureString --overwrite --value "TOKEN_DE_DECOLECTA"
```

`SecureString` sin `--key-id` usa la llave gestionada de AWS para SSM, que es
gratis; una llave propia cuesta 1 USD/mes y aqui no aporta nada. El nivel
estandar de Parameter Store tambien es gratuito.

Cuando el par se rota, el orden es: subir la publica nueva, aplicar Terraform
—que la copia a la variable de entorno de la API—, y solo entonces la privada.
Al reves hay una ventana en la que la API rechaza lo que consultas firma.

**Cambiar un parametro no cambia la funcion que ya esta desplegada.** Los valores
se leen al arrancar, y con SnapStart el arranque ocurre UNA VEZ, al publicar la
version: la instantanea guarda la clave que habia entonces. Despues de tocar
cualquiera de los cuatro parametros hay que volver a desplegar, para que se
publique una version nueva.

Y si la clave era invalida al publicar, la version queda en estado `Failed` y el
alias apuntando a ella, con lo que TODAS las peticiones responden 500. Terraform
no lo detecta —para el la funcion existe y el alias esta donde debe—, asi que el
sintoma no aparece en ningun plan. Se ve con:

```bash
VER=$(aws lambda get-alias --function-name ondexia-dev-consultas --name activo --query FunctionVersion --output text)
aws lambda get-function-configuration --function-name ondexia-dev-consultas --qualifier "$VER" --query State
```

## Las credenciales de la emisión electrónica

No las crea Terraform ni las escribe nadie a mano: **las sube el navegador** con
URL prefirmadas desde Configuración › Emisión electrónica (doc 14 §4). Aquí solo
lo que hay que saber al operar.

El bucket es `ondexia-<entorno>-emision-<sufijo>`, cifrado, sin acceso público y
con versionado. Dentro, por RUC:

| Objeto | Qué es | Quién lo lee |
|---|---|---|
| `certificados/<ruc>.pfx` | El certificado digital del cliente | **Solo el rol del Emisor** |
| `credenciales/<ruc>.json` | `{"claveCertificado": …, "claveSol": …}` | **Solo el rol del Emisor** |

El rol de la API puede escribir esos dos prefijos y listarlos, y **no puede
leerlos**. Sin el archivo y su contraseña no se puede firmar, que es lo que
mantiene la separacion que `CLAUDE.md` exige. Se comprueba en el plan:

```bash
terraform plan | grep -A30 'aws_iam_role_policy.api_emision'
```

Ninguna declaracion sobre `certificados/*` ni `credenciales/*` debe llevar
`s3:GetObject`. Si algun dia aparece, el hallazgo A2 vuelve a estar abierto.

**El certificado lo paga el cliente** a una entidad certificadora; Ondexia no lo
revende (doc 12 §5.2). Para la beta de SUNAT sirve uno autofirmado y el usuario
SOL es `MODDATOS`.

Lo que puede fallar y como se ve: si la contrasena no corresponde al archivo, el
Emisor lo dice en la propia pantalla de configuracion —«No abre: la contrasena no
corresponde al certificado»— porque al confirmar la carga se encola una
verificacion. No hace falta emitir para descubrirlo.

## Alta del primer cliente en producción

Lo que hay que hacer, y en qué orden, para que una empresa real empiece a emitir
con valor tributario. Es la iteración 7 del [plan](../ondexia.docs/12-plan-primer-producto.md#8-iteraciones).

**Antes de nada:** este apartado describe pasos sobre una cuenta de AWS real y
sobre comprobantes que SUNAT va a recibir. Un error aquí no produce una pantalla
fea, produce un comprobante inválido con consecuencias fiscales para alguien que
confió. Nada de esto se hace con prisa.

### 0. Lo que tiene que ser cierto antes de tocar producción

| Requisito | Cómo se comprueba |
|---|---|
| El ensayo de cinco días contra el entorno de pruebas de SUNAT terminó con CDR aceptado | Está anotado con su fecha en [doc 14 §6](../ondexia.docs/14-emision-electronica.md) |
| El MFA de la cuenta raíz de AWS está activado | En la consola de IAM, «Security credentials» de la cuenta |
| El presupuesto mensual y las alarmas existen en el entorno de producción | `terraform state list \| grep -E 'budgets\|metric_alarm'` |
| El rol de despliegue de producción es distinto del de `dev` | Un rol por entorno, sin fusionar (`CLAUDE.md`, hallazgo C1) |

Mientras el primero no esté anotado, la landing dice «en ensayo» y no «listo»
—lo pone `Estado.astro`—, y eso es coherente a propósito: la página no promete
lo que no se ha comprobado.

### 1. El `apply` de producción, con el plan leído

Nunca `apply` a secas. La regla está en `CLAUDE.md` y aquí es donde más pesa:

```bash
cd ondexia.infra
terraform workspace select prod   # o el mecanismo de entorno que se use
terraform plan -var-file=entornos/prod.tfvars -out=prod.tfplan
```

Se **lee el plan entero**, y en particular:

- que no haya `destroy` ni `replace` sobre `aws_db_instance.principal`;
- que `aws_iam_role_policy.api_emision` siga sin `s3:GetObject` sobre
  `certificados/*` ni `credenciales/*` (es la frontera del hallazgo A2);
- que todo `aws_iam_role` lleve su `permissions_boundary`, porque sin ella el
  apply muere con `AccessDenied` y a medio camino.

Solo entonces:

```bash
terraform apply prod.tfplan
```

### 2. La empresa, desde la aplicación

Ni el certificado ni la clave SOL ni el modo de SUNAT los toca Terraform. Se
configuran desde **Configuración › Emisión electrónica** con la empresa activa,
y el orden importa:

1. **Subir el certificado y las credenciales.** El navegador los sube directo al
   bucket con URL prefirmadas; el `.pfx` y su contraseña no pasan por la API
   (doc 14 §4). El certificado lo paga el cliente a una entidad certificadora:
   Ondexia no lo revende.
2. **Esperar la verificación.** Al confirmar la carga se encola una orden que
   abre el archivo. La misma pantalla dice si abrió, con qué sujeto y cuándo
   vence, o por qué no abrió. Si la contraseña no corresponde, se ve aquí y no
   al emitir.
3. **Pasar a producción.** El botón solo funciona con un certificado verificado
   y vigente; el servidor lo vuelve a comprobar. Desde ese momento **lo que se
   emita tiene valor tributario**.
4. **Revisar las series.** Una serie de producción no se comparte con las de
   prueba: conviene crear las suyas, con la letra que le toca a cada tipo (F
   factura, B boleta, N nota de venta).

### 3. La comprobación, con un comprobante de verdad

Una boleta pequeña, a nombre del propio cliente, emitida desde el punto de
venta. Se da por buena cuando en la ficha del documento aparece:

- el estado **Aceptado por SUNAT**;
- el XML firmado y el CDR descargables;
- el resumen de la firma, que es lo que va impreso y en el código QR.

Si sale rechazada, el código y la descripción de SUNAT están en la misma
pantalla. Un rechazo por un dato del comprobante —un RUC que no está activo, una
serie que no corresponde— se corrige y se vuelve a emitir; no es motivo para
volver a la beta.

### 4. Volver atrás

Se puede: el mismo botón devuelve la empresa a la beta. Lo que **no** se
deshace es lo ya emitido en producción, que existe ante SUNAT y solo se corrige
con una nota de crédito o una comunicación de baja (doc 13 §5 y §6). Por eso el
paso 3 se hace con un comprobante pequeño y del propio cliente.

## Deuda conocida

**La contraseña de la base queda en el estado de Terraform.** Es inherente a
generarla con Terraform, y por eso el bucket del estado está cifrado, versionado
y con el acceso público bloqueado. La solución definitiva es **autenticación por
IAM contra RDS**, que elimina la contraseña: el token se firma localmente con
las credenciales de la Lambda, sin llamada de red y sin secreto almacenado.
Cambiarlo después es contenido, no estructura.

**Sin análisis estático de seguridad en el pipeline.** El DTE §8.3 menciona
`cdk-nag`, que no aplica a Terraform. El equivalente es `tfsec` o `checkov`, y
hay que incorporarlo al CI. Hoy el CI solo comprueba `fmt`, `init` y `validate`:
sintaxis y coherencia de referencias, no configuraciones inseguras.

**El CI valida pero no planifica.** `terraform validate` no comprueba que AWS
acepte cada combinación de argumentos, ni que las cuotas den. Un `plan` en CI
exigiría credenciales en cada push, y con OIDC eso significa un rol de solo
lectura adicional. Mientras tanto, la casilla «solo plan» del workflow de
despliegue cubre el caso a mano.

## Estado de verificación

`terraform fmt`, `terraform init` y `terraform validate` pasan sobre esta
configuración y sobre `bootstrap/`.

**No se ha ejecutado `plan` ni `apply` contra una cuenta real.** `validate`
comprueba sintaxis y coherencia de referencias; no comprueba que AWS acepte cada
combinación de argumentos, ni que las cuotas den, ni que los identificadores de
políticas gestionadas de CloudFront sigan vigentes. El primer `plan` es parte
del trabajo, no un trámite.

## Promoción del único entorno AWS a producción

Decisión del propietario: desarrollo local, una única pila productiva AWS. La
[especificación](../ondexia.docs/especificaciones/2026-10-12-promocion-productiva.md)
y las pruebas `tests/promocion.tftest.hcl` acompañan esta preparación. No hay
migración, DNS ni despliegue AWS ejecutados como parte del cambio de código.

### Identidad conservada y políticas productivas

`prod.tfvars` fija `entorno = prod` y `entorno_recursos = dev`: RDS, buckets,
Lambdas, pools y rutas SSM conservan sus nombres. Las etiquetas, protecciones,
perfiles, respaldos, retención y autenticación usan prod. No renombrar objetos
para mejorar su presentación: un nombre heredado no define su seguridad.

El workflow solo permite prod; aplicar exige main y el entorno GitHub prod.
`solo_plan` permite revisar sin aplicar. Los roles siguen separados, con lectura
antes de escritura. Al aplicar prod se deniega la confianza OIDC de los roles
dev heredados sin borrarlos. La protección efectiva de environments/roles debe
comprobarse: un control de workflow no protege contra otros workflows o sesiones.

### Antes de planificar con el estado prod

1. Un operador con acceso autorizado inventaría el estado `ondexia/dev/terraform.tfstate`,
   RDS, pools, distribuciones, buckets, secretos y datos demo. Preservar los datos;
   no ejecutar limpieza ni sembrado durante la promoción.
2. Suspender todos los despliegues y desactivar la entrada OIDC dev. Esperar a
   que caduquen sus sesiones ya emitidas; una nueva política de confianza no las
   revoca. Verificar permisos mínimos del operador y entornos prod/prod-plan.
3. Guardar respaldos cifrados de estado y datos fuera del repositorio. El estado
   contiene secretos; no imprimirlo, adjuntarlo al PR ni enviarlo por chat.
4. Verificar que el destino prod no gestiona otra pila. Con los despliegues
   detenidos y autorización sobre ambos prefijos, usar la migración de backend
   de Terraform hacia `ondexia/prod/terraform.tfstate` (`init -migrate-state`),
   revisando el aviso interactivo. Nunca usar `-force-copy` ni sobrescribir otro
   estado. La operación requiere una identidad de mantenimiento, no ampliar el
   rol prod para leer libremente dev.
5. Verificar recursos, lineage y serial en destino. Archivar y retirar el objeto
   antiguo como estado activo antes de reabrir despliegues. Conservar el respaldo
   y versiones para recuperación; dos estados activos no deben poseer la pila.
   No ejecutar `destroy` sobre el estado antiguo ni eliminar los recursos.
6. El checker `comprobar_promocion.py estado` valida anclas sintéticas conocidas;
   no acredita titularidad, cuenta AWS, igualdad completa de estados ni backup.
   El operador contrasta todos los identificadores. El workflow falla cerrado
   si falta el estado o no coincide la pila esperada; no quitar esa puerta.

Este documento no ejecuta ni automatiza la migración de titularidad. El plan AWS
real y su resultado quedan pendientes hasta disponer de acceso.

Después de migrar, un operador prepara con plan revisado las identidades OIDC,
frontera y roles del pipeline usando la configuración prod, incluidas sus
etiquetas. El pipeline no puede cambiar su propia identidad. Su checker rechaza
esas modificaciones si siguen pendientes; no ampliar permisos para evitarlas.
Confirmar retiro de confianza dev y expiración de sesiones antes de delegar la
aplicación al workflow. Los recursos productivos conservan sus roles propios.

### DNS y certificados por etapas

No crear otra zona manualmente si ya existe una para este dominio: verificar
estado y AWS primero; importar una zona existente exige revisar su titularidad.
Con el estado promovido y prod.tfvars, preparar un plan excepcional acotado a
`aws_route53_zone.principal`, `aws_acm_certificate.principal`,
`aws_acm_certificate.api`, `aws_route53_record.validacion` y
`aws_route53_record.validacion_api`. Revisar todos los recursos arrastrados y
pasar el checker con `--parcial`; aplicar solo el archivo guardado aprobado.
Esta etapa no incluye `aws_acm_certificate_validation`, que espera delegación.

Leer los cuatro servidores de la zona y cargarlos en Namecheap → Domain →
Nameservers → Custom DNS. Si hay correo u otros registros existentes, trasladarlos
antes de delegar; Advanced DNS de Namecheap dejará de ser la fuente autoritativa.
Comprobar resolución y validación ACM. Después generar el plan completo, sin
`-target`, y revisarlo antes de continuar. El workflow bloquea solo_landing
porque la promoción no puede omitir los controles del resto de la pila.

El certificado principal está en us-east-1; la API usa su región. Se configuran
raíz y www en landing, app, panel, cdn y api. Se mantienen los CNAME de validación
para renovación. No configurar DNS antes de preparar aliases y certificados.

### Comprobaciones pendientes antes de clientes

- Cuota Lambda real: prod propone reserva API 20; no quitar el límite para salvar
  un apply. Solicitar cuota suficiente para las reservas y el fondo compartido.
- NVD_API_KEY efectiva y análisis de dependencias; la puerta productiva bloquea
  ausencia. No desactivar análisis ni aceptar riesgos pendientes sin evaluación.
- RDS privada, respaldo de siete días, protección contra borrado y snapshot
  final. Confirmar `PendingModifiedValues`: los cambios pueden esperar mantenimiento.
- MFA personal, callbacks de Cognito, CORS, CSP, HTTPS y configuración generada
  desde outputs; no subir config.json con URLs fijas.
- Originales y versiones históricas en S3, permisos, recuperación y usuarios demo.
- La rotación de la contraseña maestra y su uso por migraciones requiere revisión
  adicional; no está resuelta por activar prod ni por IAM en la API.
- SUNAT se habilita por empresa con certificado verificado y series aprobadas;
  no cambia automáticamente al promover infraestructura.
- Sondas funcionales y de aislamiento y ensayo de restauración; CI verde y
  planes con proveedores simulados no acreditan esos controles en AWS.

Conservar una RDS micro Single-AZ evita duplicar su costo fijo. Respaldos,
retención y DNS pueden aumentar cargos por almacenamiento/uso. No se promete
alta disponibilidad ni un costo final sin contrastar la cuenta.
