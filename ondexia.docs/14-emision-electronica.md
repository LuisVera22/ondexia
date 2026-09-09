# 14 · Emisión electrónica

Cómo una boleta o una factura pasa de estar registrada en el mostrador a estar
aceptada por SUNAT, y qué hace falta para que eso ocurra. Es lo que la
iteración 5 del [plan del primer producto](12-plan-primer-producto.md#8-plan-por-iteraciones)
deja construido.

Lo que no está aquí: la nota de venta, que no se declara y vive en el
[documento 13](13-ventas-en-punto-de-venta.md); la anulación —nota de crédito,
comunicación de baja, resumen diario—, que es de la iteración 6.

## 1. La decisión: emisión propia

[Doc 12 §10.1](12-plan-primer-producto.md#101-decisiones-tomadas-el-2026-09-07),
decisión 1: se emite desde nuestros propios sistemas con las bibliotecas
`xbuilder` de OpenUBL, y no a través de un proveedor PSE con API REST. El
sustento está allí: la homologación dejó de existir con la RS 287-2017/SUNAT, y
las bibliotecas existen, están en Maven Central y tienen licencia Apache 2.0.

**El ensayo de cinco días contra la beta sigue pendiente de ejecutarse, y hay que
decirlo claro.** El plan lo puso como puerta de salida a un proveedor: emitir una
boleta y una factura contra la beta y obtener el CDR. Desde el entorno donde se
construyó esta iteración no hay salida a `e-beta.sunat.gob.pe` —una petición
muere sin respuesta—, así que lo que se pudo verificar es todo salvo el último
salto:

| Qué | Cómo se verifica | Estado |
|---|---|---|
| El XML sale con la estructura UBL 2.1 y los importes del dominio | `ConstructorDeComprobanteTest` | Verificado |
| La firma es una XML-DSig válida, en `ext:ExtensionContent`, con su `DigestValue` | `FirmadorDeComprobanteTest`, validada con el verificador del propio JDK | Verificado |
| El sobre SOAP de `sendBill` lleva el `UsernameToken` y el zip en base64 | `LectorDeRespuestaSunatTest.sobre` | Verificado |
| Las tres respuestas de SUNAT se leen: CDR, fallo SOAP, y lo que no es ninguna de las dos | `LectorDeRespuestaSunatTest` | Verificado |
| La orden completa deja XML, CDR y resultado en el bus | `ProcesadorDeOrdenesTest` con una SUNAT fingida | Verificado |
| **Que SUNAT acepte de verdad el XML que producimos** | Un envío real a la beta | **Pendiente** |

Lo que falta se ejecuta desde una máquina con salida a internet siguiendo §6, y
el resultado se anota aquí con la fecha. Hasta entonces, la decisión 1 sigue
siendo revisable: si la beta rechazara el XML por algo estructural, la puerta de
salida al proveedor PSE está abierta y **no toca nada fuera de
`ondexia.facturacion`** —esa es la razón de que el Emisor sea un desplegable
aparte con una frontera de dos objetos (`OrdenDeEmision` y `ResultadoDeEmision`).

Un rechazo de SUNAT por un dato del comprobante —un RUC que no está activo, una
serie que no corresponde— no es motivo para cambiar de camino: eso pasa con
cualquier proveedor y lo que hay que ver es el código y la descripción, que la
pantalla muestra.

## 2. El bus: cómo se hablan la API y el Emisor

La Lambda de la API está en subred privada sin NAT y SUNAT está en internet.
En vez de pagar la NAT (32 USD/mes, [DTE §4.8](DTE-ONX-001_sistema_gestion_comercial.md)),
el Emisor vive **fuera de la VPC** —como `ondexia.consultas`— y los dos se
hablan por un bucket de S3, que la API alcanza por el endpoint de puerta de
enlace, gratuito. Es la opción B de [doc 12 §5.1](12-plan-primer-producto.md#51-el-problema).

```
  punto de venta ──emite boleta──▶ API
                                    │ (al confirmar la transacción)
                                    ▼
                 pendientes/{empresa}/{orden}.json
                                    │ evento de S3
                                    ▼
                                 Emisor ──sendBill──▶ SUNAT
                                    │                   │
                                    │◀────── CDR ───────┘
                                    ▼
             documentos/{ruc}/…xml y R-…zip
             resultados/{empresa}/{orden}.json
                                    │
                 la pantalla pregunta ──▶ API lee el resultado y lo aplica
```

Los prefijos están en `ClavesDelBus`, en el dominio, y no repartidos entre los
dos módulos: si la API escribiera en un prefijo y el evento escuchara otro, nada
fallaría y nada se emitiría.

| Prefijo | Escribe | Lee |
|---|---|---|
| `pendientes/` | API | Emisor (y borra al terminar) |
| `resultados/` | Emisor | API |
| `documentos/` | Emisor | API (para firmar la URL de descarga) |
| `errores/` | Emisor | nadie: es para mirar cuando algo va mal |
| `certificados/` | el navegador, con URL prefirmada de la API | **solo el Emisor** |
| `credenciales/` | el navegador, con URL prefirmada de la API | **solo el Emisor** |

### 2.1 La orden sale después de confirmar, no antes

`EmisionElectronica.encolar` no publica: registra la publicación en el
`afterCommit` de la transacción de la venta. El motivo es concreto: si la orden
saliera dentro de la transacción y esta se deshiciera —un pago que no cuadra, la
base que falla—, el Emisor ya la tendría y firmaría un comprobante cuyo número
volvió al correlativo. La siguiente venta reutilizaría ese número, SUNAT la
rechazaría por duplicada, y la primera estaría aceptada para una venta que no
existió.

El precio: si la publicación falla justo después de confirmar, el comprobante
queda `EN_COLA` sin orden. No se pierde —pasados diez minutos se puede
reintentar, §3— pero se acepta a sabiendas.

### 2.2 El resultado se lee, no se recibe

La API no se entera del resultado por un evento. Lo busca cuando alguien
pregunta por el comprobante: `GET /api/v1/ventas/comprobantes/{id}/sunat`, que
mientras está en cola mira el bus y aplica lo que encuentre. La pantalla repite
esa consulta cada cuatro segundos mientras espera.

Hubo un diseño con una segunda Lambda de la API suscrita a `resultados/`. Se
retiró: obligaba a levantar el contexto de Spring de la API en un handler sin
HTTP —sin forma de ejercitarlo desde las pruebas de este repositorio— para ganar
unos segundos que la pantalla ya cubre preguntando. Cuando la iteración 6 traiga
el planificador del resumen diario, esa misma tarea sincronizará lo que quede en
cola sin que nadie mire.

## 3. La máquina de estados

El estado ante SUNAT es **distinto** del estado del documento de venta. El
documento nace `PENDIENTE` y solo pasa a `EMITIDO` cuando SUNAT lo acepta; entre
medias, el comprobante electrónico puede haber ido y vuelto varias veces.

```
  EN_COLA ──resultado──▶ ACEPTADO          (final)
     ▲    ──resultado──▶ RECHAZADO ──┐
     │    ──resultado──▶ ERROR_ENVIO ─┤
     └───────── reintentar ◀──────────┘
```

| Estado | Qué pasó | Qué se puede hacer |
|---|---|---|
| `EN_COLA` | La orden está en el bus o a punto de entrar | Esperar. Reintentar solo pasados diez minutos sin respuesta: antes serían dos órdenes iguales |
| `ACEPTADO` | CDR con código 0. Puede traer observaciones (4000+), que no lo invalidan | Nada. **No se vuelve a enviar**: SUNAT ya lo tiene, y reenviarlo produce un rechazo por duplicado o un CDR distinto para el mismo número |
| `RECHAZADO` | Código 2000–3999, en el CDR o en un fallo SOAP. Para SUNAT el comprobante no existe | Corregir lo que diga el código y reenviar **con el mismo número** |
| `ERROR_ENVIO` | No llegó o SUNAT no respondió: red, servicio caído (0100–1999), certificado que no abre | Reintentar tal cual |
| `ANULADO` | Iteración 6 | — |

Un resultado que llega tarde para un comprobante que ya se reintentó **se
ignora**: `ComprobanteElectronico.aplicar` devuelve `false` si el comprobante no
estaba en cola. Sin eso, la respuesta de un intento viejo podría pisar la del
nuevo.

El XML se conserva aunque SUNAT rechace: es lo que hay que mirar para entender
el rechazo. El CDR se limpia al reintentar, porque el que había era del intento
anterior.

Todo esto está en `ComprobanteElectronico` y lo ejercita transición por
transición `ComprobanteElectronicoTest`, incluidas las prohibidas.

## 4. Dónde vive el certificado, y quién puede leerlo

El [DTE §8.2](DTE-ONX-001_sistema_gestion_comercial.md) decía Secrets Manager.
[Doc 12 §5.3](12-plan-primer-producto.md#53-dónde-vive-el-certificado) lo
cambió por el bucket: a 0,40 USD por secreto y mes, con cincuenta clientes son
20 USD/mes por guardar cincuenta archivos, y el bucket ya existe.

- `certificados/{ruc}.pfx` — el certificado, tal como lo entrega la entidad
  certificadora.
- `credenciales/{ruc}.json` — `{"claveCertificado": …, "claveSol": …}`.

Los dos los sube **el navegador** con URL prefirmadas, igual que los logos de
marca. Ni el archivo ni las contraseñas pasan por la API, por su bitácora ni por
sus registros. Lo que la API guarda en la base es solo lo que la pantalla
necesita: cuándo se cargó, qué dijo el Emisor al abrirlo, el titular y hasta
cuándo vale (`CertificadoDigital`, columnas `certificado_*` de la V20). La
columna `secret_arn_certificado` de la V1 se retira: nunca tuvo valor en ningún
entorno.

### 4.1 La frontera, y cómo se comprueba

`CLAUDE.md` dice que en `ondexia.api` no hay firma XML, porque *un módulo que
puede firmar es un módulo cuyo compromiso emite documentos con valor
tributario*. Aquí eso deja de ser una convención y pasa a ser IAM
(`ondexia.infra/facturacion.tf`):

| Prefijo | Rol de la API | Rol del Emisor |
|---|---|---|
| `pendientes/` | `PutObject` | `GetObject`, `DeleteObject` |
| `resultados/`, `documentos/` | `GetObject` | `PutObject` |
| `certificados/`, `credenciales/` | `PutObject` y `ListBucket` acotado al prefijo | `GetObject` |

La API puede **escribir** el certificado —la URL prefirmada de `PUT` ejecuta con
sus credenciales— y confirmar por listado que llegó, pero no puede **leerlo**.
Sin el archivo y su contraseña no se puede firmar.

Y esto es lo que la [regla 2 de `CLAUDE.md`](../CLAUDE.md) exige decir: **esa
frontera no la ejercita ninguna prueba de este repositorio.** No hay forma de
comprobar una política de IAM sin AWS, y fingirla con una prueba que no toca IAM
sería exactamente el `unaFirmaQueNadieVerificaPasa` del hallazgo A2. Lo que sí se
verifica es el plan de Terraform, y las dos políticas están escritas para que un
`terraform plan` las muestre enteras y legibles. Lo que sí prueban las pruebas es
lo que depende de nosotros: que la API nunca pide leer esos objetos
(`BusDeEmisionS3.existe` usa `ListObjectsV2` y no `HeadObject`, precisamente
porque `HeadObject` exige `s3:GetObject`).

### 4.2 Verificar el certificado

Al confirmar la carga, la API encola una orden `VERIFICAR_CREDENCIALES`: el
Emisor abre el `.pfx` con su contraseña y responde con el titular y la fecha de
caducidad, o con el motivo por el que no abre. No toca SUNAT.

Sirve para dos cosas: que una contraseña mal escrita se descubra en la pantalla
de configuración y no en la primera venta, y que **pasar a producción exija un
certificado que abre y no ha caducado** (`Empresa.habilitarProduccion`). En la
beta se admite cualquier cosa, que para eso está.

## 5. El Emisor por dentro

`ondexia.facturacion`, Lambda fuera de la VPC, con SnapStart como los otros dos
desplegables. Sin base de datos: recibe una orden con todo lo que el XML
necesita y devuelve un resultado.

**xbuilder sí, xsender no.** `xbuilder` genera el UBL de SUNAT con plantillas
Qute y firma con el `javax.xml.crypto` del JDK. Su hermano `xsender` haría el
envío SOAP, pero arrastra Apache Camel y CXF: decenas de megabytes y segundos de
arranque para una sola operación. El `sendBill` se escribe a mano con el
`HttpClient` del JDK —un sobre SOAP 1.1 con `UsernameToken` y el zip en base64,
unas veinte líneas— y las respuestas se leen con el analizador XML del JDK.

**Los importes son los del dominio, no los de xbuilder.** `ContentEnricher`
rellena la estructura y de paso recalcula los importes con su propia aritmética;
`ConstructorDeComprobante.imponerImportes` los pisa con los de la orden, línea a
línea y en los totales. La aritmética del comprobante es la de
[doc 13 §4.3](13-ventas-en-punto-de-venta.md) —desde el total con IGV, para que
dos bolsas a S/ 32.50 cuesten S/ 65.00 exactos— y un céntimo distinto entre el
XML y lo que el cliente pagó no es un detalle en un documento con valor
tributario.

Sin adquirente en la orden —boleta hasta S/ 700 a consumidor final— va el tipo
`0` del catálogo 06 con número `-` y nombre `CLIENTES VARIOS`.

**Siempre deja un resultado**, también cuando falla antes de llegar a SUNAT: un
fallo silencioso dejaría el comprobante en cola para siempre. Los fallos locales
llevan su propio código (`SIN_CREDENCIALES`, `CERTIFICADO_NO_ABRE`,
`EMISOR_FALLO`) y salen como `ERROR_ENVIO`.

**No se registra el cuerpo de una respuesta de error de SUNAT.** Un 401 suele
repetir la credencial enviada y CloudWatch conserva los registros (`CLAUDE.md`):
de una respuesta ilegible se guarda el estado HTTP y nada más.

## 6. Cómo se ensaya contra la beta

Lo que falta de §1 se hace desde una máquina con salida a internet. La beta de
SUNAT admite cualquier RUC con usuario `MODDATOS` y clave `MODDATOS`, y un
certificado autofirmado sirve.

1. Levantar la API y el Emisor con el perfil `local`:

   ```bash
   cd apps/backend
   SPRING_PROFILES_ACTIVE=local ./mvnw -pl ondexia.api spring-boot:run
   SPRING_PROFILES_ACTIVE=local ./mvnw -pl ondexia.facturacion spring-boot:run
   ```

   El bus del Emisor es entonces un directorio (`./bus-local` por omisión) y el
   de la API, un mapa en memoria: no se hablan solos, se les pasa la orden a
   mano. Es lo que permite ver cada paso.

2. Poner el certificado y las credenciales donde el Emisor los busca:

   ```bash
   mkdir -p bus-local/certificados bus-local/credenciales
   cp mi-certificado.pfx bus-local/certificados/20100000009.pfx
   cat > bus-local/credenciales/20100000009.json <<'JSON'
   {"claveCertificado": "la del pfx", "claveSol": "MODDATOS"}
   JSON
   ```

3. Emitir una boleta desde el punto de venta y sacar su orden de la API:

   ```bash
   curl -s localhost:8080/desarrollo/emision/ordenes | jq '.[-1]' > orden.json
   ```

4. Pasársela al Emisor, que firma y envía a la beta:

   ```bash
   curl -s -X POST localhost:8082/emision/ordenes \
        -H 'Content-Type: application/json' --data @orden.json > resultado.json
   ```

   En `bus-local/documentos/20100000009/` quedan el XML firmado y, si SUNAT
   respondió, el CDR.

5. Devolverle el resultado a la API, que lo aplica igual que en la plataforma:

   ```bash
   curl -s -X POST localhost:8080/desarrollo/emision/resultados \
        -H 'Content-Type: application/json' --data @resultado.json
   ```

Las tres rutas de `/desarrollo` solo existen con el perfil `local`, como el
emisor de tokens de desarrollo.

**Cuando el ensayo se ejecute, se anota aquí:** fecha, si el CDR llegó aceptado,
y qué hubo que corregir. Si al quinto día no hay CDR aceptado, la decisión 1
cambia y se sustituye `ProcesadorDeOrdenes` por un cliente REST del proveedor,
sin tocar nada fuera de este módulo.

### 6.1 El certificado con el que se prueba se genera, no se versiona

El paso 2 pide un `.pfx` y las pruebas del Emisor necesitan otro. Ninguno de los
dos está en el repositorio: `.gitignore` excluye `*.pfx`, `*.p12`, `*.jks` y
`*.pem` sin excepciones, porque un certificado que entra al historial obliga a
reescribirlo o a revocarlo, y la única excepción que hay —el paquete de
autoridades de RDS— es material público.

Un archivo de prueba que vive fuera del repositorio no es neutral: las once
pruebas que lo cargaban pasaban en la máquina donde se creó y fallaban en el CI
sin aparecer en ningún diff, y el fallo no se leía como «falta un archivo» sino
como once errores del módulo. Por eso el material lo produce la propia
compilación: `CertificadoDePrueba` genera un PKCS#12 autofirmado con el
`keytool` del JDK que está ejecutando las pruebas —no con openssl, que no está
garantizado en un runner— y lo deja en `target/certificado-prueba.pfx`.

Ese mismo archivo sirve para el ensayo contra la beta, que acepta un
autofirmado:

```bash
cd apps/backend && ./mvnw -o -pl ondexia.facturacion -am test
cp ondexia.facturacion/target/certificado-prueba.pfx bus-local/certificados/20100000009.pfx
# la clave del pfx es «prueba»
```

La regla general que deja esto: **si una prueba necesita material que el
`.gitignore` excluye, la prueba lo genera.** Traerlo a mano convierte la
compilación en algo que depende del disco de quien la ejecuta.

## 7. Qué pasa en el mostrador

- **Sin certificado ni clave SOL, la boleta y la factura no se emiten**:
  `emision_no_configurada`, con el mensaje que dice dónde configurarlo. Se
  comprueba **antes de asignar el correlativo**, porque un número gastado en una
  venta que no se registra es un hueco en la numeración que hay que justificar
  ante SUNAT. La nota de venta sigue saliendo: no se declara.
- El listado de boletas y facturas muestra el estado **ante SUNAT**, no el del
  negocio: un rechazo se ve desde la lista, sin abrir cada documento.
- La ficha del documento trae el código y la descripción de SUNAT tal cual, las
  observaciones del CDR si las hay, y los botones de XML, CDR y «volver a
  enviar». La representación impresa dice «Aceptado por SUNAT» con el resumen de
  la firma cuando lo está, y «pendiente» mientras no.

## 8. Lo que sigue abierto

- **El ensayo contra la beta** (§1). Es lo único que separa esta iteración de
  estar cerrada.
- **El QR normativo** en la representación impresa. Necesita el resumen de la
  firma, que ya se guarda; queda pendiente de la pantalla.
- **La alarma sobre `pendientes/` con antigüedad mayor de quince minutos**, del
  riesgo de [doc 12 §11](12-plan-primer-producto.md). Hoy hay alarma sobre los
  errores de la función; la de órdenes estancadas necesita una métrica que
  alguien tiene que publicar.
- **El resumen diario de boletas** como alternativa al envío individual. La
  decisión 2 fue enviar cada boleta; el resumen entra en la iteración 6 de todas
  formas, para anular.

## Registro de cambios

- **v1 (2026-09-08)** — Con la iteración 5.
- **v2 (2026-09-08)** — §6.1: el certificado de las pruebas lo genera la
  compilación. Once pruebas del Emisor pasaban en local y fallaban en el CI
  porque el `.pfx` estaba en el disco y no en el repositorio.
