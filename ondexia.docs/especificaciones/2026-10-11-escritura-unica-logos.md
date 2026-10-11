# Escritura única para nuevas subidas de logos

ACT-16/F06/F24. Base develop `87c05bb145706a886b317e2f303b5193452c585d`.
Decisión vigente: conservar versión del logo al emitir. El versionado del bucket
no basta cuando la URL del documento usa una clave sin versionId y esa clave se
puede sobrescribir. No se cambian formatos, límites, datos históricos ni negocio.

Corte técnico: presignar PUT con `If-None-Match: *`; Angular envía esa condición
y MIME; CORS admite las dos cabeceras desde el origen existente. Misma respuesta
url/clave/validaSegundos; OpenAPI explica la cabecera requerida y se regenera con
el cliente. El SDK debe incluir la condición en SignedHeaders: omitir/cambiar
la cabecera no conserva la petición firmada. Se verifica la firma del SDK local,
no la respuesta real de S3. No se añade servicio ni endpoint.

Oráculos: nueva clave se puede cargar; segunda subida a la misma clave se
rechaza y conserva metadata/bytes; claves nuevas independientes se mantienen;
cliente envía condición/cuerpo/MIME y permite propagar rechazo para que la
pantalla no confirme. No se hace reintento automático de una carga incierta;
el recorrido normal pide autorización nueva al seleccionar el archivo de nuevo.
Confirmar no ocurre si PUT falla, como en el flujo existente.

El doble reproduce escritura única de forma atómica por objeto, con copia de
bytes; no se usa su comportamiento para acreditar S3. El control de contenido
del PR #48 y las referencias históricas conservan sus pruebas. Una autorización
expedida antes de actualizar puede no incluir la condición hasta que caduque;
la corrección no recupera bytes ya sobrescritos ni sanea documentos antiguos.

No verificado: aplicación efectiva en S3, CORS desplegado/CDN, latencia/costo AWS,
auditoría independiente y conservación frente a acciones privilegiadas/delete.
La lectura de documentación pública de PutObject recibió HTTP 403 en este entorno;
no se presenta como fuente consultada. Referencia para contraste posterior:
https://docs.aws.amazon.com/AmazonS3/latest/API/API_PutObject.html.
Publicación de objetos antes de confirmar permanece pendiente separado. Sin
terraform apply ni despliegue; ACT-16 no se cierra con pruebas locales parciales.
