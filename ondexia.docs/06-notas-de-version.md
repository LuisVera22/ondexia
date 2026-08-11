# Ondexia — Notas de versión

## Sin publicar

Trabajo posterior a `v0.1.0`, todavía sin etiquetar. Se resume aquí porque son
cuatro cambios grandes y quien vuelva dentro de unos meses no debería tener que
reconstruirlos leyendo el historial.

### Se retiró la plantilla de terceros · 2026-08-10

El tema visual se reescribió por completo —tipografía, tokens de color,
sombras, marcos, menú lateral— y se retiró todo rastro de la plantilla de
partida para evitar cualquier conflicto de licencia. La interfaz no cambió de
aspecto; cambió de origen.

### Infraestructura en Terraform · 2026-08-10

`ondexia.infra` con la v1 completa: VPC sin NAT, RDS `db.t4g.micro`, tres
buckets con CloudFront, dos grupos de usuarios de Cognito, API Gateway con
autorizador JWT nativo. **Terraform sustituye a AWS CDK** (DT-16). Cuesta
~15 USD/mes, de los que RDS es el 90 %.

`fmt`, `init` y `validate` pasan. **No se ha ejecutado `plan` ni `apply` contra
una cuenta real.**

### Esqueleto del backend · 2026-08-11

Maven multi-módulo sobre Java 21 y Spring Boot 4.0.7. Aislamiento multiempresa
con Row Level Security, autorización por `(modulo, accion)` resuelta en base en
cada petición, catálogo de ~150 permisos y cuatro roles predefinidos.
15 pruebas de integración contra PostgreSQL real.

Hallazgo que conviene no olvidar: **un rol superusuario de PostgreSQL se salta
todas las políticas de RLS**, y `FORCE ROW LEVEL SECURITY` no le alcanza. Se
detectó porque las pruebas de aislamiento fallaron; sin ellas habría llegado a
producción pareciendo protegido. La aplicación ahora se niega a arrancar si
detecta un rol así.

### Reorganización y pipelines · 2026-08-11

`apps/backend/` y `apps/frontend/` agrupan lo que antes colgaba suelto. Los
**tres pasos del CI estaban rotos** y se corrigieron; `deploy.yml` se reescribió
entero para Terraform, porque seguía siendo de CDK.

### Lo que sigue sin existir

- **La aplicación no se conecta al backend.** El frontend sigue con datos de
  ejemplo; la primera conexión es `GET /api/v1/contexto`.
- **La API no se despliega.** `ondexia.api` es una aplicación web de Spring
  Boot, sin el adaptador para Lambda (DT-D17).
- **Nada se ha desplegado en AWS.**

---

## v0.1.0 — Recorrido cognitivo del frontend

Fecha: 2026-08-07 · Rama: `release/0.1.0` → `main`

### Qué incluye

Las **73 rutas** de la versión 1.0 navegables de principio a fin, con la
identidad visual propia de Ondexia. (La plantilla de partida se retiró por completo el 2026-08-10; ver «Sin publicar», más arriba.)

| Módulo | Contenido |
|---|---|
| **Acceso** | Iniciar sesión, recuperar contraseña, sin permisos, página no encontrada |
| **Panel** | Pendientes que exigen atención, indicadores, últimos comprobantes, acciones frecuentes |
| **Almacén** | Productos y su ficha, presentaciones, tipos de precio, marcas, modelos, unidades, almacenes, guías de ingreso, guías de remisión, productos por agotarse |
| **Compras** | Proveedores y su ficha, notas de pedido, órdenes de compra y de servicio, notas de compra, facturas de compra, liquidaciones de compra |
| **Ventas** | Clientes y su ficha, cotizaciones, notas de preventa, boletas, facturas, notas de crédito, comunicación de baja, resumen diario, formas de pago, detalle de comprobante |
| **Configuración** | Empresa, identidad visual, establecimientos, series y correlativos, usuarios, roles y permisos, comprobantes, suscripción |

### Qué NO incluye

**No es una aplicación funcional.** Es un recorrido cognitivo navegable:

- Sin backend, sin peticiones HTTP, sin persistencia
- Los datos son constantes escritas en cada componente
- Las validaciones de formato son reales; las de negocio no
- Nada se envía a SUNAT

### Componentes reutilizables construidos

La razón por la que 73 pantallas fueron viables:

| Componente | Pantallas que lo usan |
|---|---|
| `tabla-datos` | 26 |
| `editor-documento` | 12 |
| `listado-documentos` | 11 |
| `editor-lineas` | 12 |
| `buscador-entidad` | 12 |
| `estado-comprobante` | 14 |
| `confirmacion` | 9 |
| `pagina-vacia` | 26 |
| `selector-contexto` | Layout |

Catálogo consultable en `/kit/componentes`.

### Deuda declarada

| # | Deuda | Razón |
|---|---|---|
| 1 | Datos de ejemplo dentro de cada componente | Decisión explícita: se buscaba validar el recorrido antes de escribir backend. Al conectarlo habrá que reescribir la obtención de datos en cada vista |
| 2 | Ninguna pantalla verificada visualmente durante su construcción | El entorno de desarrollo no tuvo navegador disponible. Se garantiza compilación y resolución de rutas, no apariencia |
| 3 | El kit de la plantilla sigue en el menú | Referencia interna mientras se construye. **Debe eliminarse antes de publicar** |
| 4 | Sin pruebas automatizadas | Fuera del alcance de esta entrega |
| 5 | Notas de débito fuera del alcance | Comparten editor con las notas de crédito; incorporarlas es trabajo menor |

### Decisiones del dominio visibles en la interfaz

- La factura advierte si el cliente tiene DNI **antes** de emitir, en lugar de esperar el rechazo de SUNAT
- La nota de crédito se emite desde el detalle del comprobante de origen, nunca desde un formulario en blanco
- La comunicación de baja solo lista comprobantes dentro de los 7 días calendario
- El resumen diario separa ticket de estado, porque «enviado» no significa «aceptado»
- La guía de remisión traslada sin vender: su detalle no lleva precios ni IGV
- La liquidación de compra es el único documento de compras que emite Ondexia, y el único que no exige RUC
- Las existencias son de solo consulta: se modifican con guías de ingreso y ventas
- El RUC de la empresa se bloquea cuando ya hay comprobantes emitidos

### Antes de la siguiente versión

1. ~~Retirar el kit de la plantilla del menú y del enrutador~~ — hecho el 2026-08-10
2. Resolver DT-12: emisión propia ante SUNAT o vía proveedor
3. Definir qué documentos de la cadena de compras son obligatorios (riesgo C-1)
4. Confirmar precios de los planes tras validarlos con prospectos

Los tres pendientes son **decisiones de negocio, no de ingeniería**, y siguen
abiertos. DT-12 es el que más pesa: decide el costo por comprobante y con él el
margen del producto.
