# Ondexia

Sistema de gestión comercial con facturación electrónica ante SUNAT, para
empresas peruanas. Almacén, compras, ventas y configuración, con emisión de
comprobantes electrónicos.

Monorepo. Lo desarrolla **una sola persona asistida por IA** (R-13), y esa
restricción explica buena parte de las decisiones: se prefiere lo que falla
ruidosamente a lo que exige recordar algo.

## Estado — 2026-08-11

| Pieza | Dónde | Estado |
|---|---|---|
| Aplicación web | `apps/frontend/ondexia.web` | 73 vistas navegables **con datos de ejemplo**. Sin backend conectado |
| API Core | `apps/backend/ondexia.api` | Esqueleto verificado: identidad, aislamiento multiempresa, permisos. Sin dominio de negocio |
| Dominio compartido | `apps/backend/ondexia.domain` | Identidad y bitácora |
| Motor de facturación | `apps/backend/ondexia.facturacion` | **No existe todavía** |
| Landing y portal público | `apps/frontend/ondexia.landing`, `.portal` | **No existen todavía** |
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

Empieza por el DTE; el resto deriva de él.

| Documento | Qué responde |
|---|---|
| [DTE-ONX-001](ondexia.docs/DTE-ONX-001_sistema_gestion_comercial.md) | **La especificación técnica.** Arquitectura, stack, modelo de datos, seguridad, costos. Cada decisión lleva su alternativa descartada y su motivo |
| [01 · Dominios](ondexia.docs/01-dominios.md) | Los conceptos del negocio |
| [02 · Alcance](ondexia.docs/02-alcance-modulos.md) | Qué entra en la v1, y en qué orden (cortes C1 a C5) |
| [03 · Estructura](ondexia.docs/03-estructura-repositorio.md) | Por qué monorepo, convenciones de nombres, ramas, CI/CD |
| [04 · Organización](ondexia.docs/04-organizacion-y-suscripcion.md) | Cuenta, empresa, sucursal, suscripción |
| [05 · Plan de vistas](ondexia.docs/05-plan-vistas-v1.md) | Las 73 pantallas |
| [06 · Notas de versión](ondexia.docs/06-notas-de-version.md) | Qué cambió y cuándo |

> `ondexia.docs/html/` contiene una versión HTML **desactualizada** (v0.7) y su
> generador se perdió. Manda siempre el Markdown.

## Tres cosas que conviene saber antes de tocar código

**El API Core nunca abre una conexión hacia SUNAT.** Publica en una cola y
responde. Toda comunicación fiscal pasa por `ondexia.facturacion`, que es el
único componente que toca certificados digitales (DTE §3.3). Si aparece una
dependencia de firma XML en `ondexia.api`, la arquitectura se rompió.

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
