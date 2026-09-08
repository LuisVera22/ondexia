# Ondexia — Plan del primer producto funcional

Estado: **propuesta** · Fecha: 2026-09-07 · Base: rama `feature/auditoria-2026-09-01` en `36ad2d3` · Reemplaza el orden de trabajo de [02 §6.2](02-alcance-modulos.md) y [05 §8](05-plan-vistas-v1.md) en lo que contradice

> Este plan responde a una pregunta concreta: **qué es lo mínimo que un negocio
> peruano puede usar de verdad, y en qué orden se construye sobre lo que ya
> existe**. Parte de seis decisiones nuevas del propietario (§1) y del estado real
> del código (§2), no del alcance ideal de V1. Todo lo que no entra aquí sigue
> siendo válido; solo cambia cuándo.

## 0. En una página

**El primer producto es una caja que vende y emite.** Un negocio con RUC se
registra solo, crea su local y su caja, carga sus productos, abre caja, vende con
boleta, factura o nota de venta, el comprobante llega a SUNAT, se imprime, y al
final del día cierra caja con su arqueo. Si se equivocó, anula por nota de
crédito o comunicación de baja. Nada más.

| | |
|---|---|
| **Módulos que entran** | Configuración (existe), Ventas en su forma de punto de venta, Almacén en su forma mínima (producto y existencias por local) |
| **Módulos que esperan** | Compras entero, guías de remisión, cotizaciones y preventas, catálogos ricos (marcas, modelos, presentaciones, tipos de precio), portal público, reportes, panel de indicadores |
| **SUNAT** | **Sí, desde el primer producto.** Revierte la decisión del 2026-08-11 ([07 §1.2](07-plan-backend-configuracion.md)). Boleta y factura se emiten; la nota de venta no |
| **Iteraciones** | 8, de una a dos semanas cada una, cada una con algo que se puede enseñar |
| **Costo de nube** | ~15 USD/mes con clientes reales; 0 USD/mes mientras se desarrolla. La emisión no añade costo fijo (§5) |
| **Decisiones** | Las diez de §10 quedaron tomadas y sustentadas el mismo día en §10.1 |

## 1. Lo que cambia respecto a lo ya decidido

Cada fila es una decisión del propietario del 2026-09-07 y lo que mueve en los
documentos anteriores. Se anota en vez de reescribir aquellos documentos: quien
los lea verá la nota y sabrá qué cambió y por qué.

| Decisión nueva | Qué decían los documentos | Qué cambia |
|---|---|---|
| **Dos roles por defecto: Propietario y Administrador.** El cliente crea los demás | Cuatro roles de sistema sembrados en V1: `ADMINISTRADOR`, `VENDEDOR`, `ALMACENERO`, `CONTADOR`. Roles personalizados solo desde el plan intermedio ([04 §2.2](04-organizacion-y-suscripcion.md)) | Se retiran tres roles de la semilla. Roles personalizados en todos los planes. El Propietario ya existe con otro nombre: es `cuenta_administrador` (§6) |
| **Varias cajas por local** | No hay caja en ningún documento ni tabla | Entidad nueva con apertura, cierre y arqueo (§4.2) |
| **Los productos se manejan por local** | Producto por empresa; existencias por almacén ([DTE §5.3](DTE-ONX-001_sistema_gestion_comercial.md)) | Catálogo por empresa, **disponibilidad, precio y existencias por local**. Ver la alternativa descartada en §4.3 |
| **Factura entre empresas; boleta con y sin DNI y se envía a SUNAT; nota de venta sin DNI y sin SUNAT** | V1 sin SUNAT. Sin nota de venta en el catálogo (V5 admite solo 01, 03, 07, 08, 09) | Entra `ondexia.facturacion`. La nota de venta es un **documento interno** con su propia serie, fuera del catálogo 01 (§3.3) |
| **Se registran personas naturales con negocio (RUC 10) y personas jurídicas (RUC 20)** | El alta exige RUC ACTIVO y HABIDO ([11 §8](11-registro-de-empresa.md)); el autorregistro quedó **cerrado** por la cadena crítica 2 de la auditoría | El autorregistro vuelve, con los controles que la auditoría pedía y no se hicieron (§6.2). El prefijo 25 se confirma antes de admitirlo (§10) |
| **Frontend: de criollismo a formalidad y minimalismo** | El criollismo está confinado a la landing por diseño ([landing-propuesta/SEO.md](landing-propuesta/SEO.md)); la app nació en registro técnico | Se reescribe el texto de la landing y se afinan tono y tokens de la app (§7) |
| **Ágil: algo mínimo primero, e ir sumando** | Cinco cortes verticales C1–C5 ([02 §6.2](02-alcance-modulos.md)); siete etapas de vistas ([05 §8](05-plan-vistas-v1.md)) | Este plan sustituye a ambos en el orden. C1 pasa a ser «vender desde una caja» en vez de «emitir una boleta» |
| **Minimizar costo de nube** | Ya es la restricción R-12 del DTE | Se mantiene. La emisión se diseña para no añadir NAT ni endpoints de interfaz (§5) |

## 2. De dónde se parte

Lo que existe se aprovecha entero. Lo que no existe se enumera para que el
tamaño del trabajo quede a la vista.

### 2.1 Existe y sirve tal como está

- **Cuenta, empresa, establecimiento, usuario, rol, permiso**, con Row Level
  Security, autorización por `(modulo, accion)` en tres niveles y bitácora de
  solo inserción. El "local" del propietario es la tabla `sucursal`, con su
  código de establecimiento anexo de SUNAT.
- **Series y correlativos** por establecimiento y tipo de documento, con
  `SELECT FOR UPDATE`: la pieza más delicada de la emisión ya está resuelta y
  probada.
- **Tipos de comprobante habilitados por empresa** (V5). Es exactamente donde
  vive «esta empresa emite factura o solo boleta» (§3.1).
- **Registro de empresa con RUC verificado** contra el padrón, con atestación
  firmada, y el módulo `ondexia.consultas` que la produce.
- **Planes y límites** por tabla, no por constantes.
- **Frontend**: dieciséis pantallas de Configuración conectadas a la API,
  tubería de errores RFC 9457, componentes `tabla-datos`, `editor-lineas`,
  `buscador-entidad`, `estado-comprobante`, `confirmacion`, `avisos`, y 61
  pruebas.
- **Infraestructura** en Terraform, endurecida por la auditoría: roles por
  entorno, JWT verificado, MFA en el personal, CSP, CloudTrail.

### 2.2 No existe

| Falta | Dónde debería vivir |
|---|---|
| Producto, precio, existencias | `ondexia.domain/almacen` + migraciones nuevas |
| Cliente | `ondexia.domain/ventas` |
| Caja y sesión de caja | `ondexia.domain/ventas` |
| Documento de venta, detalle, pago | `ondexia.domain/ventas` |
| Comprobante electrónico y su máquina de estados | `ondexia.domain/comprobante` |
| `ondexia.facturacion` | Hoy es un `.gitkeep`. Es el módulo entero: UBL, firma, envío, CDR |
| Consulta de DNI | `ondexia.consultas` solo consulta RUC |
| Pantallas de Ventas y Almacén conectadas | Las cincuenta maquetas de `pages/almacen`, `pages/compras`, `pages/ventas` y `pages/panel` tienen datos fijos |

### 2.3 Lo que la auditoría dejó y bloquea el despliegue

Antes de cualquier `apply`, del informe de validación del 2026-09-07:

1. `verify-full` apunta a un certificado que no está en el repositorio y que el
   driver no leería. La API en AWS no abriría conexiones.
2. La CSP del panel no admite el dominio alojado de Cognito del personal: el
   panel no podría iniciar sesión.
3. El rol de solo lectura de `dev` puede leer el estado de Terraform de `prod`,
   que contiene la contraseña maestra.

Son la iteración 0. No tiene sentido construir ventas sobre una API que no
arranca en la nube.

## 3. Reglas del negocio que este plan fija

### 3.1 Quién se registra y qué puede emitir

El tipo de contribuyente sale del RUC y no se pregunta ([11 §4](11-registro-de-empresa.md)).

| Prefijo | Quién | Puede emitir |
|---|---|---|
| `10` | Persona natural con negocio | Boleta y factura, **salvo** si está en el Nuevo RUS, que solo emite boletas |
| `20` | Persona jurídica | Boleta y factura |
| `15`, `17` | Sucesiones indivisas y sociedades conyugales; entidades públicas y otros | Se rechazan en el alta hasta que haya un caso real |
| `25` | Citado por el propietario | **Por confirmar.** No consta en la lista de prefijos que conozco del padrón. Hasta confirmarlo, el alta lo rechaza con un mensaje claro y la lista de prefijos admitidos es una tabla, no una condición en el código |

**El régimen tributario no viene en la consulta del padrón**, así que el sistema
no puede saber solo si un RUC 10 está en el Nuevo RUS. La regla se resuelve con
lo que ya existe: `tipo_comprobante_empresa`. En el alta de una empresa con RUC
10, la pantalla de Comprobantes pregunta una sola vez «¿Su negocio está en el
Nuevo RUS?» y, si la respuesta es sí, deshabilita la factura y lo dice. La
responsabilidad de la respuesta es del contribuyente, como en cualquier sistema
del mercado; la nuestra es preguntarlo bien y una vez.

`Ruc.esPersonaJuridica()` hoy devuelve `true` solo para `20`. Se sustituye por un
`TipoDeContribuyente` derivado del prefijo, con los cuatro casos de la tabla.

### 3.2 Qué exige cada documento

| Documento | Código | Adquirente | SUNAT | Descarga existencias | Serie |
|---|---|---|---|---|---|
| **Factura** | `01` | RUC obligatorio (catálogo 06, tipo `6`) | Sí, individual, con CDR síncrono | Sí | `F___` por establecimiento |
| **Boleta** | `03` | DNI (tipo `1`) o **sin documento** (tipo `0`). **Obligatorio identificar al adquirente cuando el total supera S/ 700** (Reglamento de Comprobantes de Pago, art. 8, 3.10) | Sí | Sí | `B___` por establecimiento |
| **Nota de venta** | `NV`, interno | Libre, opcional | **No.** No es comprobante de pago | Sí | `N___` por establecimiento |
| **Nota de crédito** | `07` | El del documento que corrige | Sí | Repone si es devolución | `F___` o `B___` según origen |

Tres consecuencias que el código tiene que hacer cumplir, con prueba cada una:

- Una boleta de más de S/ 700 sin documento del adquirente **no se emite**, y el
  mensaje dice el motivo y la cifra.
- Una factura a un cliente sin RUC no se emite. Y un RUC en una factura se valida
  con dígito verificador antes de consultar el padrón.
- La nota de venta **lleva impresa la leyenda «Documento interno, no válido como
  comprobante de pago»** y no se parece a una boleta. Es la distinción más
  importante de toda la interfaz ([05 §9.6](05-plan-vistas-v1.md)), y en el caso
  de la nota de venta también es una protección legal para el cliente y para
  Ondexia.

### 3.3 La nota de venta

No entra al catálogo 01 ni a `comprobante_electronico`. Es un `documento_venta`
con `tipo_documento = 'NV'` y `fiscal = false`, con serie propia y correlativo
propio por establecimiento. Lo que sí comparte con la boleta: cliente opcional,
líneas, totales con IGV desglosado, pago, sesión de caja, descarga de
existencias.

Lo que la hace útil en un punto de venta real es el **canje**: convertir una
nota de venta en boleta o factura sin volver a teclear. Se implementa como
«emitir comprobante a partir de este documento», que copia las líneas y deja la
referencia. La nota de venta original queda marcada como canjeada, nunca borrada.

Los CHECK de `serie_correlativo` y `tipo_comprobante_empresa` (V4, V5) admiten
solo el catálogo 01. Una migración los amplía con `NV` y la letra `N`.

### 3.4 Caja

Una caja pertenece a un establecimiento; un establecimiento tiene una o más. Una
venta siempre ocurre dentro de una **sesión de caja**: alguien la abre con un
monto inicial y la cierra declarando lo que contó. El sistema calcula lo que
debería haber por forma de pago y muestra la diferencia. No corrige nada: la
diferencia queda registrada.

| Regla | Por qué |
|---|---|
| No se vende con la caja cerrada | Sin sesión no hay a quién atribuir el efectivo |
| Una sesión tiene un solo usuario que la abre y uno que la cierra; pueden ser distintos | Turnos y relevos existen |
| Una caja tiene a lo sumo una sesión abierta | Constraint parcial en base, no comprobación en código |
| La serie la decide el establecimiento, no la caja | Es lo que SUNAT espera y lo que ya está construido. Si dos cajas del mismo local emiten a la vez, el `SELECT FOR UPDATE` del correlativo ya lo resuelve. Series por caja es una opción posterior, no un requisito |
| Las formas de pago del primer producto son un catálogo cerrado | Efectivo, tarjeta, transferencia, billetera digital. Una tabla para cuatro filas que no cambian es una tabla de más |

### 3.5 Productos por local

Se interpreta así: **el catálogo es de la empresa; lo que es del local es si el
producto se vende ahí, a qué precio, y cuánto hay.**

| Nivel | Qué vive ahí |
|---|---|
| Empresa | Código, nombre, unidad de medida del catálogo 03 de SUNAT, afectación al IGV, precio de lista |
| Local | Disponible sí o no, precio propio si difiere del de lista, existencias en el almacén principal del local |

La alternativa —un catálogo por local, sin nada compartido— se descarta por dos
razones. La primera es fiscal: la afectación al IGV y la unidad de medida son
atributos del bien, y tenerlos duplicados por local es tener dos versiones que
pueden discrepar en dos comprobantes del mismo RUC. La segunda es operativa: la
misma persona que dirige tres tiendas no quiere crear el mismo producto tres
veces. Si el propietario quiere lo contrario, se discute en §10; el cambio es
una tabla, no una arquitectura.

Las **existencias** se llevan desde el primer producto, con dos matices que lo
hacen mínimo: se controla por producto (`controla_stock` sí o no; un servicio no
tiene existencias), y la venta con existencias insuficientes **se permite y se
avisa** salvo que la empresa lo prohíba en su configuración. Un mostrador no se
detiene por un conteo desfasado; un almacén formal sí quiere que se detenga. El
libro mayor `movimiento_stock` del DTE se construye desde ahora: la proyección
`stock` sin su libro es un número en el que nadie confía.

## 4. Modelo de datos que se añade

Migraciones a partir de V16. Todas con `empresa_id`, todas con
`activar_aislamiento_empresa()`, importes en `NUMERIC(18,6)`. Con V14 vigente,
**toda tabla nueva hereda CRUD para `ondexia_app` por los privilegios por
omisión de V8**; la migración que cree tablas de solo lectura para la API debe
decirlo con `REVOKE`, y `GrantsDeLaAplicacionIT` debe pasar a lista blanca (M2
de la auditoría, pendiente).

### 4.1 Almacén mínimo

| Tabla | Columnas clave | Notas |
|---|---|---|
| `producto` | `empresa_id`, `codigo`, `nombre`, `unidad_sunat` (cat. 03), `afectacion_igv` (cat. 07), `precio_lista`, `controla_stock`, `activo` | UNIQUE(`empresa_id`,`codigo`). Índice GIN en `nombre` |
| `producto_local` | `producto_id`, `sucursal_id`, `disponible`, `precio` (NULL = el de lista) | UNIQUE(`producto_id`,`sucursal_id`). La ausencia de fila significa **no disponible**: un producto nuevo se ofrece en el local donde se creó y en ningún otro hasta que alguien lo diga |
| `movimiento_stock` | `almacen_id`, `producto_id`, `cantidad` (con signo), `tipo`, `documento_tipo`, `documento_id`, `creado_en` | Solo inserción, por disparador |
| `stock` | `almacen_id`, `producto_id`, `cantidad` | Proyección. Se actualiza en la misma transacción que el movimiento |

El almacén del local es el «Almacén Principal» que ya se crea con cada
establecimiento ([05 §4.2](05-plan-vistas-v1.md)). Si el local tiene más de
uno, el mostrador descarga del **activo más antiguo**, que es ese: la regla
está en `AlmacenRepositorio.principalDe` y explica allí por qué no es «el
primero por código».

### 4.2 Caja

| Tabla | Columnas clave | Notas |
|---|---|---|
| `caja` | `empresa_id`, `sucursal_id`, `codigo`, `nombre`, `activa` | UNIQUE(`empresa_id`,`sucursal_id`,`codigo`) |
| `sesion_caja` | `caja_id`, `abierta_por`, `abierta_en`, `monto_inicial`, `cerrada_por`, `cerrada_en`, `declarado` (jsonb por forma de pago), `calculado` (jsonb), `estado` | Índice único parcial `(caja_id) WHERE estado = 'ABIERTA'` |

### 4.3 Ventas

| Tabla | Columnas clave | Notas |
|---|---|---|
| `cliente` | `empresa_id`, `tipo_documento` (cat. 06), `numero_documento`, `nombre`, `direccion`, `correo` | UNIQUE(`empresa_id`,`tipo_documento`,`numero_documento`). El «cliente sin documento» no es una fila: es la ausencia de `cliente_id` en la venta |
| `documento_venta` | `empresa_id`, `sucursal_id`, `sesion_caja_id`, `tipo_documento` (`01`,`03`,`07`,`NV`), `fiscal`, `serie`, `numero`, `cliente_id` NULL, `fecha_emision`, `moneda`, totales por afectación, `total`, `estado`, `documento_origen_id` NULL | UNIQUE(`empresa_id`,`tipo_documento`,`serie`,`numero`). **Inmutable una vez emitido**: disparador que prohíbe UPDATE salvo en `estado` |
| `documento_venta_detalle` | `documento_id`, `producto_id`, `descripcion`, `cantidad`, `valor_unitario`, `precio_unitario`, `afectacion_igv`, `igv`, `total` | La descripción se copia: el producto puede cambiar de nombre y el comprobante no |
| `pago` | `documento_id`, `forma`, `monto`, `referencia` | Varias filas por documento: pago mixto |
| `comprobante_electronico` | `documento_id` UNIQUE, `estado_sunat`, `xml_s3_key`, `cdr_s3_key`, `hash_firma`, `codigo_respuesta`, `descripcion_respuesta`, `intentos`, `enviado_en` | Solo para `fiscal = true`. Estados: `PENDIENTE → FIRMADO → ENVIADO → ACEPTADO | RECHAZADO | OBSERVADO | ERROR_ENVIO`, y `ANULADO` desde `ACEPTADO`. Máquina explícita en el dominio, con prueba por transición prohibida |
| `comunicacion_baja` / `resumen_diario` | Como en el DTE §5.5 | Iteración 6 |

El `token_publico` del portal se añade cuando exista el portal. Una columna
vacía durante meses invita a exponerla antes de tiempo.

## 5. Arquitectura mínima y lo que cuesta

Todo lo desplegado hoy se conserva. Lo nuevo es el Emisor, y la decisión de
diseño es **dónde corre para no pagar salida a internet**.

### 5.1 El problema

La API vive en subred privada sin NAT ([DTE §4.8](DTE-ONX-001_sistema_gestion_comercial.md)).
SUNAT está en internet. Las tres formas de llegar:

| Opción | Costo fijo | Qué implica |
|---|---|---|
| A · Instancia NAT `t4g.nano` con IP fija (la del DTE) | ~7 USD/mes (instancia + dirección IPv4) | Sencilla. La IP fija solo hace falta con un OSE que la exija; SUNAT no la exige |
| B · Emisor **fuera de la VPC**, como ya está `ondexia.consultas`, y la API le habla **por S3** | **0 USD/mes** | La API escribe la orden de emisión en S3 por el endpoint de puerta de enlace, que es gratuito; el evento de S3 invoca al Emisor; el Emisor deja XML, CDR y resultado en S3; ese evento invoca a la API, que actualiza el estado. Sin NAT, sin endpoints de interfaz, sin SQS |
| C · Endpoints de interfaz para SQS y Lambda | ~15 USD/mes | Más caro que la NAT que se quería evitar |

**Se recomienda B.** Cumple la separación del DTE mejor que la versión original:
el Emisor **no tiene acceso a la base**, así que no puede saber reglas de
negocio aunque quiera; recibe un documento ya numerado, lo convierte, lo firma,
lo envía y devuelve un resultado. Y S3 ya es donde el diseño guarda XML y CDR,
así que el «bus» es la misma carpeta donde acaban los documentos. Los fallos van
a un prefijo `errores/` con el motivo, y una alarma de CloudWatch avisa cuando
ese prefijo recibe objetos.

Lo que B pierde frente a una cola: reintentos automáticos con espera. Se
recuperan con un planificador de EventBridge cada cinco minutos que reintenta lo
que lleve más de un intento fallido, dentro del nivel gratuito. Si algún día hay
un OSE que exija IP fija, se pasa a A cambiando el `subnet_ids` del Emisor y
añadiendo la NAT: la arquitectura no cambia, cambia dónde corre.

### 5.2 Emisión propia o por proveedor

La [DTE §7.1](DTE-ONX-001_sistema_gestion_comercial.md) dejó la decisión abierta
y recomendó empezar por proveedor. Dos cosas cambian el cálculo:

1. **La homologación ya no es un trámite.** SUNAT la eliminó para el emisor
   desde sus propios sistemas; queda la prueba voluntaria contra el entorno beta,
   que es exactamente lo que se hace desde la máquina local en la Fase 0.
2. **Existen bibliotecas Java con licencia Apache que generan el UBL 2.1 de
   SUNAT y lo firman y envían**: `xbuilder` y `xsender` del proyecto OpenUBL. No
   ahorran entender la normativa, pero ahorran escribir a mano el XML de cada
   tipo de comprobante, que es donde una persona sola pierde los meses.

**Recomendación: emisión propia, con una puerta de salida.** La iteración 5
empieza con un ensayo acotado a cinco días: emitir una boleta y una factura
contra la beta con esas bibliotecas y obtener el CDR. Si al quinto día no hay
CDR aceptado, se cambia a un proveedor con API REST sin tocar nada fuera de
`ondexia.facturacion`; el resto de la iteración es igual. La decisión queda
escrita con la fecha y el resultado del ensayo.

Lo que la emisión propia cuesta y hay que aceptar: seguir los cambios normativos
de SUNAT, para siempre, y ayudar a cada cliente a obtener y renovar su
certificado digital. Ese certificado lo paga el cliente a una entidad
certificadora; Ondexia no lo revende.

### 5.3 Dónde vive el certificado

El DTE dice Secrets Manager. A 0.40 USD por secreto y mes, con cincuenta
clientes son 20 USD/mes por guardar cincuenta archivos. Alternativa que cuesta
cero y no pierde nada: **el `.pfx` en un bucket privado propio con cifrado en
reposo, al que solo el rol del Emisor puede leer, y la contraseña del `.pfx` en
SSM como `SecureString`**, que es el patrón que `ondexia.consultas` ya usa para
sus claves. El bucket tiene versionado y bloqueo de acceso público, y el rol de
`dev` no lo puede leer, que es lo que el DTE pedía del secreto de producción.

### 5.4 La representación impresa

Generar PDF en la Lambda añade una biblioteca pesada al arranque en frío, que ya
es el riesgo técnico principal. En el primer producto **el PDF lo produce el
navegador**: la pantalla del comprobante tiene una hoja de estilos de impresión
para ticket de 80 mm y para A4, con el QR normativo generado en el cliente, y
`window.print()` produce el PDF en cualquier equipo. El XML firmado y el CDR se
descargan desde S3 con URL prefirmada. El PDF en servidor, el correo al cliente
final y el portal `ver.` son la primera iteración después del primer producto,
cuando haya un cliente que los pida.

### 5.5 Costo mensual

| Momento | Componentes | USD/mes |
|---|---|---|
| **Desarrollo** (iteraciones 0 a 6) | PostgreSQL en Docker, API con `spring-boot:run`, Emisor como proceso local contra la beta de SUNAT, sin nada en AWS | **0** |
| **Primer cliente en producción** (iteración 7) | RDS `db.t4g.micro` ~14 · CloudWatch ~0.5 · Route 53 ~0.6 · S3 ~0.3 · API Gateway ~0.05 · Lambda, Cognito, CloudFront, eventos de S3, EventBridge: nivel gratuito | **~15.5** |
| Cada cliente adicional | Nada fijo. El costo marginal por comprobante es cero | 0 |

Controles que ya existen y se mantienen: presupuesto de AWS Budgets, retención
de logs, concurrencia reservada. Control nuevo: alarma sobre el prefijo
`errores/` del bus.

## 6. Roles y registro

### 6.1 Propietario y Administrador

| Rol visible | Qué es en el modelo | Qué puede |
|---|---|---|
| **Propietario** | `cuenta_administrador`, que ya existe con su invariante «nunca vacía» | Todo lo de la cuenta: suscripción, crear y suspender empresas, locales y cajas, nombrar administradores, crear roles. **Además tiene todos los permisos operativos** en todas las empresas de la cuenta, sin que haya que asignárselos |
| **Administrador** | Rol de sistema `ADMINISTRADOR`, que ya existe | Todo lo operativo de la empresa donde está asignado. No toca la suscripción ni crea empresas |
| Roles personalizados | Rol con `cuenta_id`, duplicado desde Administrador y recortado | Lo que el Propietario o un Administrador decidan, con la regla de no elevación (§6.3) |

Cambios concretos:

- `VENDEDOR`, `ALMACENERO` y `CONTADOR` **salen de la semilla**. Un negocio de
  mostrador no tiene contador dentro del sistema y un rol sembrado que nadie
  pidió es una pregunta en cada alta de usuario. Si más adelante conviene
  ofrecer plantillas, se ofrecen desde la pantalla de roles como sugerencia, no
  como filas del sistema.
- La restricción de «roles personalizados solo en plan intermedio» del
  [04 §2.2](04-organizacion-y-suscripcion.md) se retira. Con dos roles fijos el
  plan base necesita poder crear un «Cajero».
- El Propietario aparece en la lista de usuarios con esa etiqueta, y su fila no
  ofrece «cambiar rol»: no tiene rol, tiene la cuenta.

### 6.2 El registro vuelve, con las puertas que faltaban

La cadena crítica 2 de la auditoría se cerró apagando el autorregistro. El
primer producto lo necesita encendido: nadie va a esperar a que un operador de
Ondexia le cree la cuenta. Encenderlo sin repetir la cadena exige, en el mismo
commit:

| Control | Dónde |
|---|---|
| Cuota de consultas de RUC **por identidad**: cinco por hora por `sub`, contadas en la propia función | `ondexia.consultas`, en memoria por instancia más una tabla mínima en DynamoDB de nivel gratuito, o el equivalente que no cueste |
| Solo consulta quien tiene correo verificado y el token de identidad, no el de acceso | Autorizador de la ruta de consultas |
| La consulta de RUC desde el alta se hace **una vez** y queda en la atestación; el formulario no permite consultar en bucle | SPA y API |
| Alarma cuando la función de consultas supere N invocaciones por hora | `observabilidad.tf` |
| Presupuesto y límite de gasto en Decolecta y apiperu, configurados en los proveedores | Manual, documentado en el README de infra |

La variable `autoservicio_inquilinos` pasa a `true` en los dos entornos cuando
estos cinco estén hechos, y no antes. La prueba de la iteración es intentar el
bucle que la auditoría describió y ver el 429.

### 6.3 La regla de no elevación

La auditoría dejó a medias M1: nadie se cambia su propio rol, pero quien tiene
`usuario:registrar` puede invitar a una segunda identidad suya como
Administrador. Con dos roles por defecto la regla es corta: **un rol solo puede
conceder permisos que su portador tiene.** Un Administrador puede crear un
Cajero; un Cajero con permiso de crear usuarios solo puede crear cajeros. El
Propietario está fuera de la matriz y puede conceder cualquier cosa. La prueba
que falta es la de `invitar`.

## 7. Frontend: de criollismo a formalidad y minimalismo

### 7.1 Diagnóstico

El criollismo vive en la landing, a propósito: «Papelito manda», «Toda la
chamba, en un solo sitio», «No te paltees», «La yapa», «Avísenme». La app nació
en registro técnico y tiene una sola regla de tono escrita: «nunca "Dale,
emite"». Lo que la app sí tiene es **indecisión**: tutea en los errores («Tu
sesión ya no es válida»), trata de usted en «Sin permisos» («Solicite el
permiso»), y saluda con «Buenos días» como título del panel.

Visualmente, la landing es neobrutalista —sombras duras de 4 px, bordes de 2 px,
banderines rotados, fluor y fucsia— y la app usa 179 `rounded-lg`, 62
`rounded-2xl`, 41 `rounded-xl` y cinco niveles de sombra. Ninguna de las dos es
minimalista todavía.

### 7.2 Lo que se decide

**Tono.** Un solo registro en landing y app: **formal, impersonal, preciso**.

| Regla | Ejemplo |
|---|---|
| Las acciones en infinitivo | «Emitir comprobante», «Cerrar caja», «Registrar empresa» |
| Los mensajes sin tuteo ni usted explícito, en forma impersonal | «La sesión ha caducado. Es necesario ingresar de nuevo» en vez de «Tu sesión ya no es válida. Vuelve a ingresar» |
| Ningún peruanismo ni coloquialismo | Se retiran chamba, al toque, paltearse, yapa, «quiero mi acceso», «avísenme» |
| Sin exclamaciones, sin humor, sin saludos | El panel se titula por su contenido, no por la hora del día |
| Los términos de SUNAT, exactos | «Boleta de venta electrónica», «adquirente», «comunicación de baja», «resumen diario» |
| Los errores dicen qué hacer, como ya exige [05 §9.7](05-plan-vistas-v1.md) | «Para emitir factura el cliente debe tener RUC» |

**Forma.** Minimalismo es quitar, no decorar poco.

| Hoy | Se pasa a |
|---|---|
| Cuatro radios distintos | **Un radio, 6 px**, como token `--radius-base`; `rounded-full` solo en insignias y avatares |
| Cinco sombras | **Ninguna en superficies**; una sola (`--shadow-flotante`) para paneles que flotan sobre otros. La separación la hacen el borde de un píxel y el espacio |
| Sombras duras, bordes de 2 px, banderines rotados, `bamboleo`, `.pop`, `.trama` | Se retiran de la landing |
| Fluor y fucsia | Se retiran. La paleta es índigo más grises azulados, con verde, ámbar y rojo solo como semántica de estado |
| «Bricolage Grotesque» pendiente de descargar | Se descarta. **Outfit** en pesos 400, 500 y 600 para todo; el 700 y superiores no se usan. Es la fuente que ya está, con el contraste ya verificado, y con menos pesos se ve más sobria |
| Iconos por doquier | Icono solo cuando sustituye a una palabra que no cabe. En el menú, texto |
| Panel con saludo, banda de avisos, gráfico | Panel del primer producto: **estado de la caja, ventas del día, comprobantes pendientes o rechazados ante SUNAT**. Tres cifras y una lista. El gráfico vuelve cuando haya meses que comparar |

Los tokens viven duplicados en `styles.css` y `global.css`. Se unifican en un
archivo `tokens.css` que ambos importan; es el momento de hacerlo porque van a
cambiar los dos a la vez.

### 7.3 Lo que se construye

- **Un patrón nuevo, P8 · Punto de venta.** No es el editor de documento P3: es
  una pantalla de una sola vista, sin migas, pensada para teclado y para una
  tienda con cola. Buscar producto por código o nombre, sumar líneas, elegir el
  documento (nota de venta, boleta, factura), identificar al cliente si hace
  falta, cobrar, emitir, y volver a empezar. La caja abierta y el local se ven
  siempre en la barra superior, que ya existe con ese propósito.
- **La pantalla de comprobante** para los tres documentos, con estado ante
  SUNAT, hoja de estilos de impresión de ticket y A4, XML y CDR. Es el P4 del
  plan de vistas.
- **Caja**: abrir, ver la sesión en curso, cerrar con arqueo por forma de pago.
- **Producto y cliente**: listado y ficha, con el patrón P1 + P2 que ya está
  resuelto en Configuración.
- **El menú del primer producto tiene tres entradas**: Ventas (punto de venta,
  comprobantes, clientes, caja), Almacén (productos, existencias) y
  Configuración. Compras y los submódulos que esperan **no aparecen**. Las
  cincuenta maquetas no se borran del repositorio: salen de las rutas y quedan
  bajo `pages/_maquetas/` fuera del build hasta su iteración. Un menú con
  entradas que no funcionan es lo contrario de un producto mínimo.

### 7.4 Landing

Se reescribe el texto entero con las reglas de §7.2, se conserva la estructura
(qué hace, cómo se anula, precios, estado, lista de espera) y se quita el
sistema visual neobrutalista. Sigue con cero JavaScript. La CSP de la landing
tiene `form-action 'none'`: el día que el formulario de lista de espera tenga
destino, esa directiva tiene que nombrarlo o el envío no sale del navegador.

## 8. Iteraciones

Ocho iteraciones. Cada una termina con algo que se puede enseñar, con sus
pruebas en verde y con la documentación tocada en el mismo commit. Los tamaños
son órdenes de magnitud para una persona asistida por IA, no compromisos.

| # | Nombre | Tamaño | Qué se puede enseñar al terminar |
|---|---|---|---|
| **0** | Bloqueantes y decisiones | 3–4 días | La API arranca en AWS con `verify-full`; el panel inicia sesión; el estado de prod no lo lee `dev`. Las tres decisiones de §10 que bloquean la iteración 5 están tomadas |
| **1** | Identidad del primer producto | 1 semana | Dos roles por defecto. Regla de no elevación completa. El registro admite RUC 10 y 20 con la pregunta del Nuevo RUS. Al crear la empresa se crean su local principal, su almacén y su primera caja |
| **2** | Locales y cajas | 1 semana | CRUD de cajas por local. Abrir y cerrar caja con arqueo. Asignar un usuario a un local (ya existe) y ver en la barra superior la caja abierta |
| **3** | Productos y clientes | 1–2 semanas | Producto con unidad SUNAT y afectación al IGV, disponible por local, con existencias. Cliente con RUC verificado por el padrón o con DNI. Consulta de DNI añadida a `ondexia.consultas` con el mismo proveedor, si su plan la incluye |
| **4** | Vender | 2 semanas | El punto de venta emite **notas de venta** completas: correlativo, líneas, IGV, pago mixto, descarga de existencias, impresión de ticket y A4. Boleta y factura se registran hasta `PENDIENTE` sin enviar. Cierre de caja con totales por forma de pago. **Aquí el producto ya se puede usar en una tienda que no facture** |
| **5** | Emitir ante SUNAT | 2–3 semanas | Ensayo de cinco días con OpenUBL contra la beta; decisión escrita. `ondexia.facturacion` como Lambda fuera de la VPC con el bus por S3. Boleta y factura llegan a `ACEPTADO` con CDR, XML descargable. Rechazos visibles con su código y descripción de SUNAT |
| **6** | Anular | 1–2 semanas | Nota de crédito desde el comprobante. Comunicación de baja para facturas. Resumen diario para anular boletas, con su ticket y el planificador que lo consulta. Canje de nota de venta a boleta o factura |
| **7** | Salir a producción | 1 semana | Landing y app con el tono y la forma de §7. Certificado real del primer cliente en su bucket, `modo_sunat = PRODUCCION` para esa empresa, primer `apply` de prod con el plan leído, presupuesto y alarmas. Guía de alta del cliente en el README |

Total: **11 a 15 semanas** hasta un primer cliente emitiendo en producción, con
un producto usable sin SUNAT desde la semana 6. Después del primer cliente, en
este orden y una por vez: PDF en servidor y correo al adquirente; portal
`ver.`; catálogos ricos; cotizaciones; Compras; guías de remisión; reportes.

### 8.1 Lo que cada iteración deja en la documentación

| Iteración | Documento |
|---|---|
| 1 | Nota en [04 §2.2](04-organizacion-y-suscripcion.md) y en V2 sobre los roles retirados; [11](11-registro-de-empresa.md) gana la tabla de prefijos y la pregunta del Nuevo RUS |
| 2–4 | Documento **13 · Ventas en punto de venta**: caja, nota de venta, canje, existencias |
| 5 | Documento **14 · Emisión electrónica**: el bus por S3, la decisión propia o proveedor con el resultado del ensayo, la máquina de estados, cómo se prueba contra la beta |
| 7 | [10](10-convenciones-de-interfaz.md) reescrito con las reglas de tono y forma; [06](06-notas-de-version.md) con la versión 1.0 |

## 9. Estándares que este plan hace cumplir

Lo que ya rige y no se negocia está en `CLAUDE.md`. Lo que este plan añade,
porque las ventas y la emisión lo exigen:

**Del dominio**

- Los documentos emitidos son inmutables por disparador, no por costumbre. El
  único campo que cambia es `estado`, y solo por la máquina de estados.
- Cada transición prohibida de `comprobante_electronico` tiene una prueba que la
  intenta y falla.
- Cada regla fiscal de §3.2 tiene una prueba con la cifra o el dato en el
  nombre: `unaBoletaDe701SolesSinDniNoSeEmite`.
- Los totales se calculan una vez, en el dominio, y se comparan contra la suma
  de líneas antes de firmar. El XML que sale lleva los mismos números que la
  base; un test de ida y vuelta lo comprueba.
- `ondexia.api` sigue sin ninguna dependencia de firma XML. ArchUnit ya lo
  vigila; `ondexia.facturacion` tiene el mismo tipo de regla al revés: no
  depende de `ondexia.api` ni conoce JPA.

**De la emisión**

- Idempotencia: la orden de emisión lleva la clave `(empresa, tipo, serie,
  número)` en el nombre del objeto de S3. Emitir dos veces el mismo documento
  sobreescribe la misma orden en vez de enviar dos.
- Los XML se validan contra los XSD de SUNAT **antes** de firmar, en una prueba y
  en tiempo de ejecución. Un rechazo por esquema que se descubre en SUNAT es un
  rechazo que costó una llamada.
- El cuerpo de las respuestas de error de SUNAT o del proveedor se guarda en S3,
  no en el log, por la regla del `CLAUDE.md`: un 401 suele repetir la clave.
- `dev` emite contra la beta y **no puede** leer certificados de producción. Es
  política de IAM sobre el bucket, no convención.

**Del frontend**

- Cada regla de tono de §7.2 es una prueba de la landing y de la app que busca
  las palabras prohibidas en los textos compilados. Un peruanismo que vuelva
  pone el CI en rojo.
- Un radio, una sombra: el CSS compilado no contiene `rounded-xl`, `rounded-2xl`
  ni `shadow-` distinto del token. Prueba en el CI, como las de contraste.
- Los `DATOS_EJEMPLO` de las maquetas no pueden aparecer en un componente que
  esté en las rutas. ArchUnit no llega a TypeScript; una prueba de Karma que
  recorra `app.routes.ts` sí.

**Del proceso**

- Una iteración no se cierra con «sin prueba propia» salvo que el commit diga
  qué falta y por qué, como ya se hace.
- Los commits sin marca de herramienta ni coautor, como fija `CLAUDE.md`.
- Ninguna dependencia nueva de más de mil líneas sin una frase en el pom o en el
  `package.json` que diga para qué está. Es lo que hizo detectable el problema
  de `sharp`.

## 10. Decisiones que necesita el propietario

Las tres primeras bloquean la iteración 5. Las demás se resuelven en su
iteración; hasta entonces el plan asume lo que dice la columna «Si no se
decide».

| # | Pregunta | Recomendación | Si no se decide |
|---|---|---|---|
| 1 | ¿Emisión propia con OpenUBL, o proveedor PSE con API REST? | Propia, con el ensayo de cinco días como puerta de salida (§5.2) | Se hace el ensayo y decide su resultado |
| 2 | ¿Boleta enviada individualmente, o solo por resumen diario? | Individual: CDR inmediato, sin ticket, sin planificador hasta la iteración 6. Se confirma en la beta durante el ensayo | Individual |
| 3 | Emisor fuera de la VPC con bus por S3, o instancia NAT | Bus por S3 (§5.1) | Bus por S3 |
| 4 | Prefijo `25` del RUC: ¿de dónde sale? | Confirmar con un RUC real. Hasta entonces se rechaza con mensaje | Se admiten `10` y `20` |
| 5 | Catálogo por empresa con disponibilidad por local, o catálogo independiente por local | Por empresa (§3.5) | Por empresa |
| 6 | ¿La nota de venta puede emitirse sin cliente y sin límite de importe? | Sí: no es comprobante de pago. Lleva la leyenda y se canjea | Sí |
| 7 | ¿Existencias desde el primer producto, con venta bajo cero permitida y avisada? | Sí, con `controla_stock` por producto y la prohibición como opción de la empresa | Sí |
| 8 | Formas de pago: ¿efectivo, tarjeta, transferencia y billetera digital, como catálogo cerrado? | Sí. Una tabla llega cuando un cliente pida una quinta | Sí |
| 9 | `VENDEDOR`, `ALMACENERO`, `CONTADOR`: ¿se eliminan de la semilla o quedan como plantillas sugeridas? | Se eliminan. Las plantillas vuelven desde la pantalla de roles si alguien las pide | Se eliminan |
| 10 | Tono de la app: impersonal («Es necesario ingresar de nuevo») o usted («Debe ingresar de nuevo») | Impersonal: es más corto y no presupone a quién le habla | Impersonal |

## 10.1 Decisiones tomadas el 2026-09-07

El propietario delegó la decisión con dos condiciones: investigar fuera de los
documentos del proyecto cuando haga falta, y dejar el sustento escrito. Esto es
lo decidido, con la fuente que lo sostiene. Lo que sigue abierto se dice.

| # | Decisión | Sustento |
|---|---|---|
| 1 | **Emisión propia**, con las bibliotecas `xbuilder` y `xsender` del proyecto OpenUBL (`io.github.project-openubl`, Apache 2.0) y el ensayo de cinco días como puerta de salida a un proveedor | La homologación dejó de existir con la RS 287-2017/SUNAT: el emisor desde sus propios sistemas ya no rinde un examen, solo prueba contra la beta. Las bibliotecas existen, están en Maven Central y su última publicación es de junio de 2024 ([xhandler-java](https://github.com/project-openubl/xhandler-java)); que lleven más de un año sin publicar es el riesgo que el ensayo mide, y por eso hay puerta de salida |
| 2 | **Boleta enviada individualmente**, no solo por resumen diario | SUNAT admite ambas vías: individual hasta cinco días calendario desde la emisión, o resumen diario hasta siete ([Boleta de Venta Electrónica, cpe.sunat.gob.pe](https://cpe.sunat.gob.pe/tipos_de_comprobantes/boleta)). Individual da CDR inmediato y evita el ticket y el planificador hasta la iteración 6, donde el resumen entra de todas formas para anular boletas |
| 3 | **Emisor fuera de la VPC con bus por S3** | Es el mismo patrón que `ondexia.consultas` ya usa para salir a internet sin NAT (DT-19), y el endpoint de puerta de enlace de S3 es gratuito. La NAT queda como alternativa escrita en §5.1 para el día que un OSE exija IP fija |
| 4 | **El alta admite RUC `10` y `20`; `15` y `17` se rechazan con mensaje; `25` no existe** | Los prefijos vigentes son `10` (persona natural), `15` y `17` (otros documentos de identidad: sucesiones, entidades, extranjeros) y `20` (persona jurídica) ([registrounicotributario.com](https://www.registrounicotributario.com/tipos-de-ruc-10-15-17-y-20-guia-completa-y-clara/), [Bsale](https://www.bsale.com.pe/article/ruc-10-vs-ruc-20-que-puede-emitir-cada-uno)). El `25` del encargo se toma como errata del `20` o del `15`. La lista de prefijos admitidos es una tabla en el dominio, no una condición: si aparece un caso `15` real, es una fila |
| 5 | **Catálogo por empresa con disponibilidad por local** | §3.5. Sin fuente externa: es diseño, no normativa |
| 6 | **Nota de venta sin cliente y sin tope**, con leyenda y canje | No es comprobante de pago, así que el Reglamento de Comprobantes de Pago no la alcanza. El riesgo que sí existe es que se use para no declarar; la leyenda y el canje son la respuesta del producto, y lo demás es responsabilidad del contribuyente |
| 7 | **Existencias desde el primer producto**, venta bajo cero permitida y avisada | §3.5. Sin fuente externa |
| 8 | **Formas de pago como catálogo cerrado** | §3.4. Sin fuente externa |
| 9 | **`VENDEDOR`, `ALMACENERO` y `CONTADOR` salen de la semilla** | §6.1. Sin fuente externa |
| 10 | **Tono impersonal** | §7.2. Sin fuente externa |

Dos reglas fiscales del plan quedaron confirmadas por el camino: el tope de
**S/ 700** a partir del cual la boleta debe identificar al adquirente sigue
vigente en 2026 (Reglamento de Comprobantes de Pago, art. 8, 3.10;
[tramitesperu.com](https://tramitesperu.com/sunat/boleta-venta-electronica/)), y
**el Nuevo RUS solo emite boletas y tickets**, nunca facturas
([orientacion.sunat.gob.pe](https://orientacion.sunat.gob.pe/3405-que-negocios-y-personas-no-entregan-facturas-pero-si-boletas-de-venta-o-tickets)).

### Lo que la iteración 0 dejó hecho

- **`verify-full` de verdad.** El paquete de CA de RDS entra al repositorio
  (`apps/backend/certificados/`, con excepción en el `.gitignore`), lo empaquetan
  la API y el panel desde ese único sitio, y `CertificadoRaizRds` lo copia a
  disco porque `sslrootcert` no lee el classpath. La API, el panel y las
  migraciones verifican el certificado del servidor. Pruebas:
  `CertificadoRaizRdsTest` y `FuenteDeDatosIamTest` en los dos módulos.
- **La CSP del panel admite el dominio alojado de Cognito del personal**, y las
  cuatro políticas de cabeceras llevan `Permissions-Policy`.
- **El bucket de estado deniega por prefijo**: cada entorno toca solo el suyo,
  por nombre de rol, más una lista de administradores humanos y el usuario raíz.
  El argumento de «no se puede porque los roles no existen» era válido para una
  política por referencia y no para una por nombre.
- **Las pruebas de integración corren sin Docker** contra un PostgreSQL externo
  (`ONDEXIA_PRUEBAS_BD_URL` y compañía), y de paso quedó a la vista que la V8
  depende del orden de creación de roles del clúster (ver `ServidorDePruebas`).

Lo que **no** se pudo verificar aquí: `terraform validate` de la configuración
principal, porque el registro de proveedores no es alcanzable desde este
entorno; lo corre el CI en cada push.

### Lo que la iteración 1 dejó hecho

- **Dos roles por defecto.** La V16 convierte `VENDEDOR`, `ALMACENERO` y
  `CONTADOR` en roles de las cuentas que los usaban y los elimina del sistema;
  solo queda `ADMINISTRADOR`. La cuenta demo (V900) crea los suyos como lo haría
  un cliente. El listado de usuarios marca al Propietario.
- **Regla de no elevación completa.** `Permisos.cubre`: quien invita, reasigna o
  edita un rol no puede conceder permisos que no tiene; el Propietario está fuera
  de la matriz. Pruebas en `UsuariosIT` y `RolesIT`.
- **Tipo de contribuyente por tabla.** `TipoDeContribuyente` reemplaza a
  `esPersonaJuridica()`: `10` y `20` se registran, `15` y `17` se rechazan con
  mensaje, cualquier otro prefijo no existe. La puerta vive en
  `Empresa.registrar`.
- **Régimen tributario.** `empresa.regimen_tributario` (`NUEVO_RUS` u `OTRO`),
  declarado en el alta solo a un RUC 10 y editable en la ficha. La factura no se
  emite ni se puede habilitar en el Nuevo RUS; una persona jurídica no puede
  declararlo, por código y por `CHECK`.
- **El autorregistro vuelve** con tres capas: cuota de cinco consultas de RUC
  por identidad y hora en `ondexia.consultas` (`CuotaPorSolicitante`, 429 con
  `Retry-After`), throttling de la ruta que ya existía, y alarma de CloudWatch
  a partir de trescientas invocaciones por hora. La concurrencia reservada de la
  función queda como variable, en -1 hasta que la cuenta tenga cuota ampliada.

**Diferencias con lo planeado.** La consulta de RUC sigue aceptando el token de
acceso: Cognito no emite tokens a una cuenta sin correo confirmado
(`auto_verified_attributes`), así que exigir el de identidad no añadía nada. El
almacén y la caja al crear la empresa pasan a la iteración 2, donde existe la
caja y donde hace falta un puerto para operar bajo el RLS de la empresa recién
creada. La cuota es por instancia de Lambda, y se dice así en el código.

### Lo que la iteración 2 dejó hecho

- **Cajas y sesiones de caja** (V17): `caja` por establecimiento con código
  único por local; `sesion_caja` con monto inicial, declarado y calculado por
  forma de pago, una sola abierta por caja (índice parcial) y cerrada inmutable
  (disparador). Dominio `Caja`, `SesionCaja`, `FormaDePago`; casos de uso
  `Cajas` y `SesionesDeCaja`; API en `/api/v1/ventas/cajas`; submódulo de
  permisos `ventas.caja` con seis funciones. Todo en
  [13 §1](13-ventas-en-punto-de-venta.md).
- **Lo que nace con el establecimiento.** `DotacionDeEstablecimiento` crea el
  almacén y la primera caja en los tres caminos: registro de cuenta, alta de
  empresa y pantalla de establecimientos. Para los dos primeros hizo falta el
  puerto `OperarComoEmpresa`, que cambia la empresa del aislamiento durante una
  acción y la restaura.
- **Pantalla de cajas** con apertura, cierre con arqueo y diferencia, y la caja
  abierta en la barra superior. El Vendedor de la cuenta demo puede consultar,
  abrir y cerrar; no crear.

**Diferencias con lo planeado.** Ninguna en el modelo. Una en las pruebas: una
empresa registrada no se puede borrar —su alta deja bitácora, y la bitácora es
de solo inserción—, así que `RegistroDeEmpresaIT` dejó de limpiar empresas y usa
un RUC por prueba. El almacén de un local que no es la matriz se llama
`ALM-<código>`; si el cliente ya tenía uno con ese código, se respeta el suyo.

### Lo que la iteración 3 dejó hecho

- **Catálogo de la empresa, disponibilidad del local** (V18): `producto` con
  unidad del catálogo 03 y afectación del 07 como enumerados del dominio,
  `producto_local` donde la ausencia de fila es «no se vende aquí», y el libro
  `movimiento_stock` (solo inserción) con su proyección `stock` sumada de forma
  atómica en la base. El ajuste por conteo anota la diferencia, no la cantidad.
  Todo en [13 §2](13-ventas-en-punto-de-venta.md).
- **Clientes** con el catálogo 06 sin el «sin documento», RUC verificado por la
  misma atestación del alta de empresas o admitido sin verificar y marcado, y
  documento inmutable. [13 §3](13-ventas-en-punto-de-venta.md).
- **Consulta de DNI** en `ondexia.consultas` por los mismos proveedores, cuota
  y throttling que el RUC, y sin atestación (decisión en 13 §3.2). Ruta nueva en
  la pasarela con el mismo autorizador.
- **Pantallas** de productos (listado y ficha con tres pestañas) y clientes
  (listado y ficha con consulta a SUNAT o RENIEC) conectadas; las pestañas de la
  maqueta que no existen en el primer producto se retiraron.
- `ReglaDeNegocioViolada` puede señalar un campo, como ya hacía `Conflicto`.
- **Spring Boot 4.0.8** (Spring Framework 7.0.9, Spring Security 7.0.7). El
  análisis de dependencias de la PR encontró siete CVE de 9.8 en 7.0.8 y dos en
  Security 7.0.6; la 4.0.8 los cierra y la suite entera pasa con ella. La DT-18
  del DTE eligió 4.0.7 por madurez; el criterio se mantiene: se toma el parche,
  no la 4.1.

**Diferencias con lo planeado.** Ninguna en el modelo de §4.1 y §4.3. Dos
decisiones que el plan dejaba abiertas: el DNI se consulta sin atestación, y el
producto exige el establecimiento en el alta (un producto que no se vende en
ningún sitio no es un producto, es un borrador que nadie ve). El índice GIN de
`nombre` exige `pg_trgm`, extensión de confianza que crea el dueño de la base.

### Lo que la iteración 4 dejó hecho

- **El punto de venta emite** (V19): `documento_venta` con líneas, pagos y
  totales, inmutable salvo el estado por disparador; correlativo bajo bloqueo en
  la misma transacción; descarga del almacén del local con aviso o rechazo según
  `empresa.permite_venta_sin_stock`; el arqueo suma los pagos de la sesión.
  Nota de venta `EMITIDO`; boleta y factura `PENDIENTE` sin enviar. Todo en
  [13 §4](13-ventas-en-punto-de-venta.md).
- **Las tres reglas de §3.2 con su prueba**: `unaBoletaDe701SolesSinDniNoSeEmite`
  y `unaFacturaSinRucNoSeEmite` en `DocumentoVentaTest`, y la leyenda de la nota
  de venta en pantalla y en papel.
- **Pantallas**: punto de venta, listados de notas de venta, boletas y facturas,
  y el detalle imprimible en ticket de 80 mm o A4 desde el navegador. La ficha
  de la empresa gana la casilla de venta sin existencias.

**Diferencias con lo planeado.** La aritmética parte del total con IGV y no del
valor sin IGV (13 §4.3): es lo que hace que el cliente pague exactamente
cantidad por precio. El descuento se expresa con IGV, como el precio. El
`documento_venta` lleva `emitido_por` y `emitido_en` además de la fecha, que §4.3
no listaba; y la nota de venta tiene submódulo de permisos propio
(`ventas.nota_venta`) porque un cajero puede emitirla sin poder registrar
comprobantes. **Con esto el producto ya se puede usar en una tienda que no
facture**, que era el hito de la semana 6.

## 11. Riesgos de este plan

| Riesgo | Señal temprana | Qué se hace |
|---|---|---|
| El ensayo con OpenUBL no llega al CDR en cinco días | Día 3 sin XML válido contra el XSD | Proveedor PSE. El resto de la iteración 5 no cambia |
| La beta de SUNAT rechaza la boleta individual | El ensayo lo dice | Resumen diario desde la iteración 5, con el planificador adelantado |
| El arranque en frío de la Lambda del Emisor supera los diez segundos con las bibliotecas de firma | Medición en la iteración 5 | SnapStart en el Emisor, como ya tiene la API; si no basta, Emisor con concurrencia aprovisionada de 1, unos 3 USD/mes |
| El bus por S3 acumula órdenes sin procesar | Alarma sobre `pendientes/` con antigüedad mayor de quince minutos | Planificador de reintento; si el volumen lo pide, SQS con endpoint de interfaz, 7 USD/mes |
| Las cincuenta maquetas tientan a conectarlas «ya que están» | Un componente con `DATOS_EJEMPLO` vuelve a las rutas | La prueba de §9 lo detecta |
| Persona sola, doce semanas, y un cliente que espera | Dos iteraciones seguidas sin nada que enseñar | Se recorta la iteración, no el criterio: antes se saca una versión sin factura que una factura sin pruebas |

## Lo que la iteración 5 dejó hecho

Boleta y factura salen hacia SUNAT. Lo que hay, con su porqué, está en el
[documento 14](14-emision-electronica.md); aquí solo lo que cierra la iteración.

**El bus por S3 (§5.1, opción B) construido tal cual.** `ondexia.facturacion`
existe como cuarto desplegable, fuera de la VPC, disparado por los objetos que
la API deja en `pendientes/`. La API no ve SUNAT y el Emisor no ve la base: la
frontera son dos objetos, `OrdenDeEmision` y `ResultadoDeEmision`, en el
dominio.

**La orden sale al confirmar la venta, no antes.** Si saliera dentro de la
transacción y esta se deshiciera, el Emisor firmaría un comprobante cuyo número
volvió al correlativo, y la siguiente venta lo reutilizaría. Doc 14 §2.1.

**La máquina de estados con lo que cada transición prohíbe.** Un aceptado no se
reenvía; un resultado tardío no pisa lo que pasó después; en cola no se
reintenta hasta pasados diez minutos. Doc 14 §3, y `ComprobanteElectronicoTest`
la recorre entera.

**El certificado en el bucket, no en Secrets Manager (§5.3).** El navegador lo
sube con URL prefirmadas; la API puede escribirlo y confirmar por listado que
llegó, y no puede leerlo. Es la regla de `CLAUDE.md` sobre la firma convertida en
política de IAM, y el doc 14 §4.1 dice explícitamente que esa frontera no la
ejercita ninguna prueba de este repositorio, porque no hay forma de comprobar
IAM sin AWS.

**Sin certificado no se emite, y se comprueba antes del correlativo.** Un número
gastado en una venta que no se registra es un hueco que justificar ante SUNAT.
La nota de venta sigue saliendo.

**El ensayo de cinco días contra la beta sigue pendiente de ejecutarse.** Desde
el entorno donde se construyó esto no hay salida a `e-beta.sunat.gob.pe`. Lo que
sí está verificado —XML, firma válida, sobre SOAP, lectura de las tres
respuestas de SUNAT, la orden completa de principio a fin— y lo que falta están
en la tabla del doc 14 §1, con el procedimiento del ensayo en §6. La decisión 1
sigue siendo revisable hasta entonces, y la puerta de salida al proveedor PSE no
toca nada fuera de `ondexia.facturacion`.

## Lo que la iteración 6 dejó hecho

Anular y canjear, que es lo que faltaba para que el mostrador se pueda
equivocar. El detalle está en [doc 13 §5](13-ventas-en-punto-de-venta.md); aquí
lo que cierra la iteración.

**Sin tabla nueva.** La nota de crédito y el canje son un `documento_venta` con
la referencia a lo que modifican. La V21 añade el motivo del catálogo 09 y el
tipo, serie y número del original, y rehace el disparador de inmutabilidad con
esas columnas dentro: la lista es explícita, así que una columna que no esté en
ella se podría modificar después de emitida sin que nada lo impidiera.

**Anular y emitir una nota de crédito son dos rutas con permisos distintos.** Es
la separación que el catálogo de la V2 hace desde el principio, aplicada por la
anotación y no por una comprobación a mano que alguien tenga que recordar.

**El comprobante se anula cuando SUNAT acepta la nota**, no cuando se registra.
El canje, al revés: la nota de venta se marca `CANJEADO` al emitir, porque ese
estado existe para impedir un segundo canje.

**El arqueo cuenta lo que se movió, no lo que sigue vigente** (doc 13 §5.3). La
consulta de cobros excluía los documentos anulados; con la anulación construida,
eso habría cambiado el calculado de sesiones ya cerradas.

**Un defecto de xbuilder encontrado y acotado.** Su plantilla de nota de crédito
rellena el `ResponseCode` —el motivo del catálogo 09— con el tipo del documento
afectado, y no usa el campo `tipoNota` en ninguna de sus nueve plantillas: no hay
valor del modelo que deje correctos a la vez el motivo y la referencia. Se
corrige sobre el XML generado y antes de firmar, con la prueba que fija los dos
valores, y el método se podrá retirar si una versión posterior lo arregla.

**La comunicación de baja, con el envío asíncrono entero** (doc 13 §6). Es el
otro camino para dejar sin efecto una factura, y afirma algo distinto de la nota
de crédito: que el comprobante no debió existir. SUNAT no responde en el
momento —devuelve un ticket y el veredicto llega después—, así que aparece un
estado que un comprobante nunca tiene, `EN_PROCESO`, y dos viajes al Emisor por
comunicación. La API sincroniza al leer, de modo que quien envía una baja ve el
resultado sin hacer nada más.

**Dos piezas que el plan pedía y no están, cada una con su motivo escrito**
(doc 13 §6.2 y §6.3):

- El **planificador** que consulte los tickets cuando nadie mira. Su diseño está
  escrito; no se construye porque es infraestructura que solo corre en AWS y no
  hay forma de ejercitarla desde este repositorio, sobre un camino que ya
  funciona al consultar.
- El **resumen diario** para anular boletas. La nota de crédito ya lo hace, y
  cuál de los dos mecanismos admite SUNAT para una boleta enviada
  individualmente es justo lo que el ensayo contra la beta tiene que confirmar.
  Construir una segunda integración sobre una primera sin verificar multiplica
  el riesgo. El mecanismo que necesitaría —el envío asíncrono— ya está.

## Lo que la iteración 7 dejó hecho

Salir a producción, que en este plan significa dos cosas distintas: dejar el
producto con una sola cara, y dejar escrito lo que hay que hacer sobre una
cuenta real. La primera se hizo entera; la segunda es una guía, porque no hay
cuenta contra la que ejecutarla desde aquí.

**Un solo registro, formal e impersonal, en la aplicación y en la landing.** No
había dos registros: había indecisión. Los errores tuteaban, «Sin permisos»
trataba de usted, el panel saludaba por la hora del día, y la landing hablaba de
chamba y de no paltearse delante de un sistema que emite documentos con valor
tributario. El detalle está en [doc 10](10-convenciones-de-interfaz.md) §2.

**Un radio y una sombra.** 289 clases de radio pasan a `rounded-base`; cincuenta
y cuatro superficies pierden la suya. Cuatro radios y cinco sombras no eran
cuatro y cinco decisiones de diseño, eran la ausencia de una: cada pantalla
heredó lo de la maqueta de la que salió (doc 10 §5).

**Y lo vigila el CI, no la revisión.** `herramientas/comprobar-tono-y-forma.mjs`
lee la salida compilada de las dos superficies. Sobre el código no funcionaría
—los comentarios explican en español lo que la pantalla decía antes—; sobre
`dist/` lo que queda es lo que el cliente lee, y Tailwind solo emite las
utilidades que se usan, así que la ausencia de un radio en la hoja servida no es
una aproximación a «nadie lo escribió», es la prueba. Se comprobó inyectando un
radio y un peruanismo en la salida: los dos salen por pantalla y el paso
devuelve 1.

**El menú, tres entradas.** Ventas, Almacén y Configuración. Salen Compras
entera, los ocho catálogos de almacén, cotizaciones, preventas, formas de pago,
resumen diario y la suscripción: de treinta y tantas entradas, respondían con
datos reales una docena. Las maquetas no se borran —cada una es el diseño
acordado de su iteración— sino que pasan a `pages/_maquetas/`, fuera de la
compilación. Una prueba nueva recorre cada entrada del menú y exige que su ruta
esté declarada: sin ella, dejar una apuntando a una ruta retirada se descubre
pulsándola.

**El panel, con cifras de la base.** Pintaba siete indicadores, un gráfico y una
banda de avisos, todo escrito a mano en el componente. Quedan tres cifras y una
lista: la caja, lo vendido hoy y lo que sigue sin aceptar ante SUNAT (§7.2). Un
bloque en `null` significa «no te corresponde verlo» y no se pinta; un cero
significa «hoy no se ha vendido» y sí. Colapsarlos le diría a un usuario de
Configuración que la empresa no vende.

**Dos alarmas que faltaban para producción.** Una sobre los 5xx de la pasarela
—hay fallos que el cliente ve y la métrica de la Lambda no registra: un arranque
en frío que agota el tiempo de la integración, un parámetro de SSM que falta— y
otra sobre la duración del Emisor. La segunda importa por el plazo: la alarma de
errores se dispara cuando la función ya reventó, es decir cuando el comprobante
ya se quedó sin resultado.

**La guía de alta del primer cliente**, en el README de `ondexia.infra`: lo que
tiene que ser cierto antes de tocar producción, el `apply` con el plan leído y
qué mirar en él, la subida del certificado real y el paso a `PRODUCCION` desde la
aplicación, la comprobación con un comprobante pequeño, y cómo volver atrás.

**Lo que no se hizo, y por qué.** El `apply` de producción, el certificado real y
el cambio de modo son pasos sobre una cuenta de AWS y sobre comprobantes que
SUNAT recibe: desde aquí no hay ni cuenta ni salida a SUNAT. Por eso la landing
dice «en ensayo» y no «listo», y por eso [doc 14 §1](14-emision-electronica.md)
sigue teniendo una fila en «Pendiente». Decir «listo» antes del primer CDR sería
exactamente la promesa que esa sección de la landing existe para no hacer.

## Registro de cambios

- **v1 (2026-09-07)** — Propuesta inicial sobre las decisiones del propietario del
  mismo día y el estado de `feature/auditoria-2026-09-01`.
- **v1.1 (2026-09-07)** — Notas de cierre de las iteraciones 0, 1 y 2; el doc 13
  empieza con la caja.
- **v1.2 (2026-09-07)** — Cierre de la iteración 3.
- **v1.3 (2026-09-07)** — Cierre de la iteración 4.
- **v1.4 (2026-09-08)** — Cierre de la iteración 5 y documento 14. El almacén del
  local pasa a ser el activo más antiguo (§4.1).
- **v1.5 (2026-09-08)** — Nota de crédito y canje (doc 13 §5).
- **v1.6 (2026-09-08)** — Comunicación de baja y el envío asíncrono con ticket
  (doc 13 §6). El planificador y el resumen diario quedan pendientes, con su
  motivo.
- **v1.7 (2026-09-08)** — Cierre de la iteración 7: tono y forma en las dos
  superficies con su comprobación en el CI, menú de tres entradas, panel con
  cifras reales, dos alarmas de producción y la guía de alta del primer cliente.
