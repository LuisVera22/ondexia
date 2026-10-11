# ACT-15 · F17/F18 — identidad de resultados de comprobantes

Base develop `99ddbabd2aa9eea4bc0f4baea035df5626aa6a26`.
Corrección técnica de una frontera existente, sin nueva regla tributaria.

## Regla y plan

El agregado `ComprobanteElectronico` solo aplica resultados de su propio ID,
empresa y operación `EMITIR`. La clave consultada en el bus no sustituye la
validación del mensaje deserializado. Una verificación de credenciales aceptada
no acredita la aceptación de un comprobante.

1. Demostrar fallo con resultado de otra empresa y operaciones distintas/nulas,
   para estados ACEPTADO, RECHAZADO y ERROR_ENVIO, sobre fixtures independientes.
2. Validar identidad antes de modificar cualquier campo. Mensaje incompatible:
   `IllegalArgumentException` técnica con descripción fija. No guardar el
   comprobante ni modificar/guardar el documento asociado.
3. Comprobar las entradas de aplicación directa y consulta del bus con dobles
   de puertos. Conservar aceptación/rechazo/error válidos y estado final.
4. Mutar por separado controles existentes de ID y aceptación final; restaurar
   byte por byte y ejecutar regresión y CI del mismo head antes de integrar.

No cambia DTO, OpenAPI, migraciones, publicación ni reintento. No se deduce una
explotación de AWS de una prueba de dominio. Los dobles verifican la frontera
de aplicación, no RLS ni transacciones reales.

## Pendientes separados

F17 sigue abierto: rollback, fallo poscommit, duplicados, orden temporal y
correlación por intento requieren caracterización adicional. El comentario que
afirmaba ignorar cualquier intento anterior excede el control real: solo se
ignoran resultados cuando el estado ya no es EN_COLA. No se cambia aquí la
política ante respuestas tardías ni se inventa idempotencia por fecha.
ACT-15 sigue parcial; esta validación no reemplaza permisos/RLS, firma del CDR,
autenticidad del productor ni validación de claves de archivos.
