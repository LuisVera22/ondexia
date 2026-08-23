# Ondexia — Registro de empresa y validación del RUC

Estado: **definido** · Fecha: 2026-08-23 · Deriva de [DTE-ONX-001](DTE-ONX-001_sistema_gestion_comercial.md) DT-19 y de [04 · Organización y suscripción](04-organizacion-y-suscripcion.md) §2.2

Define **qué campos tiene una empresa**, cuáles se autocompletan desde SUNAT y
cuáles no se pueden tocar. El *dónde* vive la consulta ya está decidido en
DT-19: la unidad `ondexia.consultas`, fuera de la VPC.

---

## 1. Qué da SUNAT de verdad

Antes de decidir campos hay que saber qué se puede llenar. Los tres proveedores
de la cascada (DT-19) no devuelven lo mismo, y **el respaldo devuelve menos que
el principal**:

| | Decolecta `/ruc` | Decolecta `/ruc/full` | apiperu.dev | Padrón en S3 |
|---|---|---|---|---|
| razón social | sí | sí | sí | sí |
| estado / condición | sí | sí | sí | sí |
| dirección | sí | sí | sí | sí |
| **ubigeo** | sí | sí | **no** | sí |
| distrito / provincia / depto. | sí | sí | solo depto. | sí |
| agente de retención / buen contribuyente | sí | sí | sí | no |
| `locales_anexos` | sí | sí | endpoint aparte | no |
| `tipo` (S.A.C., S.R.L., E.I.R.L.) | no | sí | no | no |
| fecha de inscripción | **no** | **no** | **no** | no |

Dos consecuencias que no son opinables:

**El ubigeo se cae en el relevo.** apiperu.dev no lo devuelve, y el ubigeo va en
el comprobante: sin él SUNAT rechaza. Así que el relevo no es «un proveedor u
otro»: cuando responde el respaldo, el ubigeo se toma del padrón. Es la razón
por la que el padrón no es solo la última capa, también rellena huecos de la
segunda.

**La fecha de constitución no existe.** Ningún proveedor la da porque SUNAT no
la publica — publica *fecha de inscripción*, que es cuándo se sacó el RUC, y ni
eso aparece en los contratos de la cascada. El campo queda descartado.

## 2. La cascada

**Decolecta manda · apiperu.dev releva · el padrón atrapa.**

| Proveedor | Papel | Por qué |
|---|---|---|
| Decolecta | Principal | Es el más completo: ubigeo, distrito, anexos y `/full` |
| apiperu.dev | Respaldo | Capa gratuita permanente (100/mes, renovable) y devuelve `retryable` |
| Padrón reducido en S3 | Última | El único que responde cuando SUNAT no está en pie |

`retryable` de apiperu.dev merece subir al puerto como concepto del dominio, no
quedarse en su adaptador. Sin él, quien orquesta la cascada tiene que **adivinar**
por el código HTTP si conviene reintentar o pasar al siguiente, y adivinar mal
significa o castigar con una espera inútil a quien se está registrando, o
rendirse ante un fallo pasajero.

MIGO queda fuera: su capa «gratuita» son 7 días y 700 consultas —un respaldo que
caduca no está el día que hace falta— y su plan de pago cuesta el triple del
equivalente. Se anota como candidato si algún día se necesita la dirección
completa en una sola llamada.

## 3. Bloque 1 — Autocompletados y no editables

| Campo | Fuente | Por qué se bloquea |
|---|---|---|
| `ruc` | lo teclea la persona | Es la identidad. Único global (V1) |
| `razon_social` | SUNAT | Va en cada comprobante. Si no coincide, **rechazo** |
| `domicilio_fiscal` | SUNAT | Idem |
| `ubigeo` | SUNAT o padrón | Idem. Ya se valida como `Ubigeo` |
| `distrito`, `provincia`, `departamento` | SUNAT | Coherencia con el ubigeo |
| `estado` | SUNAT | **Puerta del registro** |
| `condicion` | SUNAT | **La otra puerta** |
| `es_agente_retencion` | SUNAT | Cambia el cálculo de las compras |
| `es_buen_contribuyente` | SUNAT | Afecta plazos |
| `tipo_societario` | Decolecta `/full` | Es el «tipo de empresa» que el borrador pedía elegir a mano |

### 3.1 Bloqueado no es congelado

Una razón social cambia legítimamente en SUNAT. Si se bloquea y **no hay forma
de refrescarla**, un dato corregible se ha vuelto imposible de corregir — peor
que dejarlo editable, porque el rechazo de SUNAT no tendría salida desde la
aplicación.

La regla es: **no editable por la persona, actualizable volviendo a consultar.**
Un «Actualizar desde SUNAT» y nada más.

### 3.2 Sin fecha, `estado` y `condicion` mienten

Son una foto del instante en que se consultó. Guardarlos sin marca de tiempo
hace que la base afirme «ACTIVO» sobre algo comprobado hace ocho meses, y esa
afirmación se usaría para decidir.

Va **`verificado_en`** junto a los dos. Con la fecha al lado, la vista puede
decir «ACTIVO · comprobado el 23/08» y eso ya es honesto. El padrón diario en S3
permite refrescarlo de todas las empresas sin gastar una sola consulta de
proveedor.

## 4. Bloque 2 — Derivados, que no se preguntan

**Persona natural o jurídica** sale de los dos primeros dígitos del RUC y ya está
implementado: `Ruc.esPersonaJuridica()`. No hay ningún caso en que la persona
sepa esto mejor que el número, así que preguntarlo solo añade una forma de
equivocarse.

No se persiste: se deriva al leer. Un dato derivado guardado es un dato que
puede contradecir su origen.

## 5. Bloque 3 — Nuestros, y editables

| Campo | Nota |
|---|---|
| `nombre_comercial` | SUNAT tiene uno, pero las empresas usan el suyo. Se prellena y se deja tocar |
| `correo`, `telefono`, `sitio_web` | Aparecen en el PDF. No son de SUNAT |
| `logo` | Va en el PDF. Ya existe como identidad visual (V7) |
| `modo_sunat` | Ya existe y ya está protegido: exige certificado |

## 6. Bloque 4 — Cuentas bancarias

No son columnas de `empresa`: una empresa tiene varias y en cuanto hay dos ya no
caben. Tabla propia.

| Campo | Nota |
|---|---|
| `banco` | Catálogo cerrado, no texto libre |
| `tipo` | Corriente, ahorros |
| `moneda` | PEN, USD |
| `numero` | Formato según banco |
| `cci` | 20 dígitos. Es el que el cliente necesita para transferir |
| `alias` | Para distinguirlas en un desplegable |
| `principal` | La que sale por defecto en el comprobante |
| `activa` | Nunca se borra: puede estar en comprobantes ya emitidos |

Sin `titular`: siempre es la razón social. Un campo que solo puede tener un valor
es un campo donde alguien escribirá otro.

### 6.1 La cuenta de detracciones va aparte

`empresa.cuenta_detracciones`, una sola columna opcional, **no** una fila de
`cuenta_bancaria` ni un valor de su enumerado `tipo`.

No es *una de las cuentas de la empresa*, es **la** cuenta: una, siempre del
Banco de la Nación, siempre en soles, y no la elige la empresa. Como fila, tres
de sus columnas serían constantes y —lo que de verdad importa— cada consulta que
liste cuentas para cobrar tendría que acordarse de excluirla. Ese filtro se
olvida una vez y el resultado es una cuenta de detracciones ofrecida como
destino de pago en una factura. Como columna aparte no puede ocurrir: no está en
la lista de la que se elige.

**Sin longitud fija.** No se pudo confirmar el formato del número del BN: ni la
web del banco ni la orientación de SUNAT publican cuántos dígitos tiene. Se
valida que sean dígitos y nada más. Un largo inventado rechazaría cuentas
válidas, y eso es peor que no comprobar.

Todavía no se usa —el SPOT no está construido—, y se guarda igual: la columna
cuesta una migración hoy, y añadirla después cuesta la migración **más**
perseguir a los clientes para que la escriban. El riesgo asumido es el de un
campo que no aparece en ninguna vista y por tanto se queda vacío.

## 7. El orden del formulario

**El RUC es el primer campo.** En el borrador estaba quinto, y ahí la consulta se
dispararía después de que la persona hubiera rellenado cuatro campos, para
sobrescribirlos. Primero el RUC, se consulta, y el resto aparece ya relleno y en
modo lectura.

Lo que se retira del borrador:

| Campo | Motivo |
|---|---|
| Fecha de constitución | SUNAT no la publica (§1) |
| Tipo de empresa (selector) | Pasa a derivado (§4) y a autocompletado (§3) |

## 8. Lo que gatea el registro

Se exige **ACTIVO** y **HABIDO**. Cualquier otra combinación no registra.

Y esto no contradice la regla I-03 del DTE («nunca bloquear una venta por una
consulta de padrón»), porque son momentos distintos: **una venta no puede
esperar; un registro sí.** Si el proveedor no responde, el alta se detiene con un
mensaje que no culpe a quien lo intenta. La alternativa —admitir la empresa como
«pendiente de verificación»— crea una empresa que puede emitir sin haber pasado
la puerta, y esa puerta es el motivo de todo esto.

## 9. Cuántas empresas

El mecanismo ya existe: `plan.max_empresas` (V9) con `NULL` = sin límite, y
`cuenta.limite_empresas` para lo pactado con un cliente concreto. Sembrado hoy
con **1 / 2 / sin límite**, que es lo que dice el doc 04 §2.2.

Cambiar esas cifras es un `UPDATE` de tres filas, no una migración. Queda
**pendiente de confirmar** antes de que el botón «Agregar empresa» salga a
producción.

## 10. Abierto

| Qué | Estado |
|---|---|
| `locales_anexos` para autocompletar establecimientos | Aplazado. Viene gratis en la respuesta de Decolecta y resolvería el código de establecimiento incorrecto que teme el doc 04 §2.1. No es requisito del alta |
| Confirmar las cifras de `plan.max_empresas` | Pendiente (§9) |
| Formato del número de cuenta de detracciones | Sin confirmar (§6.1) |
| Si la capa gratuita de apiperu.dev incluye tipo de cambio | Sin confirmar: su ficha no lista «acceso a todas las APIs». El RUC sí está incluido |
