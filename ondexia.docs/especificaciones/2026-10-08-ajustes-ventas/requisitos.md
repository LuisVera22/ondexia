# Ajustes de ventas: requisitos y ejemplos de negocio

Versión 1.0 · 2026-10-08 · Estado: implementado y verificado localmente por el implementador; auditoría independiente pendiente. Luis autorizó implementar todo lo definido en este chat.

Base inspeccionada: `/workspace/ondexia`, rama `develop`, commit `6cd2f226ece51a871a14261c9a598c38546ba0af`. Existen cambios previos de lineamientos en CLAUDE.md y documento 03; no forman parte de estas tres observaciones.

Fuentes: tres observaciones del socio transmitidas por Luis en este chat y sus capturas. Luis confirmó que son las tres y solicitó aterrizarlas mediante SDD. El ZIP de auditoría enviado por error fue excluido por instrucción expresa. No se incorporan sus contenidos ni instrucciones.

Contrato leído: [CLAUDE.md](../../../CLAUDE.md). Decisiones contrastadas: [plan del producto, §3.2–3.3](../../12-plan-primer-producto.md), [ventas, §3–5](../../13-ventas-en-punto-de-venta.md) y [emisión electrónica, §1, §5 y §7](../../14-emision-electronica.md). Esas fuentes vigentes se distinguen de las propuestas nuevas.

## Problema, valor y alcance

El vendedor necesita registrar a un cliente durante la venta sin abandonar lo preparado, entregar una nota de venta con presentación comprensible y emitir el tipo de documento correspondiente al módulo elegido. Son tres cambios relacionados por el flujo de ventas, con criterios de aceptación separados.

Valor esperado: menos interrupciones del cobro, menor confusión sobre la nota interna y menos selección accidental de otro documento. Es una hipótesis sustentada por las observaciones, sin tiempo ahorrado, frecuencia, ingresos ni rentabilidad medidos. No se inventa retorno financiero para decidir este ajuste. Tras una prueba de uso se puede medir abandono del formulario, pasos para dar de alta y errores de tipo, sin registrar datos personales innecesarios.

Alternativa actual: alta de cliente en otro módulo; nota con desglose y leyenda superior; emisión compartida desde todos los listados. Propuesta: reutilizar servicios y reglas existentes con acceso contextual, presentación diferenciada y tipo fijo por módulo. No se propone un nuevo proveedor ni una nueva plataforma de emisión.

Actores conocidos: vendedor/cajero y adquirente. Luis decide el alcance y las reglas nuevas. No se atribuyen aprobaciones a su socio ni a clientes adicionales. Soporte deberá poder explicar diferencia entre nota interna y comprobante fiscal.

Incluye nota de venta NV, boleta y factura. «Nota» en la primera observación se interpreta provisionalmente como nota de venta; notas de crédito/débito no se amplían sin una necesidad adicional. No incluye cambios de tributación, migraciones de documentos emitidos, canje nuevo, permisos nuevos ni despliegue.

## Decisiones confirmadas por Luis

| ID | Decisión | Propuesta para revisar | Efecto |
|---|---|---|---|
| DEC-01 | Revisar antes de guardar | Mostrar razón social/domicilio y acción «Registrar y usar», luego selección automática | Confirmado por Luis, respuesta en este chat del 2026-10-08 |
| DEC-02 | Cambiar solo presentación | «Importe de venta» y «Total», ambos 450 en el ejemplo; al pie «Documento interno de venta. No constituye comprobante de pago ni se envía a SUNAT». Conservar cálculos internos y canje | Confirmado por Luis, respuesta en este chat del 2026-10-08 |
| DEC-03 | Mantener mostrador general mixto | Fijar tipo solamente al iniciar desde cada módulo; selector en mostrador general según permisos/régimen | Confirmado por Luis, respuesta en este chat del 2026-10-08 |

Luis respondió explícitamente las tres preguntas en este chat el 2026-10-08. Esa confirmación fija las decisiones anteriores; no equivale a ejecución de pruebas ni aprobación de detalles complementarios que no fueron preguntados.

La propuesta «Op. gravada =450» no equivale a la base gravada sin IGV actual. No se adopta silenciosamente como cambio fiscal. El documento 12 exige desglose y leyenda literal; la nueva decisión DEC-02 queda registrada y se actualizarán documentos 12/13 junto al cambio. No se ha verificado normativa tributaria externa nueva.

## Requisitos propuestos

| ID | Fuente y comportamiento | Aceptación | Estado |
|---|---|---|---|
| REQ-01 | Observación 1: ofrecer «Nuevo cliente» junto al selector en emisión de NV/boleta/factura | Alta contextual sin navegar fuera, conservar líneas, cantidades, descuentos, pagos, observaciones y tipo; seleccionar al cliente tras alta correcta | DEC-01 confirmada |
| REQ-02 | Reutilizar consulta de RUC y registro existentes | Validar número, conservar atestación del mismo RUC; razón social/domicilio verificados provienen de atestación; no inventar datos ante error ni emitir la venta por crear cliente | Propuesto; flujo confirmado DEC-01 |
| REQ-03 | Evitar duplicación y efectos colaterales del alta contextual | RUC existente activo se puede seleccionar sin crear otra fila; duplicado concurrente se resuelve consultando el cliente propio o mostrando conflicto; inactivo no se reactiva ni selecciona silenciosamente | Propuesto |
| REQ-04 | Observación 2: NV sin IGV visible y leyenda al final | Aplicar en detalle y papel, ticket 80 mm y A4, ocultando marco de navegación al imprimir; no mostrar fila IGV ni aviso «Incluye IGV» al preparar NV; importe total pagadero del ejemplo 450 | DEC-02 confirmada |
| REQ-05 | Preservar distinción entre nota interna y comprobante fiscal | Mantener título «NOTA DE VENTA» y serie NV; leyenda legible al pie, con texto acordado; no declarar la nota aceptada por SUNAT ni comprobante fiscal | DEC-02 confirmada |
| REQ-06 | Observación 3: nueva emisión desde listado específico con tipo fijo | Notas→NV, boletas→BOLETA, facturas→FACTURA; tipo visible y sin selector de otro tipo; serie, reglas de cliente y llamada de emisión corresponden al módulo | Propuesto |
| REQ-07 | Acceso directo y falta de habilitación no cambian el tipo de manera implícita | URLs de nueva nota/boleta/factura respetan contexto; sin permiso o factura deshabilitada se bloquea con motivo; no se sustituye por NV/boleta; «Nueva venta» contextual conserva tipo cuando corresponda | Propuesto |
| REQ-08 | Mostrador general y canje siguen reglas explícitas | El mostrador general conserva el selector conforme a DEC-03; fijación del formulario no elimina el canje autorizado de NV a boleta/factura | DEC-03 confirmada / canje vigente |

Alcance visual de NV confirmado en DEC-02: resumen sin desgloses tributarios, basado en total autoritativo del documento. Mixtos gravados/exonerados/inafectos y descuentos no se reclasifican a «gravado» ni se vuelven a descontar. Detalle de líneas y observaciones se conserva. DEC-02 confirma etiqueta, importe total y conservación del cálculo interno. La presentación de mixtos y descuentos se implementó sin reclasificación ni doble descuento y con fixture independiente.

## Invariantes y requisitos no funcionales

- Permisos actuales `ventas.cliente:consultar/registrar`, `ventas.nota_venta:registrar` y `ventas.comprobante:emitir` siguen siendo efectivos en servidor. Abrir el formulario no concede permisos ni amplía el régimen de la empresa.
- Aislamiento por empresa/RLS y unicidad por empresa/tipo/número; no revelar clientes ajenos. Un cambio de empresa/local invalida respuestas tardías del alta/consulta y no mezcla la venta anterior con el contexto nuevo.
- Cancelar el alta antes de guardar no crea cliente ni vacía la venta. Si el cliente ya quedó registrado y luego se cancela la venta, no borrarlo: son operaciones distintas; explicarlo en el flujo.
- Sin proveedor, no inventar resultado ni registrar automáticamente datos incompletos. Como alternativa manual propuesta, reutilizar el alta sin verificación vigente; factura sigue sus controles del servidor. No cambiar ese contrato por omisión.
- Evitar doble envío por doble pulsación y respuestas de consulta para un RUC anterior; errores fijos y sin datos sensibles del proveedor.
- Dinero exacto NUMERIC(18,6)/BigDecimal; no introducir cálculos con double/float ni aritmética monetaria binaria en trabajo nuevo. Los importes mostrados deben venir del documento autoritativo, sin recálculo de impuesto en navegador.
- Conservar precios del servidor, totales/pagos, existencias, sesión abierta, inmutabilidad, correlativos y separación fiscal. BIEN/SERVICIO permanece con sus reglas aprobadas.
- Operación accesible por teclado, campos rotulados, estado de consulta/guardado y errores visibles; devolución de foco al selector de cliente al cerrar el formulario contextual.

## Ejemplos BDD propuestos y oráculos

| ID | Requisito | Dado / cuando / entonces | Fuente del esperado |
|---|---|---|---|
| ESC-01 | REQ-01/02 | Venta preparada con dos líneas y pago; alta RUC correcto con datos de consulta simulada identificados; al terminar alta, se selecciona ese cliente y los datos preparados siguen iguales | Datos de prueba fijados antes de ejecutar, no salida observada |
| ESC-02 | REQ-01 | Venta preparada; se cancela antes de registrar; no hay POST de alta ni cambio de venta | Regla propuesta de cancelación |
| ESC-03 | REQ-02 | Consulta pendiente para RUC A; se cambia a B; respuesta A no registra B ni aporta atestación para B | Contrato vigente de atestación |
| ESC-04 | REQ-02/03 | Consulta falla o RUC ya existe; no crear fila ficticia/duplicada; avisar y permitir recuperar selección del cliente propio activo | Contrato vigente y propuesta de no duplicación |
| ESC-05 | REQ-01/03 | Usuario sin registrar clientes o cliente de otra empresa; no alta/selección indebida incluso por llamada directa | Permisos/RLS vigentes |
| ESC-06 | REQ-04/05 | NV de 3 unidades a 150.00, sin descuento; detalle/ticket/A4 muestran importe y total 450.00, sin fila IGV y leyenda solo al pie | Ejemplo literal del socio; textos confirmados en DEC-02 |
| ESC-07 | REQ-04 | NV con descuentos y afectaciones mixtas; resumen muestra total autoritativo sin restar descuento de nuevo ni declarar todas las líneas gravadas | Regla propuesta de presentación; fixture explícito pendiente |
| ESC-08 | REQ-04/08 | Factura o boleta con mismos 3×150.00 gravados; conservar base 381.36, IGV 68.64 y total 450.00 | Aritmética vigente: 450/1.18 redondeado, diferencia; esperado independiente |
| ESC-09 | REQ-06/07 | Entrada desde nueva nota/boleta/factura o URL directa; tipo permanece NV/BOLETA/FACTURA respectivamente, sin alternativa, y usa su serie y endpoint | Observación 3 y API vigente |
| ESC-10 | REQ-07 | Factura deshabilitada o permiso revocado; nueva factura no permite emitir ni cambia a otro tipo | Régimen y permisos vigentes |
| ESC-11 | REQ-08 | Mostrador general y canje; conservan comportamiento acordado para el general y canje válido sin duplicar cobros/stock | DEC-03 y doc 13 §5.1 |

Los escenarios se vinculan a la [evidencia de implementación](../../evidencias/2026-10-08-ajustes-ventas.md); sus límites y simulaciones se declaran allí. ESC-08 asume una línea gravada de cantidad tres; no extrapolar el mismo redondeo a tres líneas independientes. El fixture mixto usa base 100, IGV 18, exonerado 50 e inafecto 32: total 200. El descuento 10 ya está aplicado; no se resta de nuevo.

## Criterios de preparación y salida

Antes de codificar: DEC-01/02/03 ya resueltas; presentar el [plan](tareas.md) y contrastar cualquier nueva decisión de negocio que surja. Luis amplió la autorización a implementar todo lo definido. Para entregar: evidencia roja/verde por corrección, mutaciones aisladas para invariantes vigentes, revisión visual de los dos formatos, contratos sincronizados si cambian APIs y auditoría independiente. No se cuentan pruebas históricas como validación de estos ajustes.
