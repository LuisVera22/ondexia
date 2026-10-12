# Ondexia

Sistema de gestión comercial con facturación electrónica ante SUNAT, para
empresas peruanas. Punto de venta, almacén, ventas y configuración, con emisión
de boletas y facturas electrónicas.

Lo que se emite desde aquí tiene valor tributario: un defecto no produce una
pantalla fea, produce un comprobante inválido con consecuencias fiscales para un
cliente que confió. Esa frase gobierna el resto del repositorio.

Monorepo. Lo desarrolla **una sola persona asistida por IA** (R-13), y esa
restricción explica buena parte de las decisiones: se prefiere lo que falla
ruidosamente a lo que exige recordar algo.

> **Antes de escribir código, lee [`CLAUDE.md`](CLAUDE.md).** Son las reglas que
> no se renegocian, y cada una salió de una falla concreta de la auditoría del
> 2026-09-01. No son principios generales: son el registro de lo que ya se rompió
> una vez.

## Estado — 2026-09-17

La última versión documentada es **v1.0.0** (2026-09-08): el primer producto
funcional, en el sentido de que una tienda peruana puede vender y declarar con
esto. El detalle, iteración por iteración, está en las
[notas de versión](ondexia.docs/06-notas-de-version.md).

| Pieza | Dónde | Estado |
|---|---|---|
| Aplicación web | `apps/frontend/ondexia.web` | Angular 21. Ventas, Almacén y Configuración **con datos reales**. Las maquetas de los módulos que aún no existen viven en `pages/_maquetas/`, fuera de la compilación y sin ruta |
| Panel interno | `apps/frontend/ondexia.admin` | Angular 21. Cuentas, planes y módulos contratados |
| Landing | `apps/frontend/ondexia.landing` | Astro, sin JavaScript de cliente |
| Portal público | `apps/frontend/ondexia.portal` | **No existe todavía** |
| Dominio compartido | `apps/backend/ondexia.domain` | Dominio puro: identidad, ventas, comprobantes, bitácora. Sin Spring |
| API Core | `apps/backend/ondexia.api` | Identidad, aislamiento multiempresa, permisos, configuración, caja, existencias y ventas |
| API del panel | `apps/backend/ondexia.admin` | Su propio desplegable y su propio pool de Cognito |
| Consultas externas | `apps/backend/ondexia.consultas` | Padrón de RUC y tipo de cambio. Solo lectura, sin certificados |
| Emisor | `apps/backend/ondexia.facturacion` | XML UBL 2.1, firma, sobre SOAP, CDR. **El único que guarda certificados** |
| Infraestructura | `ondexia.infra` | Terraform. `fmt`, `init` y `validate` pasan. **Nunca `plan` ni `apply` contra una cuenta real** |
| Base de datos | `db/migration` de la API | Migraciones V1 a V25 |
| Contrato HTTP | `ondexia.contracts/openapi.yaml` | Se regenera desde el código en cada ejecución de la suite |

### Dos cosas que faltan, y conviene decirlas claro

**El ensayo contra el entorno de pruebas de SUNAT no se ha ejecutado.** Todo lo
anterior está verificado —el XML, la firma, el sobre, la lectura de las tres
respuestas, la orden completa contra una SUNAT fingida—, pero **que SUNAT acepte
de verdad el XML que producimos** sigue sin comprobarse: el entorno donde se
construye no tiene salida a `e-beta.sunat.gob.pe`. Se ejecuta siguiendo
[doc 14 §6](ondexia.docs/14-emision-electronica.md).

**Nada está desplegado en AWS.** No es que el despliegue falle: los pasos 2, 3 y
4 del alta del pipeline —crear los roles desde un equipo y guardar sus ARN como
secretos de entorno— están sin hacer, y el workflow muere en «Asumir rol de solo
lectura» con un mensaje que no lo dice. El orden no es decorativo: el pipeline no
puede crear sus propios roles, su política se lo deniega. Guía completa en el
[README de infraestructura](ondexia.infra/README.md).

## Arrancar

Necesitas **Java 21**, **Node 22**, **pnpm**, **Docker** y, para
infraestructura, **Terraform 1.9 o superior**.

### Backend

```bash
cd apps/backend/ondexia.api && docker compose up -d
```

```bash
cd apps/backend && ./mvnw spring-boot:run -pl ondexia.api -am -Dspring-boot.run.profiles=local
```

**El perfil es obligatorio.** No hay perfil por omisión, y es a propósito:
`local` trae un emisor de tokens sin credencial, y arrancar sin querer con él en
otro sitio sería el fallo. Sin perfil la API no arranca, y es lo correcto.

Como no hay Cognito desplegado, ese emisor local firma los tokens de desarrollo:

```bash
curl -s -X POST "http://localhost:8080/desarrollo/token?sub=usuario-demo"
```

Detalle completo —los dos roles de base de datos, el certificado de RDS, cómo
correr sin Docker— en [`apps/backend/README.md`](apps/backend/README.md).

### Frontend

Cada aplicación se instala y se construye por separado; no hay workspace de pnpm
en la raíz.

```bash
cd apps/frontend/ondexia.web && pnpm install --frozen-lockfile && pnpm start
```

Lo mismo en `ondexia.admin` para el panel interno, y en `ondexia.landing` para la
página pública, donde el script es `pnpm dev`. Detalle en
[`apps/frontend/ondexia.web/README.md`](apps/frontend/ondexia.web/README.md).

## Pruebas

```bash
cd apps/backend && ./mvnw test
cd apps/backend && ./mvnw verify
cd apps/frontend/ondexia.web && pnpm run test:ci
```

Las pruebas de la API levantan **PostgreSQL de verdad** con Testcontainers, no un
motor en memoria: Row Level Security, los disparadores y los privilegios son
parte de lo que hay que probar, y ninguno de los tres existe en H2.

`verify` añade el análisis de dependencias, que descarga la base de
vulnerabilidades del NIST la primera vez. Para el día a día, `test` no la toca;
para saltarla explícitamente, `-Ddependency-check.skip=true`.

## Integración continua

Un solo job en [`ci.yml`](.github/workflows/ci.yml), sobre `main`, `develop` y
toda rama de trabajo. Construye y prueba los tres frontends y los cinco módulos
de Java, analiza dependencias y valida Terraform.

Incluye una comprobación poco habitual: **tono y forma**
([`herramientas/comprobar-tono-y-forma.mjs`](herramientas/comprobar-tono-y-forma.mjs))
lee el CSS y el HTML **compilados**, no el código. Sobre el código no se podría
—los comentarios explican en español lo que la pantalla decía antes, y buscar
palabras prohibidas ahí pondría el CI en rojo por documentar la decisión—, y
además Tailwind solo emite las utilidades que se usan: la ausencia de un radio en
la hoja servida no es una aproximación a «nadie lo escribió», es exactamente eso.

El despliegue ([`deploy.yml`](.github/workflows/deploy.yml)) es **manual**, con
casilla de «solo plan». Se deja así mientras no haya nada desplegado: un workflow
que apunta a nada solo produce rojos que enseñan a ignorar el tablero.

## Estructura

```
apps/
  backend/        Java, un solo árbol de Maven
                    domain · api · admin · consultas · facturacion
  frontend/       un build independiente cada uno
                    ondexia.web · ondexia.admin · ondexia.landing · ondexia.portal
ondexia.contracts/  OpenAPI, generado desde el código
ondexia.infra/      Terraform
ondexia.docs/       Especificación y decisiones
herramientas/       Comprobaciones del CI que no caben dentro de un módulo
```

El porqué de cada frontera está en
[`ondexia.docs/03-estructura-repositorio.md`](ondexia.docs/03-estructura-repositorio.md).

## Documentación

Antes de trabajar, leer completo [CLAUDE.md](CLAUDE.md). Consultar después el DTE
y los documentos pertinentes; sus apartados históricos no sustituyen decisiones
y lineamientos vigentes.

| Documento | Qué responde |
|---|---|
| [DTE-ONX-001](ondexia.docs/DTE-ONX-001_sistema_gestion_comercial.md) | **La especificación técnica.** Arquitectura, stack, modelo de datos, seguridad, costos. Cada decisión lleva su alternativa descartada y su motivo |
| [01 · Dominios](ondexia.docs/01-dominios.md) | Los conceptos del negocio |
| [02 · Alcance](ondexia.docs/02-alcance-modulos.md) | Qué entra en la v1, y en qué orden |
| [03 · Estructura](ondexia.docs/03-estructura-repositorio.md) | Por qué monorepo, convenciones de nombres, ramas, CI/CD |
| [04 · Organización](ondexia.docs/04-organizacion-y-suscripcion.md) | Cuenta, empresa, establecimiento, suscripción |
| [05 · Plan de vistas](ondexia.docs/05-plan-vistas-v1.md) | Las pantallas de la v1 |
| [06 · Notas de versión](ondexia.docs/06-notas-de-version.md) | **Qué cambió, cuándo, y qué quedó pendiente** |
| [07 · Plan de Configuración](ondexia.docs/07-plan-backend-configuracion.md) | El módulo de Configuración, entrega por entrega |
| [08 · Arquitectura del backend](ondexia.docs/08-arquitectura-backend.md) | **Dónde va cada cosa, y por qué** |
| [09 · Panel administrativo](ondexia.docs/09-panel-administrativo.md) | La consola interna: planes, módulos por cuenta, personal |
| [10 · Convenciones de interfaz](ondexia.docs/10-convenciones-de-interfaz.md) | **Lo que la interfaz ya hace y por qué.** Registro, color y contraste, tablas, foco |
| [11 · Registro de empresa](ondexia.docs/11-registro-de-empresa.md) | El alta: RUC verificado contra el padrón, casa matriz, primera caja |
| [12 · Plan del primer producto](ondexia.docs/12-plan-primer-producto.md) | **En qué se está trabajando ahora**, en ocho iteraciones |
| [13 · Ventas en punto de venta](ondexia.docs/13-ventas-en-punto-de-venta.md) | Caja, nota de venta y existencias |
| [14 · Emisión electrónica](ondexia.docs/14-emision-electronica.md) | XML, firma, envío, CDR, notas de crédito y comunicaciones de baja |
| [Propuesta de landing](ondexia.docs/landing-propuesta/) | Los bocetos de la página pública y su [plan de SEO](ondexia.docs/landing-propuesta/SEO.md) |
| [Infraestructura](ondexia.infra/README.md) | Qué crea Terraform, y **lo que hay que hacer a mano** |

> `ondexia.docs/html/` contiene una versión HTML **desactualizada** (v0.7) y su
> generador se perdió. Manda siempre el Markdown.

## Lineamientos de desarrollo y soporte

Complemento aprobado el 2026-10-10, incorporado al harness y a los documentos
existentes. Aplicar según alcance y riesgo; reutilizar decisiones y evidencias.

| Etapa | Qué debe quedar definido o comprobado | Referencia vigente |
|---|---|---|
| Negocio | Necesidad y fuente, proceso, alternativas, impacto, costos y rentabilidad de cliente/Ondexia por separado; datos desconocidos explícitos | [03, §11.1](ondexia.docs/03-estructura-repositorio.md#111-de-una-opinión-a-una-decisión-de-negocio) |
| Requisitos | Historias con valor; casos de uso con flujo, extensiones, validaciones y respuestas al aceptar/rechazar/quedar indeterminado; estados y recuperación pertinentes | [03, §11.2](ondexia.docs/03-estructura-repositorio.md#112-especificación-antes-de-código) |
| Diseño | Responsabilidades, encapsulamiento, dependencias, contratos e invariantes; SOLID y arquitectura hexagonal conforme a las decisiones existentes | [08, §10](ondexia.docs/08-arquitectura-backend.md#10-criterios-de-diseño-y-arquitectura) |
| UX/UI | Tareas y estados, recuperación, móvil/escritorio, teclado y foco; WCAG 2.2 AA como objetivo comprobable | [10, §15](ondexia.docs/10-convenciones-de-interfaz.md#15-uxui-y-accesibilidad) |
| Seguridad | Activos, amenazas y controles aplicables versionados; OWASP Top 10:2025, API Top 10:2023 y ASVS 5.0.0; configuración y pruebas efectivas | [03, §11.4](ondexia.docs/03-estructura-repositorio.md#114-seguridad-e-información) |
| Pruebas | Esperados independientes; correcciones con fallo sin arreglo y éxito con él; controles existentes con mutación aislada detectada y restaurada | [03, §11.3](ondexia.docs/03-estructura-repositorio.md#113-pruebas-y-evidencia) |
| Entrega y soporte | GitFlow, Conventional Commits en español, CI del mismo commit, auditoría independiente y seguimiento de resultados/costos | [03, §11.5](ondexia.docs/03-estructura-repositorio.md#115-entrega-soporte-y-mejora) |

SDD organiza la especificación versionada; BDD ayuda a descubrir y acordar
comportamientos; TDD guía lógica comprobable cuando corresponde. Antes de una
funcionalidad, presentar el plan y consultar las decisiones de negocio pendientes.
La aprobación del método no aprueba nuevas políticas, no ejecuta el backlog ni
certifica accesibilidad o seguridad. Declarar expresamente lo no verificado.

Usar los [formatos existentes de requisitos, pruebas, UX y seguridad](ondexia.docs/investigacion/2026-10-10-requisitos-ux-seguridad/plantillas.md),
con sus [fuentes y límites](ondexia.docs/investigacion/2026-10-10-requisitos-ux-seguridad/README.md).
El [plan de fundamentos](ondexia.docs/planes/2026-10-10-fundamentos-arquitectura-sdlc.md)
conserva sus actividades pendientes. No se ha verificado restauración del núcleo
externo del harness en otra máquina ([03, §11.6](ondexia.docs/03-estructura-repositorio.md#116-continuidad-del-harness)).

## Cuatro cosas que conviene saber antes de tocar código

**El API Core nunca abre una conexión hacia SUNAT.** Publica en una cola y
responde. Toda comunicación fiscal pasa por `ondexia.facturacion`, el único
componente que **guarda certificados y realiza operaciones fiscales** (DTE §3.3).
ArchUnit vigila que la API no adquiera ninguna dependencia de firma XML: un
módulo que puede firmar es un módulo cuyo compromiso emite documentos con valor
tributario.

La regla habla de certificados y no de «salir a internet», y la diferencia
importa: las consultas de solo lectura contra servicios externos —padrón de RUC,
tipo de cambio— viven en `ondexia.consultas`, que también sale a internet y **no**
debe acabar dentro de `facturacion` por ese parecido (DT-19). Lo que se protege
es el certificado, no la salida.

**El token porta identidad y nada más.** Ni permisos, ni empresa, ni rol, ni
plan: todo eso se resuelve en la base en cada petición, y `permisos_version`
invalida la caché de forma atómica. Un JWT vale hasta que caduca, y revocar
«anular comprobante» no puede esperar a la renovación (DTE §8.1).

**El aislamiento entre clientes lo pone la base, no el código.** Row Level
Security forzado sobre `empresa_actual()`, con un `set_config` local a la
transacción que falla cerrado. La aplicación nunca se conecta como superusuario:
un superusuario no está sujeto a RLS, y el aislamiento quedaría activo y sin
efecto — el peor estado posible, porque parece protegido.

**Los importes son `NUMERIC(18,6)`, jamás coma flotante**, en ninguna capa, y se
redondean al calcularlos, no al mostrarlos. SUNAT valida que la suma de las
líneas cuadre con el total declarado; un error de redondeo es un comprobante
rechazado.

## Ramas

GitFlow. `main` no es «la última versión», es **la versión con la que hay
comprobantes emitidos** — una afirmación con consecuencias legales. Nunca recibe
commits directos.

```
main <- release/* <- develop <- feature/*
                        ^
                    hotfix/* -> main y develop
```

Todo va en español: código, identificadores, comentarios, mensajes de error,
commits y documentación. Los commits no llevan marca de herramienta.

## Nota operativa

El repositorio vive dentro de una carpeta de OneDrive. Git y OneDrive compiten
por los mismos archivos, y ya ha causado problemas reales: `node_modules`
bloqueado durante un movimiento, `target/` que no se deja borrar, y paquetes que
aparecen a medio materializar. **Conviene moverlo fuera** (por ejemplo
`C:\dev\ondexia`): el respaldo lo da GitHub, no OneDrive.
