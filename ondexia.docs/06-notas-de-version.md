# Ondexia — Notas de versión

## v1.0.0 — El primer producto funcional

Fecha: 2026-09-08 · Rama: `claude/audit-review-validation-t1lisq`

**Una tienda peruana puede vender y declarar con esto.** Es lo que las ocho
iteraciones del [plan del primer producto](12-plan-primer-producto.md#8-iteraciones)
se propusieron, y el punto en el que el sistema deja de ser un recorrido de
pantallas para ser algo que se usa.

### Qué se puede hacer

| Lo que el cliente hace | Desde |
|---|---|
| Registrar su empresa con RUC 10 o 20, con su casa matriz, su almacén y su primera caja | Iteración 1 |
| Abrir y cerrar caja con arqueo por forma de pago | Iteración 2 |
| Llevar productos con unidad y afectación de SUNAT, existencias por almacén, y clientes verificados contra el padrón | Iteración 3 |
| Vender desde el punto de venta: nota de venta con correlativo, IGV, pago mixto y descarga de existencias | Iteración 4 |
| Emitir boleta y factura electrónicas: XML UBL 2.1 firmado, envío a SUNAT, CDR y estado visible con el código y la descripción del rechazo | Iteración 5 |
| Anular: nota de crédito desde el comprobante, comunicación de baja con su ticket, canje de nota de venta a comprobante | Iteración 6 |
| Ver la portada con la caja, lo vendido hoy y lo que sigue sin aceptar ante SUNAT | Iteración 7 |

### Lo que sostiene todo eso

- **El aislamiento entre clientes lo pone la base.** Row Level Security forzado
  sobre `empresa_actual()`, con la aplicación conectándose como un rol que no es
  superusuario —un superusuario no está sujeto a RLS y el aislamiento quedaría
  activo y sin efecto—.
- **El token porta identidad y nada más.** Permisos, empresa y plan se resuelven
  en cada petición; `permisos_version` invalida la caché de forma atómica, así
  que revocar un permiso surte efecto en la petición siguiente.
- **Los documentos emitidos son inmutables por disparador**, no por costumbre.
  Lo único que cambia es el estado, y solo por la máquina de estados.
- **La firma vive fuera de `ondexia.api`.** `ondexia.facturacion` es su propio
  desplegable, y ArchUnit vigila que la API no adquiera ninguna dependencia de
  firma XML: un módulo que puede firmar es un módulo cuyo compromiso emite
  documentos con valor tributario.
- **Importes en `NUMERIC(18,6)`** en todas las capas, nunca coma flotante.

### Cifras

| Qué | Cuánto |
|---|---|
| Pruebas del dominio | 117 |
| Pruebas de la API, con PostgreSQL de verdad | 255 |
| Pruebas del panel interno | 41 |
| Pruebas de consultas externas | 41 |
| Pruebas del Emisor | 27 |
| Pruebas del frontend | 78 |
| Migraciones de base de datos | V1 a V22 |

### Lo que esta versión también hizo con la interfaz

Un solo registro —formal, impersonal, preciso—, un radio de seis píxeles y una
sola sombra, para lo que flota. La landing pierde el sistema neobrutalista y el
menú se recorta a tres entradas: Ventas, Almacén y Configuración. Las cincuenta
maquetas de los módulos que aún no existen salen de las rutas y quedan en
`pages/_maquetas/`, fuera de la compilación. Un menú con entradas que no
funcionan es lo contrario de un producto mínimo. El detalle está en
[doc 10](10-convenciones-de-interfaz.md) §2 y §5.

### Lo que sigue pendiente, y hay que decirlo claro

- **El ensayo contra el entorno de pruebas de SUNAT no se ha ejecutado.** Todo
  lo anterior al último salto está verificado —el XML, la firma, el sobre SOAP,
  la lectura de las tres respuestas, la orden completa contra una SUNAT
  fingida—, pero **que SUNAT acepte de verdad el XML que producimos** sigue sin
  comprobarse. El entorno donde se construyó esta versión no tiene salida a
  `e-beta.sunat.gob.pe`. Se ejecuta siguiendo [doc 14 §6](14-emision-electronica.md)
  y hasta entonces la landing dice «en ensayo», no «listo».
- **Nada se ha desplegado en una cuenta de AWS real.** `terraform validate` pasa;
  `plan` y `apply` son parte del trabajo, no un trámite. La guía de alta del
  primer cliente está en el README de `ondexia.infra`.
- **El planificador que consulta los tickets de la comunicación de baja no
  existe.** Se sincroniza al listar, que funciona; un planificador es
  infraestructura que no se puede ejercitar desde el repositorio.
- **Compras, cotizaciones, preventas, guías de remisión y los catálogos de
  almacén siguen siendo maquetas.** Están en el repositorio, sin ruta, hasta su
  iteración.

---

## Entre v0.1.0 y v1.0.0

Trabajo entre `v0.1.0` y `v1.0.0`. Se conserva porque son varios cambios grandes
y quien vuelva dentro de unos meses no debería tener que reconstruirlos leyendo
el historial. En orden cronológico: primero la infraestructura y el backend,
después la conexión del primer módulo, y al final una revisión completa de la
interfaz.

Lo que en su momento se listó aquí como «lo que sigue sin existir» dejó de ser
cierto con la v1.0.0: el panel, Almacén y Ventas piden datos reales, la API se
despliega como Lambda, y la infraestructura está escrita entera. Lo que sigue sin
hacerse está arriba, en las pendientes de la v1.0.0.

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
