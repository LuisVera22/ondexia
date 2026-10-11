# ACT-17 · F24 — lectura local acotada de XML/ZIP recibidos

Base develop `2d22377e419489f9aa44dedc8f8fd994655cfe44`.
Corte técnico del backlog existente; no cambia reglas tributarias, emisión,
inmutabilidad ni contrato HTTP. No requiere un caso real en producción.

## Flujo y criterios

SOAP externo → DOM → base64 CDR → ZIP → primer XML → DOM CDR → respuesta existente.
El ZIP se lee en memoria; no se extraen rutas al disco. Conservar compatibilidad
con carpeta `dummy/`, nombre `.xml` en mayúsculas y elección del primer XML.

Límites operativos iniciales: ZIP comprimido 8 MiB, consumo descomprimido total
hasta el primer XML inclusive 8 MiB, 32 entradas examinadas y SOAP recibido por
el lector 16 MiB. No se confía en tamaño declarado por la entrada: cuenta bytes
realmente leídos. Valores exactos admitidos; un byte/entrada adicional se rechaza.
No son límites fiscales ni un máximo comercial de comprobantes; una respuesta
mayor queda sin interpretar y requiere diagnóstico, no se considera rechazada
por SUNAT. No se amplían silenciosamente ante un archivo desconocido.

ZIP ausente, vacío, sin XML, corrupto o sobre límite y CDR XML inválido devuelven
`SIN_RESPUESTA / CDR_ILEGIBLE`, con descripción fija, sin cuerpo, notas ni
archivos que el consumidor pueda interpretar como constancia. Base64 ilegible
de `applicationResponse` y `status/content` sigue ese mismo resultado, sin lanzar
una excepción fuera del lector. SOAP ilegible/sobre límite mantiene el código
existente `HTTP_<estado>`, sin aceptación ni rechazo tributario.

Parser externo: rechaza DTD; sin entidades externas, XInclude, DTD/esquemas
remotos; suprime los diagnósticos predeterminados que imprimen contenido en
stderr. El comentario de seguridad enlaza las pruebas correspondientes.
No cambia descripciones legítimas de CDR/Fault: su revisión pertenece a otro
corte. Los XML internos generados para firma también quedan fuera de este corte.

## Oráculos, evidencia y alcance

Fixtures sintéticos con bytes/conteos fijados por el límite especificado,
independientes del resultado; no se provoca agotamiento de memoria. Una entrada
comprimible de tamaño conocido reproduce ausencia de límite. Otra entrada grande
previa al XML prueba que saltar una entrada también cuenta. XML mal formado con
un marcador público demuestra diagnóstico expuesto; no se utilizan credenciales.

Cada defecto: prueba roja sobre la base y verde después del arreglo. Para DTD y
CDR correcto ya admitidos, mutaciones aisladas con restauración byte por byte.
Regresión completa de dominio/facturación y CI del mismo head antes de integrar.

Este corte recibió SOAP ya materializado; el [corte posterior HTTP](2026-10-11-descarga-soap-acotada.md)
añade conteo durante descarga y tiempo de recepción completa.
Pendientes: presupuesto global de CPU/memoria, validación estructural y
vinculación/firma CDR, XML interno y auditoría independiente. No se afirma
explotación AWS/SUNAT ni seguridad completa. Por decisión del propietario, la
validación de originales reales queda para cuando exista un caso en producción;
ahora se verifican escenarios y archivos sintéticos sin bloquear desarrollo.
