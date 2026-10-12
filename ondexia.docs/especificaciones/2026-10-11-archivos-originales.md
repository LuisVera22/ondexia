# F06: archivos originales de emisión

Base: develop ad3eaba2ffd586c4a9693a8968c974fa67138f44 (datos históricos, PR #40).
Decisión aprobada: documentos exactos enviados/recibidos de SUNAT se conservan
como originales. Este bloque conserva los tipos presentes en el flujo: XML firmado,
ZIP de envío, CDR ZIP recibido y SOAP reconocido con CDR/ticket/estado en proceso. El flujo actual no produce PDF persistido.

## Flujo y condiciones

Cada procesamiento con archivos crea un intento UUID independiente en
`documentos/{ruc}/originales/{ordenId}/{intentoId}/`. Conserva XML firmado y ZIP
antes de llamar al transporte; si falla su almacenamiento, no se envía. Emisión y
bajas conservan el ZIP exacto pasado al transporte. CDR se guarda sin regenerarlo.
Consultas de ticket tienen carpeta propia y vínculo al ticket en la referencia.

`envio.json` vincula orden/empresa, operación, fecha de registro, claves XML/ZIP y
SHA-256 de sus bytes. `recepcion.json` vincula tipo/código, ticket, CDR y su hash,
y la clave/hash del SOAP original reconocido (`respuesta.xml`). El estado 98
también conserva su respuesta; no se regenera el XML a partir del modelo.
Se trata de referencias documentales permanentes: no contienen certificado,
contraseñas SOL o textos de errores del proveedor. Registrar un envío preparado
no afirma aceptación de SUNAT ni entrega efectiva si falla la red.

ResultadoDeEmision apunta a los originales del intento. Un reintento no cambia los
archivos anteriores; las rutas legacy siguen disponibles y no se reescriben.
Los resultados operativos siguen siendo transitorios; los documentos y referencias
quedan bajo documentos/, sin regla de expiración en el Terraform existente.

Disco: CREATE_NEW rechaza clave existente. S3: PutObject con If-None-Match: *
solicita creación sin sobrescritura. Ambos adaptadores rechazan borrar documentos.
No es una afirmación de Object Lock, privilegios de administradores o configuración
activa de AWS. El ensayo real de la condición, concurrencia y recuperación requiere
un entorno AWS autorizado; pendiente. No se cambia retención, bucket ni IAM.

## Pruebas y oráculos

Regresión sin arreglo: dos pruebas fallan (ruta reutilizada y disco sobrescribible).
CDR esperado procede de una entrada binaria preparada por el test. Para XML/ZIP,
el oráculo es la captura de la frontera de transporte independiente del almacén:
lo que recibió SUNAT fingida debe coincidir byte por byte con lo conservado. No se
obtiene el esperado del archivo guardado. Fechas/IDs/códigos son entradas fijas.

Una regresión adicional falla al comparar el ticket SOAP original con el archivo
ausente; pasa tras conservar la respuesta exacta. El lector copia defensivamente
los bytes originales y excluye Fault y respuestas no reconocidas.

Pruebas negativas: falla de archivo impide llamada de red; colisión en disco no
altera bytes; borrado de original rechazado. Quitar condición/guardia S3 produce
fallos en pruebas de peticiones SDK sin red. Restauración byte por byte y suite
completa posterior. No se consideran rojas funcionales errores de configuración
de Mockito ni invocaciones de Maven desde una carpeta incorrecta.

## Límites

No recupera datos del legado ni crea PDF. No conserva cuerpos SOAP de error ni
credenciales de transporte como documentos. No prueba SUNAT/AWS real, Object Lock,
permisos de consola, listado histórico de intentos en interfaz ni restauración de
backups. No cierra auditoría independiente ni todo F06. Datos exactos al emitir se
conservan por V28 del bloque anterior; no se modifica otra migración ni dinero.
