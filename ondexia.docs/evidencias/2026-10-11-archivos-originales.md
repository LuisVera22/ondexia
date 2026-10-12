# F06 — evidencia de archivos originales

Base: develop `ad3eaba2ffd586c4a9693a8968c974fa67138f44` (PR #40).
Especificación: [archivos originales](../especificaciones/2026-10-11-archivos-originales.md).

## Resultado comprobado

533 pruebas backend completas correctas (130 dominio, 288 API, 41 panel,
41 consultas y 33 emisor). Base efímera PostgreSQL de Testcontainers; ningún
volumen de datos existente eliminado. SCA omitido explícitamente en local;
CI pendiente al registrar esta evidencia. No hay cambio de API ni de dinero.

Antes del arreglo, dos pruebas fallaron por ruta reutilizada y sobrescritura
en disco. Añadir la comprobación de SOAP original produjo otro fallo porque
no existía el archivo del ticket. Tras los arreglos las pruebas pasan.

Mutaciones aisladas detectadas: retirar condición/guardia S3 (dos fallos),
enviar sin guardar ZIP (un fallo), conservar cuerpos Fault/no reconocidos
(dos fallos). Cada archivo se restauró y comparó byte por byte; la suite final
es correcta y la ejecución posterior a la última restauración pasa.

Los esperados CDR/SOAP proceden de entradas controladas; XML/ZIP se contrastan
con la captura del transporte fingido, independiente del almacenamiento. No
se construye el esperado leyendo archivos conservados. El lector protege
la copia del SOAP de cambios posteriores en arreglos externos.

## Límites y pendientes

No se ensayó AWS ni SUNAT real, Object Lock, administradores, concurrencia real,
restauración de backups ni recuperación de documentos anteriores. La impresión
actual no genera PDF persistido. No se afirma auditoría independiente ni cierre
completo de F06. Los errores de Mockito/invocación desde carpeta incorrecta no
se cuentan como pruebas rojas funcionales.

El JSON adjunto contiene los registros comprimidos y sus SHA-256.
