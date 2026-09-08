# Ondexia — reglas invariantes

Facturación electrónica para Perú. Lo que se emite desde aquí tiene valor
tributario ante SUNAT: un defecto no produce una pantalla fea, produce un
comprobante inválido con consecuencias fiscales para un cliente que confió.

Este archivo existe porque la auditoría del 2026-09-01 encontró que **las cuatro
fallas más graves tenían una frase de origen en la documentación**, y ninguna se
habría evitado con una regla de código. Las convenciones vivían en prosa repartida
por `ondexia.docs/` y en comentarios largos, y cada sesión las releía como
especificación. Lo de abajo es lo que no se renegocia.

Lo demás —el porqué de cada decisión— está en `ondexia.docs/`, y los comentarios
del código explican lo que no se puede deducir leyéndolo. Sigue esa costumbre: se
comenta el motivo, no el mecanismo.

## Idioma

Todo en español: código, identificadores, comentarios, mensajes de error,
commits, documentación. Sin excepciones y sin mezclar.

Los commits no llevan marca de herramienta: nada de `Co-Authored-By` ni
«Generated with».

## Las cuatro reglas que salieron de la auditoría

Cada una tiene detrás un hallazgo concreto. No son principios generales.

### 1. Un comentario que describe un control de seguridad enlaza al test que lo ejercita

Sin test, el comentario dice «no verificado».

`application-aws.yml` decía «lo que sí se revalida es la firma y el emisor». La
firma **no** se verificaba: el comentario quedó desactualizado cuando cambió el
bean, y ningún test tocaba ese decodificador. Un control descrito en prosa y no
ejercitado deja de existir en cuanto alguien toca el código, sin que nada se
ponga rojo. (Hallazgo A1.)

### 2. Toda premisa de seguridad externa lleva, en el mismo commit, el test que la verifica

El panel no verificaba firmas apoyándose en esto: «el permiso de invocación está
atado al ARN de esa API: no hay otra puerta». Era falso cuando se escribió — la
ruta `OPTIONS /{proxy+}` llegaba a la misma función sin autorizador.

Y la prueba que acompañaba la decisión, `unaFirmaQueNadieVerificaPasa`, fijaba la
**excepción** en vez de la premisa: dejaba constancia verde de que una firma
falsa pasaba. Si la premisa no se puede comprobar desde donde estás, no la uses
como base: verifica tú lo que sí depende de ti. (Hallazgo A2.)

### 3. Lo que se compra también se configura

Por cada control que se delegue en un proveedor, nombra la línea de configuración
que lo activa y el ítem que lo verifica.

El DTE listaba «rotación de refresh tokens» entre lo que se compra con Cognito.
Es una opción, y estaba apagada: treinta días de vigencia, sin rotación y sin
revocación al cerrar sesión, mientras dos comentarios afirmaban que Cognito
rotaba. (Hallazgo C3.)

### 4. Una obligación previa al despliegue se codifica, o se comprueba con `precondition`

No se deja como recordatorio.

«Requisito previo, no negociable: el MFA pasa a ON antes de que el panel exista»
seguía sin cumplirse cuatro meses después, con el recordatorio repetido en dos
sitios. Si de verdad no se puede codificar —el rol de PostgreSQL de las
migraciones es el caso—, el documento explica **por qué** no se puede, no solo
qué hay que acordarse de hacer. (Hallazgo A4.)

Corolario que también salió de la auditoría: **una frontera de confianza no se
colapsa por ahorro operativo.** Un rol de despliegue por entorno; el
mantenimiento se automatiza con `for_each`, no se evita fusionando. (Hallazgo C1.)

## Reglas del dominio

**El token porta identidad y nada más.** Ni permisos, ni empresa, ni rol, ni
plan. Todo eso se resuelve en la base en cada petición: `permisos_version`
invalida la caché de forma atómica, y así revocar un permiso surte efecto en la
petición siguiente en vez de cuando caduque un token.

**Importes en `NUMERIC(18,6)`.** Nunca `double` ni `float`, en ninguna capa. Seis
decimales porque los precios unitarios de SUNAT los admiten.

**Nada de firma XML en `ondexia.api`.** La firma de comprobantes es de su propio
desplegable. Un módulo que puede firmar es un módulo cuyo compromiso emite
documentos con valor tributario.

**El aislamiento entre clientes lo pone la base, no el código.** Row Level
Security forzado, con `empresa_actual()` sobre un `set_config` local a la
transacción que falla cerrado. La aplicación NUNCA se conecta como superusuario:
un superusuario no está sujeto a RLS y el aislamiento quedaría activo y sin
efecto.

**Las bitácoras son de solo inserción**, por disparador y no solo por permisos.
Los privilegios los concede quien es dueño de la tabla y se pueden volver a
conceder.

## Al escribir SQL de migración

`GRANT` es **aditivo**. Un `GRANT SELECT` después de un `ALTER DEFAULT
PRIVILEGES` no restringe nada: hace falta `REVOKE`. Eso dejó a la API de clientes
con CRUD sobre los planes y sobre la bitácora del panel mientras el comentario
decía lo contrario (hallazgo M2), y lo fija ahora `GrantsDeLaAplicacionIT`.

Una tabla nueva hereda CRUD por los privilegios por omisión. Esa prueba se pondrá
roja: es a propósito, para que alguien decida si era lo que se quería.

## Al tocar infraestructura

- Todo `aws_iam_role` declara `permissions_boundary`. `iam:CreateRole` está
  condicionado a ella, así que sin la línea el apply falla con `AccessDenied`.
- `iam:PassRole` va condicionado al servicio que lo recibe; `iam:AttachRolePolicy`,
  a una lista cerrada de políticas.
- Ninguna ruta de API Gateway sin autorizador apunta a una Lambda. El preflight
  lo responde la pasarela desde su `cors_configuration`, no una ruta `OPTIONS`
  hacia la integración.
- Los valores de los parámetros de SSM **no los crea Terraform**: el estado vive
  en S3.

## Lo que no se hace

- No se ejecuta `terraform apply` sin haber leído el plan.
- No se sube `config.json` con URLs de un entorno: lo escribe el despliegue desde
  `terraform output`.
- No se registra el cuerpo de una respuesta de error de un proveedor externo: un
  401 suele repetir la clave enviada, y CloudWatch conserva los registros.
- No se pone `X-Forwarded-For` en una bitácora. Su primer elemento lo escribe el
  cliente (hallazgo M3).

## Dónde mirar

| Qué | Dónde |
|---|---|
| Decisiones técnicas y su porqué | `ondexia.docs/DTE-ONX-001_sistema_gestion_comercial.md` |
| Qué se construye primero y en qué orden | `ondexia.docs/12-plan-primer-producto.md` |
| Estructura del repositorio y CI/CD | `ondexia.docs/03-estructura-repositorio.md` |
| Arquitectura del backend | `ondexia.docs/08-arquitectura-backend.md` |
| Panel interno | `ondexia.docs/09-panel-administrativo.md` |
| Caja, nota de venta y existencias | `ondexia.docs/13-ventas-en-punto-de-venta.md` |
| Emisión electrónica ante SUNAT | `ondexia.docs/14-emision-electronica.md` |
| Infraestructura, y lo que hay que hacer a mano | `ondexia.infra/README.md` |
| Auditorías y sus skills | `../ondexia.auditoria/` (fuera del repo) |
