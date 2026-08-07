# Ondexia — Plan de vistas v1.0

Estado: **propuesta (v3)** · Fecha: 2026-08-06 · Solo frontend · Base: plantilla TailAdmin en `apps/ondexia.web`

> **v3** — Se agrega el módulo Configuración con gestión de logos, se incorporan Notas de Crédito y Comunicación de Baja, se resuelve Almacenes, y se desglosan las vistas por submódulo. Las "olas" de versiones previas ahora se llaman "Etapas".

## 1. Qué es y qué no es este entregable

**Es** un recorrido cognitivo navegable: las pantallas de la v1.0 con datos fijos escritos en el propio componente, para validar navegación y flujo de trabajo antes de escribir backend.

**No es** una aplicación funcional. Sin servicios, sin peticiones HTTP, sin validaciones reales, sin persistencia.

**Deuda asumida:** al conectar el backend habrá que reescribir la obtención de datos en cada vista. Se acota manteniendo los datos de ejemplo en una constante única por componente (`DATOS_EJEMPLO`), para que el punto a reemplazar sea uno solo y esté señalado.

## 2. Un submódulo no es una vista

Corrección respecto de la versión anterior, que subestimaba el trabajo. Un submódulo del menú es una **entrada de navegación**; las vistas que lo componen dependen de su naturaleza:

| Tipo de submódulo | Vistas que lo componen | Ejemplo |
|---|---|---|
| Catálogo simple | Listado ruteado + ficha en modal | Marcas, Unidades |
| Catálogo complejo | Listado + ficha ruteada con pestañas | Productos |
| Documento | Listado + editor + detalle en solo lectura | Órdenes de Compra |
| Comprobante electrónico | Listado + emisión + detalle con estado SUNAT | Facturas, Boletas |
| Vista derivada | Solo listado | Productos por Agotarse |

**Total estimado: ~58 pantallas ruteadas y ~12 modales**, sobre 30 submódulos. La proporción es cercana a dos por submódulo, no una.

## 3. Convención de idioma en el código

**Lo que escribimos nosotros va en español; lo que impone el framework queda en inglés.**

| Elemento | Idioma | Ejemplo |
|---|---|---|
| Clases, archivos y selectores | Español | `ProductosComponent`, `productos.component.ts` |
| Propiedades y métodos propios | Español | `listaProductos`, `guardarProducto()` |
| Rutas | Español | `/almacen/productos`, `/ventas/boletas` |
| Texto visible | Español, registro técnico | "Emitir comprobante", nunca "Dale, emite" |
| API de Angular | Inglés | `ngOnInit`, `@Input`, `signal` |
| Términos sin traducción establecida | Inglés | `endpoint`, `token`, `payload` |

**Ortografía en la interfaz:** con tilde y plural correcto — "Presentaciones", "Guías de Remisión", "Tipos de Precio", "Órdenes de Compra", "Notas de Pedido". El concepto y el orden del menú no cambian respecto del inventario.

## 4. Decisiones tomadas

### 4.1 Módulo Configuración — se agrega

Cuarto módulo, al final del menú, separado de los tres operativos porque se usa al inicio y luego casi nunca.

### 4.2 Almacenes — se crea como submódulo, con valor por defecto automático

**Decisión: existe el submódulo Almacenes dentro de Almacén.**

Se evaluó derivarlo del establecimiento —un almacén implícito por local— pero se descarta: es muy común que un mismo local tenga tienda y depósito como espacios distintos, sobre todo en distribución. Derivarlo obligaría a rehacerlo al primer cliente con esa realidad.

**Para que no estorbe a quien no lo necesita:** al crear un establecimiento se crea automáticamente su "Almacén Principal". Un negocio de un solo local nunca entra a esa vista y todo funciona; quien necesita separar depósito de tienda lo hace sin pedir cambios.

Justifica su existencia: Productos por Agotarse compara existencias contra un mínimo, y esas existencias tienen que estar en algún lugar; las Guías de Ingreso deben ingresar mercadería a un destino concreto.

### 4.3 Notas de Crédito y Comunicación de Baja — se incorporan

Entran al alcance. Con ellas el sistema deja de tener el vacío de no poder anular una venta emitida.

**Notas de Débito quedan fuera**, tal como está el inventario. Comparten editor con las Notas de Crédito —cambia el catálogo de motivos y el efecto sobre el importe—, así que incorporarlas después es trabajo menor.

### 4.4 Detalle de comprobante emitido — vista compartida

No es un submódulo del menú. Es la vista a la que se llega desde los listados de Facturas, Boletas y Notas de Crédito, y muestra estado ante SUNAT, XML, CDR, PDF y trazabilidad. Una sola vista parametrizada por tipo.

## 5. Estructura del menú

Se respeta el orden del inventario. **El orden del menú no es el orden de construcción** (§8): el menú se ordena por cómo lo busca el usuario, la construcción por dependencia técnica.

| Módulo | Submódulos |
|---|---|
| **Almacén** | Productos · Presentaciones · Productos por Agotarse · Guías de Remisión · Guías de Ingreso · Tipos de Precio · Marcas · Modelos · Unidades · **Almacenes** |
| **Compras** | Facturas · Notas de Pedido · Liquidación de Compra · Notas de Compra · Órdenes de Compra · Órdenes de Servicio · Proveedores |
| **Ventas** | Clientes · Cotizaciones · Facturas · Boletas · **Notas de Crédito** · Notas de Preventa · **Comunicación de Baja** · Resumen Diario · Formas de Pago |
| **Configuración** | Empresa · Identidad Visual · Establecimientos · Almacenes · Series y Correlativos · Usuarios · Roles y Permisos · Comprobantes · Suscripción |

## 6. Módulo Configuración en detalle

### 6.1 Empresa

Datos que van impresos en cada comprobante: RUC, razón social, nombre comercial, domicilio fiscal, ubigeo, teléfono, correo. Solo lectura para el RUC una vez emitido el primer comprobante — cambiarlo invalidaría la numeración.

### 6.2 Identidad Visual

Gestión de logos, **por empresa y no por cuenta**: en multiempresa cada RUC tiene su propia marca.

| Recurso | Uso | Requisitos |
|---|---|---|
| **Logo principal** | Encabezado de la aplicación, PDF de comprobantes, informes | Horizontal, fondo transparente, PNG o SVG |
| **Logo para ticket** | Impresión térmica de boletas | Monocromo, alto contraste, ancho reducido |
| **Símbolo** | Barra lateral colapsada, favicon | Cuadrado |

Dos razones para separar el logo de ticket del principal:

1. **La impresora térmica es monocroma y angosta.** Un logo a color con degradados sale como una mancha gris. Necesita una versión pensada para blanco y negro.
2. **El PDF A4 y el ticket de 80 mm tienen proporciones incompatibles.** Forzar el mismo archivo en ambos deja el logo ilegible en uno de los dos.

La vista incluye **previsualización sobre el comprobante**, no solo el archivo suelto. Ver el logo dentro de la factura es la única forma de detectar que quedó desproporcionado antes de emitir.

### 6.3 Comprobantes

Preferencias de emisión e impresión: formato por defecto (A4 o ticket), leyenda al pie, moneda predeterminada, tasa de IGV vigente, y si el comprobante se envía por correo al cliente automáticamente.

### 6.4 Resto

Establecimientos, Almacenes, Series y Correlativos, Usuarios, Roles y Permisos, y Suscripción y Consumo, según lo definido en el documento 04.

## 7. Patrones — la observación que define el tamaño del trabajo

Son ~58 pantallas, pero solo **7 patrones**.

| Patrón | Se repite en | Descripción |
|---|---|---|
| **P1 · Listado con filtros** | ~26 pantallas | Encabezado, filtros, tabla paginada, acciones por fila, estado vacío |
| **P2 · Ficha de catálogo** | ~11 | Formulario de entidad simple, en modal o página |
| **P3 · Editor de documento** | **~14** | Cabecera + líneas de detalle + totales |
| **P4 · Detalle de comprobante** | ~3 | Solo lectura: estado SUNAT, XML, CDR, PDF |
| **P5 · Panel de indicadores** | 2 | Tarjetas y gráficos |
| **P6 · Formulario de configuración** | ~6 | Secciones con guardado por bloque |
| **P7 · Selección y acción masiva** | 2 | Marcar varios documentos y aplicarles una acción |

**P3 es el componente crítico del proyecto.** Lo usan Cotizaciones, Notas de Preventa, Boletas, Facturas, Notas de Crédito, Notas de Pedido, Órdenes de Compra, Órdenes de Servicio, Notas de Compra, Facturas de compra, Liquidación de Compra, Guías de Ingreso y Guías de Remisión. Comparten cerca del 80 %: buscar producto, agregar línea, calcular IGV según afectación, totalizar.

Construirlo una vez y parametrizarlo por tipo de documento es la decisión que más tiempo ahorra. **Construirlo catorce veces es el error más caro posible en este frontend.**

**P7 es nuevo** y lo exigen Comunicación de Baja y Resumen Diario: ambos operan sobre un conjunto de comprobantes seleccionados, no sobre uno solo. No es un editor de documento, es una lista con selección múltiple, motivo y confirmación.

## 8. Etapas de construcción

Cada etapa deja algo recorrible de principio a fin.

### Etapa 0 · Base

Sin vistas nuevas.

- Menú lateral con los cuatro módulos reales
- Rutas en español con estructura por módulo
- Componentes transversales: `tabla-datos`, `buscador-entidad`, `editor-lineas`, `selector-contexto`, `estado-comprobante`, `pagina-vacia`, `confirmacion`
- Retirar del menú las páginas de demostración de la plantilla, moviéndolas a `/kit` como referencia interna a eliminar antes de publicar

### Etapa 1 · Acceso y Configuración

| Submódulo | Pantallas |
|---|---|
| Acceso | Iniciar sesión · Recuperar contraseña |
| Sistema | Panel inicial · Mi perfil · No encontrada · Sin permisos |
| Empresa | Datos de la empresa |
| Identidad Visual | Logos con previsualización |
| Establecimientos | Listado + ficha |
| Almacenes | Listado + ficha |
| Series y Correlativos | Listado + ficha |
| Usuarios | Listado + ficha |
| Roles y Permisos | Listado + editor de permisos |
| Comprobantes | Preferencias de emisión |
| Suscripción | Plan y consumo |

**Sin vista de registro público.** Los usuarios los crea el administrador de la empresa; el autoregistro no tiene sentido en un sistema de facturación y abre superficie innecesaria. La vista `sign-up` de la plantilla se retira.

### Etapa 2 · Catálogos de Almacén

Orden interno por dependencia: un producto necesita unidad, marca, modelo y tipo de precio ya existentes.

| Submódulo | Pantallas |
|---|---|
| Unidades | Listado + ficha |
| Marcas | Listado + ficha |
| Modelos | Listado + ficha |
| Tipos de Precio | Listado + ficha |
| Presentaciones | Listado + ficha |
| Productos | Listado + ficha con pestañas |

La ficha de Producto es la más densa de la etapa: datos generales, presentaciones asociadas, precios por tipo y existencias por almacén, resueltos con pestañas.

### Etapa 3 · Terceros

| Submódulo | Pantallas |
|---|---|
| Clientes | Listado + ficha |
| Proveedores | Listado + ficha |

### Etapa 4 · Ventas

Aquí se construye **P3** y el sistema se vuelve demostrable.

| Submódulo | Pantallas |
|---|---|
| Formas de Pago | Listado + ficha |
| Cotizaciones | Listado + editor + detalle |
| Notas de Preventa | Listado + editor + detalle |
| Boletas | Listado + emisión |
| Facturas | Listado + emisión |
| *(compartida)* | Detalle de comprobante |
| Notas de Crédito | Listado + emisión |
| Comunicación de Baja | Listado + nueva comunicación **(P7)** |
| Resumen Diario | Listado + detalle **(P7)** |

Formas de Pago va primero porque el editor de documentos la necesita como catálogo.

Se empieza por Cotización a propósito: usa el mismo editor pero **no tiene consecuencias fiscales**, así se pule el componente más difícil sin riesgo de confundirlo con un documento emitido.

**La Nota de Crédito parte siempre de un comprobante existente.** El acceso correcto es desde el detalle del comprobante con la acción "Emitir nota de crédito", no desde un formulario en blanco. El listado del menú sirve para consultarlas, no para crearlas desde cero. Así se evita el error de referenciar mal el documento de origen.

### Etapa 5 · Compras

| Submódulo | Pantallas |
|---|---|
| Notas de Pedido | Listado + editor |
| Órdenes de Compra | Listado + editor + detalle |
| Órdenes de Servicio | Listado + editor |
| Notas de Compra | Listado + editor |
| Facturas | Listado + registro + detalle |
| Liquidación de Compra | Listado + emisión + detalle |

**Riesgo abierto (C-1):** hay cuatro documentos internos encadenados y falta definir cuáles son obligatorios y si se pueden saltar pasos. Se necesita tu criterio antes de esta etapa, no antes de arrancar.

### Etapa 6 · Movimientos de Almacén

Dependen de productos, almacenes, proveedores y clientes ya existentes.

| Submódulo | Pantallas |
|---|---|
| Guías de Ingreso | Listado + editor |
| Guías de Remisión | Listado + emisión + detalle |
| Productos por Agotarse | Listado |

El editor de Guía de Remisión es el único P3 con secciones propias: punto de partida, punto de llegada, transportista y datos del traslado.

Productos por Agotarse va al final porque solo tiene sentido cuando ya existe movimiento de existencias que lo alimente.

### Etapa 7 · Cierre

Panel principal definitivo. Se rehace al final, cuando ya se sabe qué indicadores tienen sentido; diseñarlo primero sería inventar métricas sobre entidades que aún no existen.

## 9. Criterios de recorrido cognitivo

1. **Una acción principal por pantalla**, destacada y siempre en la misma posición.
2. **Migas de pan en todas las vistas internas**, con el componente que la plantilla ya trae.
3. **El estado vacío enseña.** La primera vez que se entra a Productos, la pantalla explica qué es y ofrece crear el primero.
4. **Confirmación solo en lo irreversible.** Anular un comprobante la exige; guardar un borrador no.
5. **El contexto siempre visible:** empresa y establecimiento en el encabezado.
6. **Distinguir borrador de emitido.** Un documento con valor fiscal debe verse claramente distinto de uno editable. Es la distinción más importante de toda la interfaz.
7. **Los errores dicen qué hacer.** "El cliente requiere RUC para emitir factura", no "Error de validación".

## 10. Pendientes que no bloquean la Etapa 0

| # | Pendiente | Bloquea |
|---|---|---|
| 1 | ¿Qué documentos de la cadena de compras son obligatorios? (C-1) | Etapa 5 |
| 2 | ¿Formas de Pago y Métodos de Pago son lo mismo? El inventario marca solo la primera | Etapa 4 |
| 3 | Favicon: ¿`.ico` o `favicon.svg`? | Etapa 0 |
