# Ondexia

Sistema de gestión comercial con facturación electrónica ante SUNAT, para
empresas peruanas. Almacén, compras, ventas y configuración, con emisión de
comprobantes electrónicos.

Monorepo. Lo desarrolla **una sola persona asistida por IA** (R-13), y esa
restricción explica buena parte de las decisiones: se prefiere lo que falla
ruidosamente a lo que exige recordar algo.

## Estado — 2026-08-22

| Pieza | Dónde | Estado |
|---|---|---|
| Aplicación web | `apps/frontend/ondexia.web` | 73 vistas navegables. **Configuración pide datos reales**; Panel, Almacén, Compras y Ventas siguen con datos de ejemplo. 52 pruebas |
| API Core | `apps/backend/ondexia.api` | Identidad, aislamiento multiempresa, permisos y el módulo de Configuración completo. Sin dominio de facturación |
| Dominio compartido | `apps/backend/ondexia.domain` | Identidad y bitácora |
| Motor de facturación | `apps/backend/ondexia.facturacion` | **No existe todavía** |
| Landing | `apps/frontend/ondexia.landing` | Astro. Una página, sin JavaScript de cliente. Construye y pasa por el CI. **Sin desplegar** |
| Portal público | `apps/frontend/ondexia.portal` | **No existe todavía** |
| Infraestructura | `ondexia.infra` | Terraform v1 escrito y validado. **Nunca aplicado contra una cuenta real** |
| Contrato HTTP | `ondexia.contracts/openapi.yaml` | Generado desde el código en cada ejecución de la suite |

**Nada está desplegado en AWS.** Fase 0 del plan por fases (DTE §10.3):
desarrollo íntegramente local, coste cero.

## Arrancar

Necesitas **Java 21**, **Node 22**, **pnpm** y **Docker**.

### Backend

```bash
cd apps/backend/ondexia.api && docker compose up -d
```

```bash
cd apps/backend && ./mvnw spring-boot:run -pl ondexia.api -am
```

No hay Cognito desplegado, así que el perfil `local` emite sus propios tokens:

```bash
curl -s -X POST "http://localhost:8080/desarrollo/token?sub=usuario-demo"
```

Detalle completo en [`apps/backend/README.md`](apps/backend/README.md).

### Frontend

```bash
cd apps/frontend/ondexia.web && pnpm install --frozen-lockfile && pnpm start
```

Detalle en [`apps/frontend/ondexia.web/README.md`](apps/frontend/ondexia.web/README.md).

## Estructura

```
apps/
├── backend/        Java · un solo árbol de Maven (domain, api, facturacion)
└── frontend/       Angular, Astro y portal · un build independiente cada uno
ondexia.contracts/  OpenAPI + catálogos SUNAT
ondexia.infra/      Terraform
ondexia.docs/       Especificación y decisiones
ondexia.tools/      Scripts
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
| [02 · Alcance](ondexia.docs/02-alcance-modulos.md) | Qué entra en la v1, y en qué orden (cortes C1 a C5) |
| [03 · Estructura](ondexia.docs/03-estructura-repositorio.md) | Monorepo, nomenclatura, GitFlow, Conventional Commits, CI/CD y desarrollo guiado por negocio, especificaciones y evidencia (§11) |
| [04 · Organización](ondexia.docs/04-organizacion-y-suscripcion.md) | Cuenta, empresa, sucursal, suscripción |
| [05 · Plan de vistas](ondexia.docs/05-plan-vistas-v1.md) | Las 73 pantallas |
| [06 · Notas de versión](ondexia.docs/06-notas-de-version.md) | Qué cambió y cuándo |
| [07 · Plan de Configuración](ondexia.docs/07-plan-backend-configuracion.md) | **En qué se está trabajando ahora** |
| [08 · Arquitectura del backend](ondexia.docs/08-arquitectura-backend.md) | **Dónde va cada cosa, y por qué** |
| [09 · Panel administrativo](ondexia.docs/09-panel-administrativo.md) | Plan de la consola interna: planes, módulos por cuenta, personal |
| [10 · Convenciones de interfaz](ondexia.docs/10-convenciones-de-interfaz.md) | Convenciones visuales existentes y criterios de UX/UI, móvil y accesibilidad (§15); distinguir reglas de trabajo de cumplimiento comprobado |
| [Propuesta de landing](ondexia.docs/landing-propuesta/) | Los bocetos de la página pública y su [plan de SEO](ondexia.docs/landing-propuesta/SEO.md) |

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

## Tres cosas que conviene saber antes de tocar código

**El API Core nunca abre una conexión hacia SUNAT.** Publica en una cola y
responde. Toda comunicación fiscal pasa por `ondexia.facturacion`, que es el
único componente que **guarda certificados y realiza operaciones fiscales**
(DTE §3.3). Si aparece una dependencia de firma XML en `ondexia.api`, la
arquitectura se rompió.

La regla habla de certificados y no de «hablar con el exterior», y la
diferencia importa: las consultas de solo lectura contra servicios externos
—padrón de RUC, tipo de cambio— viven en `ondexia.consultas`, que también sale
a internet y **no** debe acabar dentro de `facturacion` por ese parecido
(DT-19). Lo que se protege es el certificado, no la salida.

**El token porta identidad y nada más.** La empresa activa y los permisos se
resuelven en la base en cada petición. Un JWT vale hasta que caduca, y revocar
«anular comprobante» no puede esperar a la renovación (DTE §8.1).

**Los importes son `NUMERIC(18,6)`, jamás coma flotante**, y se redondean al
calcularlos, no al mostrarlos. SUNAT valida que la suma de las líneas cuadre
con el total declarado; un error de redondeo es un comprobante rechazado.

## Ramas

GitFlow. `main` no es «la última versión», es **la versión con la que hay
comprobantes emitidos** — una afirmación con consecuencias legales. Nunca recibe
commits directos.

```
main ← release/* ← develop ← feature/*
                      ↑
                  hotfix/* → main y develop
```

## Nota operativa

El repositorio vive dentro de una carpeta de OneDrive. Git y OneDrive compiten
por los mismos archivos, y ya ha causado problemas reales: `node_modules`
bloqueado durante un movimiento y paquetes que aparecen a medio materializar.
**Conviene moverlo fuera** (por ejemplo `C:\dev\ondexia`): el respaldo lo da
GitHub, no OneDrive.
