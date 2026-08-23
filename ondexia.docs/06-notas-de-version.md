# Ondexia — Notas de versión

## Sin publicar

Trabajo posterior a `v0.1.0`, todavía sin etiquetar. Se resume aquí porque son
varios cambios grandes y quien vuelva dentro de unos meses no debería tener que
reconstruirlos leyendo el historial. En orden cronológico: primero la
infraestructura y el backend, después la conexión del primer módulo, y al final
una revisión completa de la interfaz.

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

### Configuración conectada al backend · 2026-08-16 al 2026-08-22

**El frontend dejó de ser un recorrido con datos de ejemplo**, al menos en un
módulo. Configuración —empresas, establecimientos, almacenes, series, usuarios,
roles, comprobantes, identidad—, más el contexto, el perfil y el registro,
piden datos reales a la API.

Con ello llegó la tubería de errores: `ProblemDetail` (RFC 9457) desde el
backend, y en el cliente `interpretarError`, que **solo confía en el `detail`
del servidor si viene con `codigo`** —el campo que pone nuestro manejador y que
API Gateway nunca pone—. Sin esa comprobación, un error de infraestructura
llegaría al usuario con el texto que le apeteciera a AWS.

Los errores de campo se pintan sobre su campo y se limpian al corregirlo. Los
mensajes de validación pasaron a `ValidationMessages.properties`: los de
Hibernate llegaban diciendo «el tamaño debe estar entre 0 y 200».

También apareció la capacidad de **reactivar** un establecimiento o un almacén,
que no existía: `DELETE /{id}` se sustituyó por `PUT /{id}/estado`, idempotente
y auditado, con pruebas de ida y vuelta que comprueban que el código sobrevive
a la desactivación.

### La interfaz, reescrita por dentro · 2026-08-22

Cuatro ramas seguidas sobre el mismo tema. El detalle y los porqués están en
[10 · Convenciones de interfaz](10-convenciones-de-interfaz.md); aquí lo que
cambió:

- **Paleta propia.** El índigo `#4f46e5` sustituye al azul `#465fff` de la
  plantilla, en los tokens y en los cinco SVG de la marca. El botón primario
  pasa de 4,84:1 a 6,29:1 contra el blanco.
- **Tablas con dos presentaciones.** Desde 640 px la tabla de siempre; por
  debajo, cada fila es una tarjeta. Antes, en un móvil los diecisiete listados
  aparecían dentro de una caja que se arrastraba de lado con las primeras
  columnas fuera de vista.
- **Buscador y recarga** en la tabla, acciones de fila en un menú, rayado
  cebra, y el recuento arriba en lugar de al pie.
- **Panel principal rehecho**: saludo con la fecha de Lima —no la del
  navegador, que puede decir otro día del que estampará SUNAT—, banda de avisos
  solo cuando hay algo, gráfico de barras sin dependencias nuevas, estado ante
  SUNAT y existencias por agotarse.
- **Barra superior**: fija de verdad —el `sticky` estaba en un elemento sin
  holgura y no pegaba, en silencio—, contexto de trabajo como control
  segmentado, y en teléfono los controles bajan a una segunda fila.
- **La descripción de cada módulo** pasa detrás de un botón de información, y
  la ruta de navegación sube por encima del título.
- **Foco de teclado definido.** No había ninguno: se dependía del contorno por
  defecto del navegador.
- **El icono de la pestaña** deja de ser el de Angular.

Y las primeras **pruebas automatizadas del frontend**: 52, la mayoría de
comportamiento, algunas de maquetación. La deuda 4 de la v0.1.0 deja de estar
al descubierto, aunque no está saldada.

### Lo que sigue sin existir

- **Panel, Almacén, Compras y Ventas siguen con datos de ejemplo.** Solo
  Configuración pide datos reales. El panel principal, en particular, está
  construido sobre constantes con la forma que tendrá la respuesta.
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
