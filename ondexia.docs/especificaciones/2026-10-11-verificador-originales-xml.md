# F06 — comprobar integridad de candidatos XML

Base: develop `fb5c9fc1637e3dff0e6b53f5a5680063c057338a`.
Complementa el inventario y el procedimiento de recuperación existentes.
Capacidad nueva local, sin endpoint, migración, dependencia añadida ni modificación
del firmador de producción. No cierra F06 ni ejecuta una recuperación.

## Flujo y fuentes de los valores esperados

El operador obtiene una copia estable del candidato y un único certificado
público X.509 por una fuente independiente del XML. Documenta la procedencia
del certificado y del archivo. El RUC, tipo (`01`, `03`, `07`) y serie/número
esperados provienen del registro que se investiga, no del candidato ni de la
salida del verificador. Se comprueba la identidad UBL en campos directos únicos.
No se procesa ZIP/CDR/PDF ni se accede a red, BD o AWS.

Entradas XML de hasta 8 MiB y certificado de hasta 64 KiB: límites operativos,
no fiscales. La consola exige archivos regulares, no sigue el enlace final,
limita la lectura y no escribe en la fuente. Los directorios y la copia estable
son responsabilidad del operador; no se afirma resistencia a sustituciones
concurrentes de directorios. Pruebas operativas ejecutadas en Linux/JDK 21.

## Perfil de firma y rechazos

Una única XML-DSig en `ext:UBLExtensions/UBLExtension/ExtensionContent`, una
referencia con URI vacía al documento completo, sin objetos ni recuperación de
claves externa. Certificado incorporado igual en DER al certificado independiente;
se valida con la clave de este último. RSA-SHA256/SHA256 por defecto; RSA-SHA1/SHA1
solo con opción explícita `--permitir-sha1-legado`. Esta opción permite examinar
firmas heredadas, no moderniza SHA-1 ni cambia la firma de producción.

Canonicalización inclusiva o exclusiva sin comentarios; transformaciones:
`enveloped-signature`, opcionalmente seguida de esa canonicalización. Sin
parámetros en transformaciones. Se examina el perfil DOM antes de construir
servicios de transformación. No DTD, entidades externas, XInclude, referencias
externas, firmas de fragmentos, múltiples firmas ni XSLT. Un perfil fuera del
conjunto admitido requiere evaluación separada, no implica documento perdido.

Resultado JSON y códigos: `ENTRADA_NO_ADMITIDA`, `XML_NO_ADMITIDO`,
`IDENTIDAD_NO_COINCIDE`, `CERTIFICADO_NO_COINCIDE`, `PERFIL_NO_ADMITIDO`,
`FIRMA_INVALIDA`, `INTEGRIDAD_VERIFICADA`, `INTEGRIDAD_SHA1_LEGADO`.
Código de salida: 0 integridad comprobada, 1 candidato rechazado, 2 uso/lectura
ilegible o no admitida. Errores operativos no muestran XML, rutas o mensajes
internos del proveedor. El resultado positivo mantiene `confianzaVerificada`
y `aceptacionSunatVerificada` en falso.

## Aceptación y límites

Pruebas con identidad fijada en fixtures y certificado autofirmado exclusivo
de pruebas: original íntegro, alteración de nombre, RUC/tipo/número distintos,
certificado distinto, SHA-1 no autorizado, SHA-256 sin compatibilidad, firma
criptográficamente válida de fragmento, referencia externa, XSLT, dos firmas,
identidad duplicada, XML inválido, DTD, vacío/excesivo, consola y enlaces.
Se conserva el archivo byte por byte. La capacidad nueva se desarrolla con
prueba roja sobre stub; no se presenta como siete defectos históricos.
Mutaciones aisladas deben demostrar sensibilidad y restaurarse byte por byte.

No comprueba cadena de confianza, revocación, identidad jurídica del titular,
vigencia aplicable a la emisión, sello de tiempo, esquema/reglas SUNAT, CDR
ni aceptación tributaria. Una firma válida y un certificado coincidente no
bastan para declarar auténtico un original o restaurar datos. Pendientes esas
comprobaciones, fuentes reales, vinculación histórica y auditoría independiente.
PDF en servidor pendiente de decisión de fase (§5.4 del plan de primer producto).
