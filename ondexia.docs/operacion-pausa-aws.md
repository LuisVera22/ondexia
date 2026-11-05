# Pausa operativa de Ondexia en AWS

Estado: **en pausa desde el 2026-11-05**. Decisión del propietario: el proyecto
todavía no genera ingresos y RDS es casi todo el gasto. Se conserva PostgreSQL
y todo lo demás; no hay migración de motor ni borrado de nada.

Este documento es el registro de lo que se cambió **fuera de Terraform**. Cada
cambio de aquí es deriva respecto del estado `ondexia/prod/terraform.tfstate`, y
un `apply` sin revertirlos antes deshace la pausa a medias: devuelve la
concurrencia de las Lambdas, pero con la base detenida. Por eso lo primero que
se hizo fue deshabilitar el despliegue.

## 1. Alcance

Cuenta `370930247103`, región `us-east-1`. Ondexia se identifica por
`proyecto=ondexia` y `entorno=prod`; los nombres `ondexia-dev-*` son heredados
de la promoción (ver `ondexia.infra/README.md`, «Identidad conservada»).

En la misma cuenta vive **otro proyecto**, AtajoIA (`sst:app=atajoia`): dos
distribuciones de CloudFront, los buckets `adminbucket-*` y `webbucket-*`, una
zona de Route 53 y la función `WebViewerRequest-*`. **No se tocó** y no forma
parte de nada de lo que sigue, ni del costo atribuido a Ondexia.

## 2. Estado previo a la pausa

| Comprobación | Resultado |
|---|---|
| Último despliegue (`deploy.yml`) | 2026-10-16, en verde |
| Invocaciones de la API | Ninguna desde el 2026-10-16 |
| Conexiones a RDS | 0 en los siete días anteriores |
| Bus de emisión `ondexia-dev-emision-*` | **Vacío**, incluidas versiones: ninguna orden pendiente, resultado, certificado ni documento. En AWS nunca se emitió ante SUNAT |
| Colas, programaciones, reglas de EventBridge, *event source mappings* | Ninguno. El único disparador asíncrono es la notificación S3 `pendientes/*.json` → Emisor |
| Usuarios de Cognito | 3 en inquilinos, 1 en personal; sin disparadores Lambda |
| `api.ondexia.com/salud` | Ya respondía 500: la Lambda estaba `Inactive` por inactividad en la VPC y la primera petición expira mientras se reactiva |

Al no haber operaciones en curso, no hubo nada que drenar. La sección 6 dice qué
hacer si al reanudar apareciera alguna orden.

## 3. Lo que se cambió, y cómo deshacerlo

| # | Recurso | Antes | Durante la pausa | ¿En Terraform? |
|---|---|---|---|---|
| 1 | Workflow `Deploy` en GitHub | activo | `disabled_manually` | No: GitHub |
| 2 | Concurrencia reservada de `ondexia-dev-api` | 20 | 0 | Sí → deriva |
| 3 | Concurrencia reservada de `-consultas`, `-facturacion`, `-migraciones`, `-panel` | sin reserva | 0 | Sí → deriva |
| 4 | RDS `ondexia-dev` | `available` | `stopped`, etiqueta `pausa=2026-11-05` | Estado no; etiqueta sí → deriva |
| 5 | Instantánea manual `ondexia-dev-pausa-20261105` | — | creada al detener | No: se conserva |
| 6 | Grupo de Scheduler `ondexia-pausa` y programación `mantener-rds-detenida` | — | `rate(1 hour)` → `rds:StopDBInstance` sobre `ondexia-dev` | No |
| 7 | Rol IAM `ondexia-pausa-detener-rds` | — | solo `rds:StopDBInstance` sobre `ondexia-dev`, con la frontera `ondexia-frontera-despliegue` | No |
| 8 | CloudFront Function `ondexia-pausa-mantenimiento` | — | asociada en *viewer-request* a app (`E22DQ5CGOJPI7E`) y panel (`E3LC7MDPU6JNS1`) | Asociación sí → deriva |

**Concurrencia 0 es el interruptor.** Con ella ninguna Lambda se ejecuta, la
invoque quien la invoque: API Gateway responde 503, la notificación de S3 no
puede disparar al Emisor y las migraciones no corren contra una base detenida.
No borra versiones, alias, variables ni la instantánea de SnapStart.

**La página de mantenimiento no toca los buckets.** La función responde en el
borde con un 503 y `Retry-After`; los archivos de la SPA y del panel siguen
intactos en S3. No tiene formularios ni botones: solo el correo de contacto y
un enlace a la landing.

**La landing sigue publicada.** Cuesta prácticamente nada (capa gratuita de
CloudFront) y no tiene nada que aparente funcionar sin backend: «Solicitar
acceso» es un `mailto:` y los enlaces «Ingresar» llevan a la página de
mantenimiento.

## 4. Por qué hace falta la programación, y sus límites

AWS **vuelve a arrancar sola** una RDS detenida a los siete días. La
programación `mantener-rds-detenida` llama cada hora a `StopDBInstance`:

- Mientras la base está detenida, la llamada falla con
  `InvalidDBInstanceState` y no hace nada. Es lo esperado.
- Si AWS la arranca, la siguiente ejecución la detiene. Ventana máxima
  encendida: **alrededor de una hora por cada siete días**, más lo que tarde en
  pasar de `starting` a `available` (mientras arranca, la llamada también
  falla y se reintenta a la hora siguiente).
- Al volver a detenerla, AWS reinicia su cuenta de siete días.

Límites que conviene conocer:

- **No evita los arranques, solo los acorta.** No existe una opción de AWS para
  mantener una RDS detenida indefinidamente.
- Si alguien arranca la base a mano mientras la programación está activa
  —para consultar algo o sacar un volcado—, **se detendrá en menos de una hora**.
  Para eso, desactivar antes la programación (sección 5, paso 1) y volver a
  activarla después.
- El rol solo puede detener `ondexia-dev`; no puede arrancarla, borrarla ni
  tocar otra base. Se comprobó con el simulador de IAM y con una ejecución
  real (sección 7).
- Durante la ventana encendida, las Lambdas siguen en concurrencia 0: nadie se
  conecta a la base aunque esté arriba.

## 5. Reanudar

En este orden. El orden importa: si se aplica Terraform con la base detenida,
las migraciones fallan; si se arranca la base con la programación activa, se
vuelve a detener.

1. **Desactivar la programación** (primero, siempre):

   ```bash
   aws scheduler update-schedule --name mantener-rds-detenida --group-name ondexia-pausa \
     --schedule-expression "rate(1 hour)" --flexible-time-window Mode=OFF --state DISABLED \
     --target '{"Arn":"arn:aws:scheduler:::aws-sdk:rds:stopDBInstance","RoleArn":"arn:aws:iam::370930247103:role/ondexia-pausa-detener-rds","Input":"{\"DbInstanceIdentifier\":\"ondexia-dev\"}"}'
   ```

2. **Arrancar RDS** y esperar a `available` (unos minutos):

   ```bash
   aws rds start-db-instance --db-instance-identifier ondexia-dev
   aws rds wait db-instance-available --db-instance-identifier ondexia-dev
   aws rds remove-tags-from-resource --resource-name arn:aws:rds:us-east-1:370930247103:db:ondexia-dev --tag-keys pausa
   ```

3. **Devolver la concurrencia** a lo que tenía:

   ```bash
   aws lambda put-function-concurrency --function-name ondexia-dev-api --reserved-concurrent-executions 20
   for f in consultas facturacion migraciones panel; do aws lambda delete-function-concurrency --function-name ondexia-dev-$f; done
   ```

4. **Quitar la página de mantenimiento** de app y panel. Se lee la
   configuración vigente con su `ETag`, se deja
   `DefaultCacheBehavior.FunctionAssociations` en `{"Quantity": 0}` y se envía
   con `aws cloudfront update-distribution --id <id> --if-match <ETag>
   --distribution-config file://...`. Antes de la pausa ninguna de las dos
   tenía funciones asociadas. Esperar a `Deployed`.

5. **Comprobar** que `https://api.ondexia.com/salud` responde 200 (la primera
   petición puede fallar mientras la Lambda sale de `Inactive`; repetir) y que
   app y panel cargan la SPA.

6. **Rehabilitar el despliegue** y lanzar `deploy.yml` con «Solo mostrar el
   plan». El plan debe salir **sin cambios** respecto de los recursos de la
   tabla de la sección 3; si muestra concurrencia, etiqueta o función, algún
   paso anterior quedó a medias.

   ```bash
   gh workflow enable deploy.yml -R LuisVera22/ondexia
   ```

7. **Retirar lo que creó la pausa**, cuando todo lo anterior esté verificado:

   ```bash
   aws scheduler delete-schedule --name mantener-rds-detenida --group-name ondexia-pausa
   aws scheduler delete-schedule-group --name ondexia-pausa
   aws iam delete-role-policy --role-name ondexia-pausa-detener-rds --policy-name detener-rds-ondexia
   aws iam delete-role --role-name ondexia-pausa-detener-rds
   E=$(aws cloudfront describe-function --name ondexia-pausa-mantenimiento --query ETag --output text)
   aws cloudfront delete-function --name ondexia-pausa-mantenimiento --if-match "$E"
   ```

   La instantánea `ondexia-dev-pausa-20261105` **se conserva**: borrarla es una
   decisión aparte, con su propio permiso.

## 6. Operaciones pendientes al reanudar

Al pausar no había ninguna (sección 2). Si al reanudar hubiera objetos en
`pendientes/` —por ejemplo, una orden escrita justo antes de pausar en una
futura pausa—, la notificación de S3 que no pudo invocar al Emisor **ya se
perdió**: Lambda descarta los eventos asíncronos tras seis horas de
reintentos. El objeto, en cambio, sigue ahí, porque el Emisor solo lo borra
después de procesarlo. Para volver a disparar cada orden, sin cambiar su
contenido:

```bash
B=ondexia-dev-emision-370930247103
aws s3api list-objects-v2 --bucket $B --prefix pendientes/ --query "Contents[].Key" --output text
aws s3api copy-object --bucket $B --key <clave> --copy-source "$B/<clave>" --metadata-directive REPLACE
```

La copia sobre sí misma genera un `ObjectCreated` nuevo. La deduplicación del
Emisor (doc 14, reintentos) es lo que hace seguro reenviar una orden que SUNAT
pudiera haber recibido ya; no saltarse esa comprobación.

## 7. Verificación de la pausa

Hecha el 2026-11-05, después de aplicar los cambios:

| Qué | Cómo | Resultado |
|---|---|---|
| RDS | `describe-db-instances` | `stopped` |
| Instantánea de la pausa | `describe-db-snapshots` | manual, cifrada |
| Respaldos automáticos | `describe-db-instance-automated-backups` | 8 instantáneas diarias del 2026-10-29 al 2026-11-05 y la manual del 2026-10-12, todas `available` |
| Lambdas | `get-function-concurrency` | las cinco en 0 |
| Rol de la pausa | `simulate-principal-policy` | `StopDBInstance` sobre `ondexia-dev`: permitido; sobre otra base, `StartDBInstance` y `DeleteDBInstance`: denegados |
| Programación | CloudTrail, evento `StopDBInstance` | 2026-11-05T16:58:50Z, `assumed-role/ondexia-pausa-detener-rds`, `InvalidDBInstanceStateFault` (la base ya se estaba deteniendo) |
| Landing | `curl` | 200 en `ondexia.com` y `www` |
| App y panel | `curl` y navegador a 375 px | 503 con la página de mantenimiento; sin formularios ni botones; sin desbordamiento; foco visible |
| API y API del panel | `curl /salud` | 503 |
| Despliegue | `gh workflow list` | `Deploy` en `disabled_manually` |
| Estado de Terraform | `s3api list-objects-v2` y versionado | `ondexia/prod/terraform.tfstate` intacto (último cambio 2026-10-16), bucket versionado |

**No verificado:** que la programación detenga de verdad una base que AWS haya
arrancado. Probarlo exige arrancarla; se comprobó en su lugar que Scheduler
asume el rol y que RDS recibe y autoriza la llamada (CloudTrail registra
`InvalidDBInstanceState` y no `AccessDenied`). Conviene mirar el 2026-11-12,
siete días después de detenerla, que la base vuelve a `stopped`.

## 8. Costo

Cifras de Cost Explorer con registros de tipo `Usage`, es decir, antes de
aplicar créditos. La cuenta tiene créditos (US$138.57 al 2026-11-05) que hoy
cubren todo; las cifras son lo que costaría sin ellos.

**Octubre de 2026, cuenta entera:** US$15.61. De eso, RDS US$14.38: instancia
11.90, almacenamiento gp3 de 20 GB 2.30, respaldos 0.18.

| Concepto | Antes | En pausa | Por qué sigue |
|---|---|---|---|
| Horas de instancia RDS | ~11.90 | ~0.07 | La ventana de hasta una hora cada siete días |
| Almacenamiento RDS (20 GB gp3) | 2.30 | 2.30 | Una instancia detenida cobra su disco |
| Respaldos RDS | ~0.18 | ~0.2–0.5 | Dos instantáneas manuales y las automáticas que quedan |
| Zona de Route 53 `ondexia.com` | 0.50 | 0.50 | Cobro fijo por zona; sin ella no resuelve la landing |
| Consultas DNS, S3, CloudTrail, CloudWatch, API Gateway | ~0.2 | < 0.1 | Por uso; casi sin tráfico |
| CloudFront, función de mantenimiento, Scheduler, Cognito, Lambda | 0 | 0 | Dentro de la capa gratuita |
| **Total estimado de Ondexia** | **~15.1** | **~3.1–3.5** | |

Supuestos: precios públicos de `us-east-1`; tráfico de la landing dentro de la
capa gratuita de CloudFront; menos de 10 alarmas; menos de 50 000 usuarios
activos en Cognito. La segunda zona DNS y el resto de AtajoIA no se cuentan.

**Reducciones adicionales que no se hicieron**, porque exigen borrar:

- Borrar la instancia tras una instantánea final ahorraría unos US$0.40/mes:
  se dejan de pagar los 2.30 del disco, pero la instantánea de 20 GB pasa a
  cobrarse entera, sin la franja gratuita. Al reanudar habría que restaurar con
  otro *endpoint* y reconciliar Terraform. No compensa.
- Borrar la zona DNS ahorraría 0.50 y dejaría sin resolver la landing y el
  correo que dependa de ella.
