# 13 · Ventas en punto de venta

Lo que el plan del primer producto ([12](12-plan-primer-producto.md)) construye
en las iteraciones 2 a 4: la caja, la nota de venta, el canje y las
existencias. Este documento crece con cada iteración; lo que todavía no existe
está marcado como pendiente y no se describe como si existiera.

| Parte | Iteración | Estado |
|---|---|---|
| §1 Caja y sesiones de caja | 2 | Hecho |
| §2 Productos disponibles por local y existencias | 3 | Pendiente |
| §3 Nota de venta y canje | 4 | Pendiente |

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

## 2. Productos disponibles por local y existencias

Pendiente: iteración 3.

## 3. Nota de venta y canje

Pendiente: iteración 4.

## Registro de cambios

- **v1 (2026-09-07)** — §1, con la iteración 2.
