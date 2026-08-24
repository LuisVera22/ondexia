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
terraform apply
```

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

**2. La identidad de GitHub, desde tu equipo.** Es el paso que rompe el círculo:

```bash
terraform apply -var-file=entornos/dev.tfvars -target=aws_iam_role.despliegue -target=aws_iam_role_policy.despliegue_iam -target=aws_iam_role_policy_attachment.despliegue_poweruser
```

**3. Guardar el ARN como secreto.** `terraform output -raw rol_despliegue`, y
ese valor va a `AWS_DEPLOY_ROLE_ARN` en los secretos del repositorio.

**4. Crear los entornos `dev` y `prod` en GitHub.** Sin el entorno, el `sub` del
token OIDC no tiene la forma que espera la política de confianza y AWS responde
`AccessDenied` — ver el comentario largo de `despliegue.tf`. A `prod`, además,
ponerle revisor requerido (DTE §10.2).

**5. Lanzar `deploy.yml`** con entorno `dev`, «Solo la landing» marcado y «Solo
mostrar el plan» **también marcado**. Lee el plan en el resumen de la ejecución:
deberían salir cuatro recursos y ninguno de ellos una base de datos.

**6. Repetir sin «solo plan».** La URL sale en el resumen, en la fila `Landing`.

### Qué vas a ver, y dónde

Con `gestionar_dns = false` —lo que trae `dev.tfvars`— **no hace falta tener el
dominio**: CloudFront sirve por el suyo, algo como
`https://d111111abcdef8.cloudfront.net`. Es la forma de comprobar la cadena
entera —bucket, control de acceso de origen, distribución, caché— antes de
gastar en un dominio.

Para que responda en `ondexia.com` hay que poner `gestionar_dns = true`, y eso
arrastra el dominio registrado en Route 53, el certificado de ACM y la zona
alojada (doc 01 §8). Es un paso aparte y con su propio coste.

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

`dev.tfvars` ya apunta al artefacto real. `prod.tfvars` sigue con la función de
relleno que responde `501`, a la espera de que dev demuestre que el despliegue
funciona.

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
- **Poner el MFA del grupo de personal en `ON`** cuando el primer usuario tenga
  su TOTP configurado. Se deja opcional para no quedarse fuera al primer acceso.
- **Cargar los servidores de nombres en el registrador**, si se activa
  `gestionar_dns`.
- **Pasar la cuenta al plan de pago** antes de que venza el periodo gratuito:
  el plan gratuito cierra la cuenta sola.
- **Crear los parametros de la consulta de RUC.** Cuatro, y sin ellos el `plan`
  falla antes de tocar nada: Terraform lee la clave publica con un *data source*,
  y un *data source* se resuelve al planificar. Ver mas abajo.

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
