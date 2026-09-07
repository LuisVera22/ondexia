# 13 · Ventas en punto de venta

Lo que el plan del primer producto ([12](12-plan-primer-producto.md)) construye
en las iteraciones 2 a 4: la caja, la nota de venta, el canje y las
existencias. Este documento crece con cada iteración; lo que todavía no existe
está marcado como pendiente y no se describe como si existiera.

| Parte | Iteración | Estado |
|---|---|---|
| §1 Caja y sesiones de caja | 2 | Hecho |
| §2 Productos, disponibilidad por local y existencias | 3 | Hecho |
| §3 Clientes | 3 | Hecho |
| §4 Nota de venta y canje | 4 | Pendiente |

## 1. Caja y sesiones de caja

### 1.1 Qué es

Una **caja** es un punto de cobro dentro de un establecimiento. Un
establecimiento tiene una o más; el código (`CAJA1`, `MOSTRADOR`) es único
dentro del establecimiento, no de la empresa, así que dos locales pueden tener
cada uno su `CAJA1`.

Una **sesión de caja** es un turno: desde que alguien abre la caja con lo que
hay en el cajón hasta que alguien —que puede ser otra persona— la cierra
declarando lo que contó. La venta (iteración 4) ocurrirá siempre dentro de una
sesión: sin sesión no hay a quién atribuir el efectivo.

### 1.2 Reglas y dónde viven

| Regla | Dónde se garantiza | Prueba |
|---|---|---|
| Toda caja pertenece a un establecimiento | `caja.sucursal_id NOT NULL`; `Caja` lo exige en el constructor | `CajasIT.establecimientoObligatorioYPropio` |
| El establecimiento es de la misma empresa | `Cajas.validarSucursal` | ídem |
| Código único por establecimiento | `caja_codigo_unico UNIQUE (empresa_id, sucursal_id, codigo)`; el caso de uso da el mensaje | `CajasIT.codigoUnicoPorEstablecimiento` |
| A lo sumo una sesión abierta por caja | Índice único parcial `sesion_caja_una_abierta (caja_id) WHERE estado = 'ABIERTA'`. No una comprobación en código: dos aperturas a la vez leerían «ninguna abierta» y las dos pasarían | `CajasIT.abrirYCerrarConArqueo` |
| Una caja inactiva no se abre; con sesión abierta no se desactiva | `SesionesDeCaja.abrir`, `Cajas.cambiarEstado` | `CajasIT.cajaInactivaNoSeAbre`, `abrirYCerrarConArqueo` |
| Una sesión cerrada es inmutable | Disparador `sesion_caja_cerrada_inmutable` sobre UPDATE y DELETE, además del dominio | `CajasIT.unaSesionCerradaNoSeToca` |
| El arqueo compara lo declarado con lo calculado y **no corrige**: la diferencia se guarda | `SesionCaja.cerrar` y `diferencia()` | `SesionCajaTest`, `CajasIT.abrirYCerrarConArqueo` |
| El efectivo calculado parte del monto inicial; las demás formas, de cero | `SesionCaja.cerrar` | `SesionCajaTest.calculadoPorFormaDePago` |
| Quien está acotado a un establecimiento solo ve y toca sus cajas | `Cajas.listar`, `Cajas.exigir` sobre `ContextoOperacion.sucursalId` | `CajasIT.alcancePorEstablecimiento`, `elVendedorVeSuCajaYNoCreaOtras` |
| Aislamiento entre empresas | RLS estándar en `caja` y `sesion_caja` | `AislamientoEmpresaIT` (política común) |

Las formas de pago son un enumerado del dominio, `FormaDePago`: efectivo,
tarjeta, transferencia y billetera digital. Una tabla para cuatro filas que no
cambian sería una tabla de más; si un día hace falta una quinta, es una
constante y una migración de nada, no un catálogo que mantener por empresa.

### 1.3 Lo que nace con cada establecimiento

`DotacionDeEstablecimiento` crea, al dar de alta un establecimiento, su almacén
y su primera caja (`CAJA1 · Caja 1`). Para la casa matriz el almacén es
`PRINCIPAL · Almacén principal`; para los demás, `ALM-<código del local>`. Lo
llaman los tres caminos por los que aparece un establecimiento: el registro de
la cuenta, el alta de otra empresa y la pantalla de establecimientos.

Los dos primeros ocurren **fuera** del contexto de la empresa nueva —el registro
no tiene contexto todavía, y el alta de empresa opera sobre otra empresa— y
`almacen` y `caja` están bajo Row Level Security. Por eso existe el puerto
`OperarComoEmpresa` (`OperadorComoEmpresa` en infraestructura): durante una
acción cambia el contexto que ven los casos de uso y la variable
`ondexia.empresa_id` que leen las políticas, vacía lo pendiente antes de
cambiar y después de la acción para que cada fila llegue a la base bajo la
empresa que le corresponde, y restaura todo al salir. Exige una transacción en
curso y no se usa para nada más. Lo ejercitan `RegistroIT`, `RegistroDeEmpresaIT`
y `ConfiguracionEmpresaIT`.

Una consecuencia que las pruebas tuvieron que respetar: una empresa registrada
ya no se puede borrar, porque su alta deja filas en la bitácora y la bitácora es
de solo inserción por disparador. `RegistroDeEmpresaIT` usa un RUC distinto por
prueba y cuenta las empresas en vez de suponer cuántas hay. Es lo mismo que
ocurre en producción.

### 1.4 API

Todo bajo `/api/v1/ventas/cajas`, con el submódulo de permisos `ventas.caja`
(V17). Las funciones —`consultar`, `registrar`, `editar`, `desactivar`, `abrir`,
`cerrar`— las reparte cada cuenta desde su pantalla de roles; el sistema solo
las da al Administrador. Que un cajero pueda abrir y cerrar su caja sin poder
crear cajas es exactamente la decisión que el doc 12 §6.1 deja al cliente.

| Método y ruta | Permiso | Qué hace |
|---|---|---|
| `GET /` | consultar | Las cajas que el usuario alcanza, cada una con su sesión abierta si la hay |
| `POST /` | registrar | Alta. `codigo`, `nombre`, `sucursalId` |
| `PUT /{id}` | editar | Cambia el nombre. El código no cambia |
| `PUT /{id}/estado` | desactivar | Activa o desactiva. Nunca se borra |
| `GET /{id}/sesion-abierta` | consultar | La sesión en curso, o 204 |
| `GET /{id}/sesiones` | consultar | Historial, de la más reciente a la más antigua |
| `POST /{id}/sesiones` | abrir | Abre con `montoInicial`. 400 `caja_ya_abierta`, `caja_inactiva` |
| `PUT /sesiones/{sesionId}/cierre` | cerrar | Cierra con `declarado` por forma de pago. Devuelve declarado, calculado y diferencia. 400 `sesion_ya_cerrada` |

Los importes viajan como números con hasta seis decimales y se guardan en
`NUMERIC(18,6)`, como todo importe.

### 1.5 Pantalla

`Ventas → Cajas`: una tabla con código, nombre, establecimiento, sesión y
estado (abierta, cerrada o inactiva). Las acciones de cada fila dependen del
estado y del permiso: «Abrir caja» solo sobre una caja activa y cerrada, «Cerrar
caja» solo sobre una abierta, y ninguna de las dos si el usuario no tiene la
función. El cierre pide lo declarado por forma de pago —lo vacío se declara como
cero— y muestra el arqueo con la diferencia, sin ofrecer corregirla.

La barra superior muestra la caja abierta del establecimiento activo
(`CajaActivaService`): quien vende tiene que saber dónde está cobrando sin ir a
buscarlo. Se recarga al cambiar de empresa y cuando la pantalla de cajas abre o
cierra una.

### 1.6 Lo que queda para la iteración 4

- `CobrosDeSesion` devuelve hoy un mapa vacío (`SinCobrosTodavia`): no hay
  ventas que sumar. Cuando exista `documento_venta` con sus pagos, se sustituye
  por la implementación que suma por forma de pago y `SesionesDeCaja` no cambia.
- El punto de venta exigirá una sesión abierta en la caja elegida antes de
  cobrar.
- Series por caja no se contemplan: la serie la decide el establecimiento (doc
  12 §3.4).

## 2. Productos, disponibilidad por local y existencias

### 2.1 El catálogo es de la empresa; el local decide si lo vende y a qué precio

Es la interpretación de «los productos se manejan por local» que fija el doc
12 §3.5, y la V18 la escribe en tres tablas:

| Tabla | Qué guarda | Regla |
|---|---|---|
| `producto` | Código, nombre, descripción, unidad (catálogo 03), afectación al IGV (catálogo 07), precio de lista, si controla existencias | Código único por empresa. La unidad y la afectación son atributos del bien y no pueden discrepar entre dos comprobantes del mismo RUC |
| `producto_local` | Por establecimiento: si se vende y el precio propio (nulo: el de lista) | **Sin fila, no se vende ahí.** Un producto nuevo nace disponible solo en el local donde se creó; los demás se activan en la ficha |
| `movimiento_stock` y `stock` | El libro (cada entrada y salida, con signo) y su proyección por almacén | El libro es de solo inserción por disparador; la proyección se mueve en la misma sentencia que el movimiento |

Los catálogos de SUNAT son enumerados del dominio, no tablas: `UnidadDeMedida`
trae las veintidós unidades que un mostrador usa (NIU, ZZ para servicios, KGM,
BG, MTR…) y `AfectacionIgv` las tres onerosas (10, 20, 30). Las variantes
gratuitas del catálogo 07 son de la operación —el mismo bien se vende gravado y
se regala— y se resolverán en la línea del documento, no en el producto. La API
publica ambos en `GET /api/v1/almacen/productos/catalogos` para que la pantalla
no tenga una segunda copia.

### 2.2 Existencias: el libro explica la proyección

`Existencias.ajustar` no fija una cantidad: registra la **diferencia** entre lo
contado y lo que había como un movimiento `AJUSTE`, y la proyección se mueve con
él. Contar lo mismo que había no anota nada. Así el libro siempre explica el
número que se ve, que es lo que el DTE pedía de `movimiento_stock` desde el
primer día.

La suma es atómica en la base: `INSERT … ON CONFLICT DO UPDATE SET cantidad =
stock.cantidad + EXCLUDED.cantidad` (`ExistenciasAdaptador`). Dos ventas del
mismo producto a la vez no pueden leer la misma existencia y escribir cada una la
suya; con entidades sería leer, sumar y guardar, que es justo la carrera. La
venta de la iteración 4 descargará por este mismo puerto con tipo `VENTA`.

Un producto que no controla existencias (un servicio) no admite ajustes: no hay
nada que contar.

| Regla | Prueba |
|---|---|
| Código único por empresa; unidad y afectación del catálogo | `ProductosIT.validaciones`, `ProductoTest.catalogos` |
| Nace disponible solo en el local de alta; los demás se fijan con precio propio; un local de otra empresa no vale | `ProductosIT.disponibilidadPorLocal` |
| El ajuste anota la diferencia, la proyección la sigue, el libro no se borra | `ProductosIT.ajusteDeExistencias` |
| Un servicio no tiene existencias | `ProductosIT.servicioSinExistencias` |
| Búsqueda por código o nombre sin mayúsculas (pg_trgm) | `ProductosIT.busqueda` |
| Aislamiento entre empresas | `ProductosIT.aislamiento` |

### 2.3 API y pantalla

Bajo `/api/v1/almacen/productos`, con los permisos `almacen.producto` (ficha y
disponibilidad) y `almacen.stock` (existencias: `consultar`, `ajustar`), que ya
existían en la V2.

| Método y ruta | Permiso | Qué hace |
|---|---|---|
| `GET /?q=` | producto:consultar | Todo el catálogo, o hasta 50 por código o nombre |
| `GET /catalogos` | producto:consultar | Unidades y afectaciones admitidas |
| `POST /` | producto:registrar | Alta; `sucursalId` obligatorio: el local donde nace disponible |
| `PUT /{id}`, `PUT /{id}/estado` | producto:editar, desactivar | Ficha y estado. El código no cambia; nunca se borra |
| `GET /{id}/locales`, `PUT /{id}/locales/{sucursalId}` | producto:consultar, editar | Disponibilidad y precio por establecimiento |
| `GET /{id}/existencias`, `GET /{id}/movimientos` | stock:consultar | La proyección y el libro |
| `POST /{id}/existencias/ajustes` | stock:ajustar | Conteo: `almacenId`, `cantidad`, `motivo` |

La pantalla `Almacén → Productos` trae el catálogo entero y filtra en el
navegador: son cientos de filas, no miles, y el filtro instantáneo vale más que
una petición por tecla; la búsqueda del servidor queda para el punto de venta.
La ficha tiene tres pestañas —datos generales, disponibilidad por local,
existencias con sus movimientos y el ajuste por conteo—. Las de presentaciones,
precios por tipo y kardex valorizado de la maqueta se retiraron: no existen en
el primer producto y una pestaña que promete algo que no se guarda enseña a no
confiar en la ficha.

## 3. Clientes

### 3.1 Documento del adquirente

`cliente` guarda al adquirente identificado con el catálogo 06 de SUNAT:
`TipoDocumentoIdentidad` admite DNI (1), carné de extranjería (4), RUC (6) y
pasaporte (7). Falta a propósito el «0 · sin documento»: el cliente sin
documento de una boleta o una nota de venta **no es una fila**, es la ausencia
de `cliente_id` en la venta (doc 12 §4.3).

Cada tipo valida su número al entrar —el DNI son ocho dígitos, el RUC lleva
dígito verificador (doc 12 §3.2)— y el error señala al campo: para eso
`ReglaDeNegocioViolada` gana en esta iteración un `campo` opcional, con el mismo
motivo que `Conflicto` ya lo tenía. El documento no cambia después del alta:
identifica al cliente en sus comprobantes; si se tecleó mal, se crea otro. La
unicidad es por `(empresa, tipo, número)` y la garantiza la base.

### 3.2 RUC verificado por el padrón; DNI sin firma

Un cliente con RUC puede llegar con la **atestación** de `GET /consultas/ruc`,
la misma firma que verifica el alta de empresas (doc 11). Entonces la razón
social y el domicilio salen de la firma, no del formulario, y `verificado_en`
queda con la fecha. Sin atestación también se admite —el servicio de consulta
puede no estar y el mostrador no puede esperar—, sin `verificado_en`, y la
pantalla lo dice: la factura de la iteración 4 lo volverá a comprobar. Una
atestación de otro RUC se rechaza.

Para el DNI se añade `GET /consultas/dni/{dni}` a `ondexia.consultas`, por los
mismos proveedores y credenciales que el RUC (Decolecta `GET /reniec/dni`,
ApiPeru `POST /dni`), con la misma cuota por identidad y el mismo throttling en
la pasarela. **Sin atestación**, y es una decisión: SUNAT no valida el nombre del
adquirente de una boleta, solo el número, y el número lo teclea quien vende. La
consulta es una comodidad para no teclear el nombre, y una comodidad no
necesita firma. Si el plan del proveedor no incluye RENIEC, responde 401 o 403
y la cascada pasa al siguiente; si ninguno la tiene, la pantalla avisa y deja
teclear el nombre.

| Regla | Prueba |
|---|---|
| DNI de ocho dígitos, RUC con dígito verificador, error en el campo | `ClienteTest`, `ClientesIT.rucInvalido` |
| Con atestación, razón social y domicilio del padrón y `verificadoEn` | `ClientesIT.clienteConRucVerificado` |
| La atestación de otro RUC no vale | `ClientesIT.atestacionDeOtroRuc` |
| Sin atestación se admite y se verifica después | `ClientesIT.rucSinVerificarYLuegoVerificado` |
| Documento único por empresa | `ClientesIT.clienteConDni` |
| La consulta de DNI devuelve el nombre en el orden de la boleta, sin firma; 404 si no existe; 401 sin solicitante | `ConsultaDeDniControllerTest` |

### 3.3 API y pantalla

Bajo `/api/v1/ventas/clientes` con el permiso `ventas.cliente` (V2): listado y
búsqueda (`?q=`, por nombre o documento), catálogo 06 en `/tipos-documento`,
alta con atestación opcional, edición de nombre y contacto,
`POST /{id}/verificacion` para aplicar una atestación nueva, y estado.

La ficha consulta SUNAT o RENIEC según el tipo, rellena lo que el servicio sabe y
guarda la atestación solo si la consulta fue de ese mismo número: cambiar el
número después de consultar la descarta, porque era de otro RUC
(`FichaClienteComponent`, con su prueba). El listado marca «RUC sin verificar»
para que no sorprenda al facturar.

## 4. Nota de venta y canje

Pendiente: iteración 4.

## Registro de cambios

- **v1 (2026-09-07)** — §1, con la iteración 2.
- **v1.1 (2026-09-07)** — §2 y §3, con la iteración 3.
