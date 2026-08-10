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
terraform init -backend-config="bucket=ondexia-tfstate-TU_ID_DE_CUENTA" -backend-config="region=us-east-1"
```

Y desplegar:

```bash
terraform apply -var-file=entornos/prod.tfvars
```

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

## Antes del backend

`artefacto_api` está vacío, así que se despliega una función de relleno que
responde `501`. No es adorno: permite verificar dominio, autorizador, red y
permisos **antes** de escribir la primera línea del backend, que es cuando
salen baratos los errores de infraestructura.

Cuando exista `ondexia.api`, en `prod.tfvars`:

```hcl
artefacto_api = "../apps/ondexia.api/target/ondexia-api.zip"
runtime_api   = "java21"
handler_api   = "com.ondexia.api.Manejador::handleRequest"
```

## Lo que Terraform no hace por ti

- **Confirmar la suscripción de correo al tema de SNS.** Llega un mensaje de AWS
  y hay que pulsar el enlace, o las alarmas no avisan a nadie.
- **Poner el MFA del grupo de personal en `ON`** cuando el primer usuario tenga
  su TOTP configurado. Se deja opcional para no quedarse fuera al primer acceso.
- **Cargar los servidores de nombres en el registrador**, si se activa
  `gestionar_dns`.
- **Pasar la cuenta al plan de pago** antes de que venza el periodo gratuito:
  el plan gratuito cierra la cuenta sola.

## Deuda conocida

**La contraseña de la base queda en el estado de Terraform.** Es inherente a
generarla con Terraform, y por eso el bucket del estado está cifrado, versionado
y con el acceso público bloqueado. La solución definitiva es **autenticación por
IAM contra RDS**, que elimina la contraseña: el token se firma localmente con
las credenciales de la Lambda, sin llamada de red y sin secreto almacenado.
Cambiarlo después es contenido, no estructura.

**Sin análisis estático de seguridad en el pipeline.** El DTE §8.3 menciona
`cdk-nag`, que no aplica a Terraform. El equivalente es `tfsec` o `checkov`, y
hay que incorporarlo al CI.

**El CI todavía no despliega esto.** El workflow de `deploy` sigue preparado
para otra cosa y hay que reescribirlo, junto con el cambio a pnpm que quedó a
medias.

## Estado de verificación

`terraform fmt`, `terraform init` y `terraform validate` pasan sobre esta
configuración y sobre `bootstrap/`.

**No se ha ejecutado `plan` ni `apply` contra una cuenta real.** `validate`
comprueba sintaxis y coherencia de referencias; no comprueba que AWS acepte cada
combinación de argumentos, ni que las cuotas den, ni que los identificadores de
políticas gestionadas de CloudFront sigan vigentes. El primer `plan` es parte
del trabajo, no un trámite.
