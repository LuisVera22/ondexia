# Ondexia — Modelo de organización y suscripción

Estado: **propuesta** · Fecha: 2026-08-06 · Afecta al DTE §5 (modelo de datos) y al plan de vistas

## 1. Las cuatro unidades no son lo mismo

Conviene separarlas antes de decidir precios, porque se confunden con facilidad y tienen naturaleza distinta.

| Unidad | Qué es | Ante SUNAT |
|---|---|---|
| **Cuenta** | El **titular de la suscripción**: quien contrata Ondexia y paga | No existe. Es un concepto comercial nuestro |
| **Empresa** | Un **RUC**. Es quien emite el comprobante y quien responde tributariamente | Es el emisor. Tiene su propio certificado digital y sus propias credenciales SOL |
| **Establecimiento** (local, sucursal) | Un punto físico del **mismo RUC** | Se registra en la ficha RUC y recibe un **código de establecimiento anexo**. El principal suele ser `0000` |
| **Almacén** | Espacio donde hay existencias | No existe para SUNAT. Es interno |

### 1.1 Jerarquía

```
Cuenta  ──  Suscripción  ──  Plan (límites: N empresas, M usuarios)
  │
  ├── Empresa (RUC)  ──  Establecimientos  ──  Almacenes
  ├── Empresa (RUC)  ──  Establecimientos  ──  Almacenes
  │
  └── Usuarios  ──  acceso otorgado por empresa
```

**La suscripción pertenece a la cuenta, no a la empresa.** Es la corrección que hace coherente el resto: un plan que otorga "hasta 2 empresas" solo tiene sentido si el contrato está por encima de las empresas. Si la suscripción colgara de cada RUC, el cliente con dos empresas simplemente contrataría dos suscripciones y los planes escalonados no existirían.

Los **usuarios también cuelgan de la cuenta**, no de la empresa: una misma persona —el contador, típicamente— opera los dos RUC con una sola credencial. Por eso el modelo de datos del DTE ya tenía `usuario` global con una tabla puente `usuario_empresa` que otorga el acceso.

Un establecimiento **no** es una empresa: comparte RUC, certificado y responsabilidad tributaria. Un almacén tampoco es un establecimiento: un local puede tener varios almacenes, y un almacén puede no ser un local de atención.

Un establecimiento **no** es una empresa: comparte RUC, certificado y responsabilidad tributaria. Un almacén tampoco es un establecimiento: un local puede tener varios almacenes, y un almacén puede no ser un local de atención.

> **Verificar antes de implementar:** el tratamiento normativo del código de establecimiento anexo en cada tipo de comprobante, en particular su obligatoriedad en la guía de remisión, donde identifica el punto de partida.

## 2. Qué limita la suscripción

Los planes descritos limitan **empresas** y **usuarios**. La pregunta abierta era qué hacer con los establecimientos.

**Recomendación: no limitar establecimientos ni almacenes.**

La razón no es comercial sino de calidad del dato. Si el plan cobra por local, el cliente con tres tiendas registra una sola y carga todo ahí. El resultado es un código de establecimiento incorrecto en los comprobantes y en las guías de remisión — es decir, **el precio empuja al cliente a declarar mal**. No conviene monetizar un campo que tiene que ser exacto.

Empresas y usuarios sí son ejes legítimos: cada empresa adicional es otro RUC, otro certificado y otro trámite de homologación; cada usuario adicional es carga real de la plataforma.

### 2.1 El eje de cobro está acoplado a DT-12

Antes de fijar precios hay que resolver algo que parecía puramente técnico: **si la emisión es propia o vía proveedor** (DTE §7.1).

| Decisión DT-12 | Costo marginal por comprobante | Qué habilita comercialmente |
|---|---|---|
| **Emisión directa a SUNAT** | Cero | Se puede ofrecer **comprobantes ilimitados** con tarifa plana |
| **Vía proveedor** | Real, por cada documento | Obliga a incluir cupo con excedente, o el margen se erosiona al crecer el cliente |

Esto convierte a DT-12 en una decisión comercial, no solo de arquitectura. **Comprobantes ilimitados es un argumento de venta fuerte** en un mercado donde buena parte de la competencia cobra por paquetes de comprobantes: el cliente que crece deja de ser castigado por crecer.

Recomendación coherente con el resto del proyecto: **empezar con proveedor** (por el riesgo de una sola persona), pero **no prometer ilimitado mientras se dependa de él**. Incluir un cupo mensual holgado con excedente facturable, y pasar a ilimitado cuando la emisión sea propia. Convertir eso en un hito de producto comunicable.

### 2.2 Qué módulos ofrecer por plan

**Recomendación: no restringir los tres módulos núcleo. Almacén, Compras y Ventas van completos en todos los planes.**

La tentación es poner Compras en un plan superior. No conviene, y no es por generosidad: **los tres módulos son un mismo flujo de trabajo**. Un cliente que no puede registrar compras no tiene existencias correctas, y sin existencias correctas Ventas entrega datos equivocados. El resultado no es un cliente que sube de plan, es un cliente que concluye que el producto no funciona.

Restringir una pieza de la que dependen las otras produce un producto roto, y el cliente culpa al producto, no al plan.

Ejes de diferenciación que sí funcionan porque **no rompen nada al faltar**:

| Se diferencia por | Base | Intermedio | A demanda |
|---|---|---|---|
| Empresas (RUC) | 1 | 2 | Negociable |
| Usuarios | 2 | 5 | Negociable |
| Almacén, Compras y Ventas | Completos | Completos | Completos |
| Notas de crédito y débito | Sí | Sí | Sí |
| **Guías de Remisión Electrónica** | No | Sí | Sí |
| **Roles personalizados** | Roles fijos | Sí | Sí |
| **Acceso por API para integradores** | No | No | Sí |
| **Reportes avanzados y exportación** | Básicos | Completos | Completos + a medida |
| Soporte | Correo | Correo prioritario | Canal directo |

**El límite de empresas quedó confirmado el 2026-08-23 en 1 / 2 / negociable**, y
está sembrado en `plan.max_empresas` (V9) como `1 / 2 / NULL` — donde `NULL` es
sin límite, no cero. Cambiarlo es un `UPDATE` de tres filas; por eso los límites
son una tabla y no constantes en el código.

La GRE es el mejor candidato a estar en plan superior y la razón es honesta: **es una integración técnica aparte** (DTE R-06), con costo real de construcción y mantenimiento, y no todos los clientes trasladan mercadería. Un comercio de mostrador no la necesita.

Lo que **nunca** debe restringirse: emitir, anular y consultar comprobantes; la retención de XML y CDR; y el acceso a los datos históricos. Todo eso tiene obligación legal de 5 años y limitarlo expondría al cliente ante SUNAT.

### 2.3 Hipótesis de precios

> **Advertencia:** estas cifras son un punto de partida razonado, **no un estudio de mercado**. No tengo forma de verificar precios vigentes de la competencia peruana. Valídalas con el método de §2.5 antes de publicarlas.

| Plan | Empresas | Usuarios | Precio mensual (hipótesis) |
|---|---|---|---|
| **Base** | 1 | 2 | S/ 89 |
| **Intermedio** | 2 | 5 | S/ 199 |
| **A demanda** | Negociable | Negociable | Desde S/ 390 |

Complementos:

| Complemento | Precio (hipótesis) |
|---|---|
| Usuario adicional | S/ 29 / mes |
| Empresa (RUC) adicional | S/ 69 / mes |
| Pago anual | Dos meses sin costo (≈ 17 % de descuento) |

**Los usuarios se cuentan por cuenta, no por empresa.** El plan Intermedio da 5 usuarios en total, no 5 por RUC. Es lo más simple de explicar y refleja el caso real: el contador que opera ambas empresas es una persona, no dos.

Tres criterios detrás de estos números:

1. **Se compite contra el statu quo, no contra un competidor.** Hoy el cliente paga un proveedor de facturación y lleva el inventario en Excel. Ondexia reemplaza ambos, así que el precio se compara con esa suma, no con el precio de la facturación sola.
2. **El escalón entre Base e Intermedio es de más del doble a propósito.** Un cliente con dos RUC es notoriamente más grande y tiene más capacidad de pago.
3. **El costo de infraestructura es irrelevante para el precio.** Con ~24 USD/mes repartidos entre todos los clientes, fijar precio sobre costo llevaría a un número absurdamente bajo. El precio se fija por valor entregado.

### 2.4 El costo que sí limita: el soporte

Con un solo desarrollador (DTE R-13), **el recurso escaso no es el servidor, es tu tiempo**. Cada cliente consume horas de soporte, y en un producto tributario esas consultas no son opcionales: un comprobante rechazado es urgente para el cliente.

Consecuencia directa: **precios bajos son un riesgo operativo, no una ventaja competitiva.** Vender barato a muchos clientes pequeños es la vía más rápida a un soporte desbordado que degrada el servicio para todos. Es preferible cobrar más y atender bien a menos.

**Sin plan gratuito.** Un nivel gratuito en un producto fiscal atrae usuarios que consumen soporte y generan datos con consecuencias legales. En su lugar:

**Prueba gratuita de 14 a 30 días emitiendo contra la beta de SUNAT.** Los comprobantes no tienen validez legal, así que no hay obligación de conservación ni riesgo tributario, el cliente prueba el producto completo, y a nosotros no nos cuesta nada. Es la única forma de prueba gratuita que no genera pasivo.

### 2.5 Cómo validar estos precios antes de publicarlos

No hace falta un estudio formal. Con esto alcanza:

1. **Revisar cinco competidores peruanos directos** y anotar qué incluyen, no solo cuánto cobran. La comparación útil es qué se llevan por su precio.
2. **Conversar con cinco a diez prospectos reales** — pequeños comercios y distribuidoras. La pregunta que sirve no es "¿pagarías S/ 89?" sino **"¿cuánto pagas hoy por facturación y cuánto tiempo pierdes cuadrando el inventario?"**. La primera se responde por cortesía; la segunda da cifras.
3. **Publicar precios desde el primer día.** Ocultarlos para "conversar" filtra clientes y multiplica el trabajo comercial, que también sale de tu tiempo.
4. **Subir precios es más difícil que bajarlos.** Ante la duda, arrancar arriba y ajustar con descuentos de lanzamiento, que son reversibles sin renegociar con nadie.

**Pendiente de tu decisión:** nombres comerciales de los planes y confirmación de los precios tras la validación. Nada de esto bloquea el desarrollo — el plan de vistas solo necesita saber **qué se limita**, no cuánto cuesta.

### 2.2 Qué pasa al alcanzar un límite

Es un estado de interfaz que hay que diseñar, no un error a improvisar:

| Situación | Comportamiento |
|---|---|
| Intentar crear el usuario que excede el plan | El formulario se abre pero el botón de guardar queda inhabilitado, con el motivo visible y un enlace a la vista de suscripción |
| Intentar crear la empresa que excede el plan | Igual |
| El plan baja y quedan recursos por encima del límite | **Nunca borrar datos.** Los excedentes pasan a solo lectura hasta que el cliente regularice |

Esa última regla importa: borrar una empresa por una baja de plan destruiría comprobantes con obligación de conservación de 5 años.

## 3. Contexto de trabajo en la interfaz

De aquí sale la decisión que estaba pendiente sobre el selector de empresa.

**El encabezado muestra el contexto activo: empresa y establecimiento.** Cada uno se comporta según cuántas opciones tenga el usuario:

| Opciones disponibles | Qué se muestra |
|---|---|
| Una sola | Texto fijo, no es un control. Un desplegable de un elemento es ruido |
| Dos o más | Desplegable para cambiar de contexto |

Así el plan Base se ve limpio (una empresa, sin selector) y el plan Intermedio activa el selector sin rehacer el layout. Es el mismo componente en dos estados, no dos diseños.

### 3.1 Por qué el establecimiento va en el contexto y no en cada documento

El establecimiento determina dos cosas del comprobante: **la serie** que se usa y **el almacén** que descarga existencias. Pedirlo dentro de cada documento invita al error — el operador de la tienda de San Isidro no debería poder emitir con la serie de Miraflores por un descuido de un desplegable.

Se fija una vez, en el contexto de trabajo, y se muestra siempre visible para que nadie emita sin saber desde dónde está emitiendo.

### 3.2 Consecuencia sobre las series

Las series de comprobante se configuran **por establecimiento y por tipo de documento**:

| Establecimiento | Factura | Boleta |
|---|---|---|
| `0000` Principal | `F001` | `B001` |
| `0001` Miraflores | `F002` | `B002` |

Esto ya estaba previsto en el modelo de datos del DTE (`serie_correlativo` con `sucursal_id`); acá queda explícito el porqué.

## 4. Impacto en el modelo de datos del DTE

La tabla `sucursal` del DTE §5.2 ya cubre el establecimiento. **Lo que falta es la entidad `cuenta`**, que el DTE no contemplaba porque se escribió antes de definir el modelo de suscripción:

| Entidad | Ajuste |
|---|---|
| `cuenta` | **Nueva.** `razon_social_titular`, `ruc_titular`, `email_contacto`, `estado` |
| `empresa` | Añadir `cuenta_id` |
| `usuario` | Añadir `cuenta_id` |
| `sucursal` | Añadir `codigo_establecimiento_sunat` (4 dígitos) y marcar `es_principal` |
| `plan` | `codigo`, `nombre`, `max_empresas`, `max_usuarios` |
| `suscripcion` | `cuenta_id`, `plan_id`, `estado`, `vigente_desde`, `vigente_hasta` |

> **`suscripcion` referencia a `cuenta_id`, no a `empresa_id`.** Es la consecuencia directa de §1.1 y el punto donde es más fácil equivocarse: enlazarla a la empresa haría imposible un plan de dos empresas.

El aislamiento multiempresa del DTE §5.1 **no cambia**: las tablas transaccionales siguen filtrando por `empresa_id`, no por `cuenta_id`. La cuenta es la frontera comercial; la empresa sigue siendo la frontera de los datos.

## 5. Fuera del alcance de v1.0

**No se construyen vistas de pago ni de contratación en línea.** La vista de suscripción muestra el plan vigente y el consumo frente a los límites; cambiar de plan es una gestión comercial fuera de la aplicación por ahora. Incorporar cobros exige pasarela de pago, facturación de la propia suscripción y manejo de datos de tarjeta: es un proyecto aparte, no una vista más.
