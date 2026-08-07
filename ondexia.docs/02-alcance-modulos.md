# Ondexia — Alcance funcional V1

Estado: **borrador para confirmación** · Fecha: 2026-08-06 · Fuente: inventario de módulos del sistema de referencia

> Este documento fija **qué se construye en V1**. Es el insumo de alcance del que el DTE deriva entidades, flujos técnicos e integraciones. No sustituye a un PRD (ver §5).

## 1. Módulos en alcance

Tres módulos: **Almacén**, **Compras**, **Ventas**. Usuarios es transversal, no un módulo de negocio (ver §4).

### 1.1 Almacén

| Submódulo | V1 | Notas |
|---|---|---|
| Productos | ✅ | Entidad núcleo de todo el sistema |
| Presentación | ✅ | Unidad de venta/empaque del producto |
| Productos por agotarse | ✅ | Vista derivada: stock vs punto de reorden. No es entidad nueva |
| Guías de Remisión | ✅ | **Comprobante electrónico** — integración SUNAT GRE (ver §3) |
| Guías de Ingreso | ✅ | Documento interno de recepción de mercadería |
| Tipos de Precio | ✅ | Listas de precio por segmento de cliente |
| Marcas | ✅ | Catálogo maestro |
| Modelos | ✅ | Catálogo maestro, depende de Marca |
| Unidades | ✅ | Unidades de medida — deben mapear al catálogo SUNAT nº 03 |
| Retazos Vidrio | ❌ | Fuera de V1 |
| Traslados | ❌ | Fuera de V1 — pero ver riesgo A-1 |
| Clases | ❌ | Fuera de V1 |
| Administrar Jabas | ❌ | Fuera de V1 |

### 1.2 Compras

| Submódulo | V1 | Notas |
|---|---|---|
| Proveedores | ✅ | Maestro. Validación de RUC contra SUNAT |
| Órdenes de Compra | ✅ | Documento interno |
| Órdenes de Servicio | ✅ | Documento interno, sin movimiento de stock |
| Nota de Pedidos | ✅ | Documento interno previo a la OC |
| Nota de Compra | ✅ | Documento interno |
| Facturas (compra) | ✅ | **Registro** de comprobantes recibidos, no emisión |
| Liquidación de Compra | ✅ | **Comprobante electrónico que TÚ emites** (tipo 04) — ver §3 |
| Importaciones | ❌ | Fuera de V1 |
| Letras por Pagar | ❌ | Fuera de V1 |
| Nota de Crédito Compra | ❌ | Fuera de V1 (registro de NC recibida) |

### 1.3 Ventas

| Submódulo | V1 | Notas |
|---|---|---|
| Clientes | ✅ | Maestro. Validación RUC/DNI |
| Cotizaciones | ✅ | Documento interno |
| Notas de Preventa | ✅ | Documento interno previo al comprobante |
| Facturas | ✅ | **Comprobante electrónico** (tipo 01) |
| Boletas | ✅ | **Comprobante electrónico** (tipo 03) |
| Resumen Diario | ✅ | **Obligatorio para boletas** (RC) |
| Formas de Pago | ✅ | Contado / crédito / mixto |
| Notas de Crédito | ❌ | Fuera de V1 — **ver riesgo V-1, recomendamos incluirla** |
| Notas de Débito | ❌ | Fuera de V1 |
| Comunicación de Baja | ❌ | Fuera de V1 — **ver riesgo V-1** |
| Letras por Cobrar | ❌ | Fuera de V1 |
| Métodos de Pago | ❌ | Fuera de V1 — posible duplicado de "Formas de Pago" (ver §5) |

## 2. Riesgos de alcance detectados

### V-1 — Sin Nota de Crédito ni Comunicación de Baja no existe forma legal de anular una venta · **Severidad: Alta**

Emitir facturas y boletas sin poder corregirlas deja al usuario sin salida ante el error más frecuente de la operación diaria (cliente equivocado, monto equivocado, devolución de mercadería). En el régimen peruano hay exactamente dos mecanismos, y ambos quedaron fuera de V1:

| Mecanismo | Para qué sirve | Plazo |
|---|---|---|
| **Comunicación de Baja** | Anular una factura ya enviada a SUNAT | Hasta 7 días calendario desde la emisión |
| **Nota de Crédito** (tipo 07) | Anular fuera de plazo, devoluciones, descuentos, corrección de datos | Sin plazo equivalente |

Pasado el plazo de la baja, la NC es el **único** camino. Las boletas, además, se anulan por el Resumen Diario (que sí está en V1) pero solo dentro de su ventana.

**Recomendación:** subir **Nota de Crédito** y **Comunicación de Baja** a V1. El costo incremental es bajo — reutilizan el mismo pipeline de firma y envío que ya construimos para factura y boleta — y sin ellas el producto no es operable en producción real.

### A-1 — Guías de Remisión sin Traslados · **Severidad: Media**

La GRE está en V1 pero "Traslados" no. Si el traslado entre almacenes propios es un caso de uso real, la guía existe sin el movimiento de inventario que la origina. Confirmar si en V1 la GRE cubre solo el traslado **por venta** (origen: almacén → cliente); si es así, no hay problema y se deja constancia.

### C-1 — Cadena de compras larga sin definir obligatoriedad · **Severidad: Media**

Nota de Pedido → Orden de Compra → Nota de Compra → Factura de compra son cuatro documentos internos encadenados. Hay que definir cuáles son obligatorios y cuáles opcionales, y si se puede saltar pasos. Si los cuatro son obligatorios siempre, la fricción operativa es alta; si ninguno lo es, la trazabilidad se pierde.

## 3. Documentos que tocan SUNAT en V1

Esta es la parte cara del alcance y determina la arquitectura. **Cinco tipos de comprobante electrónico** en V1:

| Documento | Código | Canal SUNAT | Módulo |
|---|---|---|---|
| Factura de venta | 01 | CPE | Ventas |
| Boleta de venta | 03 | CPE + Resumen Diario | Ventas |
| Resumen Diario de boletas | RC | CPE (asíncrono, con ticket) | Ventas |
| Liquidación de Compra | 04 | CPE | Compras |
| Guía de Remisión Electrónica | 09 / 31 | **API REST GRE — canal distinto** | Almacén |

Dos observaciones que condicionan el diseño técnico:

1. **La GRE no usa el mismo canal que el resto.** Desde su migración, la guía electrónica se emite contra una **API REST con autenticación OAuth2**, mientras que factura, boleta, resumen y liquidación van por el canal CPE tradicional. Son dos integraciones distintas con dos modelos de autenticación distintos, aunque compartan la firma digital del XML (UBL 2.1). Presupuestar como tal.
2. **El Resumen Diario es asíncrono.** No devuelve el CDR en la misma llamada: entrega un **ticket** que hay que consultar después. Eso obliga a tener trabajos en segundo plano y manejo de estados desde el día uno — no es algo que se pueda agregar luego.

Si se aprueba la recomendación de V-1, se suman Nota de Crédito (07) y Nota de Débito (08) por el canal CPE, más la Comunicación de Baja.

## 4. Usuarios y seguridad (transversal)

No es un módulo de negocio sino una capa que cruza los tres. Alcance V1:

- Autenticación con MFA opcional
- Roles y permisos por módulo y por acción (no basta rol global: quien registra una compra no necesariamente aprueba la orden)
- Multiempresa: un usuario puede operar más de un RUC emisor
- **Bitácora de auditoría** sobre todo documento que llegue a SUNAT — quién emitió, quién anuló, cuándo. Es requisito de trazabilidad, no una funcionalidad opcional

## 5. Preguntas abiertas

1. ¿Se aprueba subir **Nota de Crédito** y **Comunicación de Baja** a V1? (riesgo V-1)
2. **Formas de Pago** vs **Métodos de Pago**: ¿son conceptos distintos (condición contado/crédito vs instrumento efectivo/tarjeta/transferencia) o el sistema de referencia los duplica?
3. ¿La GRE de V1 cubre solo traslado por venta, o también entre almacenes propios? (riesgo A-1)
4. ¿Cuáles documentos de la cadena de compras son obligatorios? (riesgo C-1)
5. ¿El sistema opera **un solo RUC emisor** o es multiempresa desde V1? Cambia el modelo de datos y el manejo de certificados digitales.

## 6. Capacidad del equipo y cortes verticales

> **Actualización:** el proyecto lo desarrolla **una sola persona, asistida por IA**.

### 6.1 El alcance V1 está dimensionado para un equipo

Lo marcado ✅ en §1 son **23 submódulos**, más 5 tipos de comprobante electrónico, más multiempresa, más autenticación con roles y permisos por acción. Es alcance de equipo, no de una persona.

Sin cambiar nada, el resultado previsible no es el fracaso sino algo peor de detectar: **muchos módulos a medias y ninguno en producción**. La IA acelera la escritura de código, pero no acelera la homologación ante SUNAT, ni las decisiones de negocio, ni el descubrimiento de que un supuesto tributario era falso.

**El alcance no se reduce: se secuencia.** Todo lo de §1 se construye, pero en cortes verticales que llegan a producción uno por uno.

### 6.2 Cortes propuestos

Cada corte es **funcional de extremo a extremo** — no "todos los catálogos primero". Un corte terminado es algo que un cliente real puede usar.

| # | Corte | Contenido | Por qué en este orden |
|---|---|---|---|
| **C1** | **Emitir una boleta** | Empresa · Usuario · Producto (mínimo) · Cliente · Boleta · emisión · CDR · PDF · portal público | Atraviesa **toda** la arquitectura y ataca primero lo más incierto: la emisión ante SUNAT. Si algo va a salir mal, que salga mal en el mes 1, no en el 9 |
| **C2** | **Operar ventas de verdad** | Factura · **Nota de Crédito** · **Comunicación de Baja** · Resumen Diario · Formas de Pago · Cotización · Nota de Preventa | Cierra el riesgo V-1: aquí el sistema pasa a ser legalmente operable |
| **C3** | **Catálogo completo** | Marcas · Modelos · Unidades · Presentaciones · Tipos de Precio · Productos por agotarse | Ya hay algo en uso que justifica enriquecer el catálogo |
| **C4** | **Compras** | Proveedores · Órdenes de Compra y Servicio · Notas de Pedido y Compra · Facturas de compra · Guías de Ingreso · Liquidación de Compra | Módulo entero, independiente del riesgo de ventas |
| **C5** | **Guías de Remisión** | GRE con su canal REST propio | **Deliberadamente al final:** es una segunda integración completa (R-06) y la menos necesaria para el primer cliente que paga |

**Multiempresa se mantiene desde C1**, pero solo en el modelo de datos (`empresa_id` en todas las tablas). La interfaz de gestión de varias empresas puede esperar. Agregar la columna después es carísimo; agregar la pantalla después es trivial.

### 6.3 Qué valida cada corte

C1 no es un prototipo desechable: es **el corte que responde las preguntas caras**. Al terminarlo ya sabes si la firma XAdES quedó bien, si la homologación avanza, cuánto cuesta realmente la infraestructura y si el desacople por cola funciona como se diseñó. Todas esas respuestas llegan antes de haber invertido en 23 submódulos.

### 6.4 Orden de magnitud

Con una persona asistida por IA, C1 es cuestión de semanas y el conjunto C1–C5 es cuestión de **varios trimestres**, no de semanas. No es una estimación formal —eso corresponde al PRD y al plan de proyecto— pero sirve para una cosa importante: **decidir con qué corte se sale a buscar el primer cliente**, en vez de esperar a tenerlo todo.

## 7. Relación con el proceso COE QE

Este documento **no es un PRD**. El skill `dte-coe-qe` exige un PRD en estado "Aprobado" como precondición bloqueante, y no lo tenemos: no se ejecutó la Etapa 1 de descubrimiento (AS-IS, Personas, Journeys, Escenarios, TO-BE).

Decisión tomada: se avanza con el DTE en estado **"Preliminar"**, declarando explícitamente la desviación. El DTE no podrá pasar a "Aprobado" hasta que exista el PRD y una bitácora de peer review. Ver la nota de desviación en el §1 del DTE.

Razón: el alcance está acotado por un sistema de referencia ya en operación, lo que reduce —sin eliminar— el riesgo de construir sobre requisitos no validados.
