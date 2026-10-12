# Evidencia ACT-17 · F24 — recepción HTTP acotada

Base develop `fb90213416fc1bfc7d9ab5192fe23b990b8f4d03`.
[Especificación](../especificaciones/2026-10-11-descarga-soap-acotada.md),
[registro y hashes](2026-10-11-descarga-soap-acotada.json).

## Defectos demostrados y corrección

`ClienteSunatHttpTest`: ocho casos sintéticos locales. Con el cliente original,
cuatro fallan: cuerpo abierto tras 16 MiB + 1 byte excede la ventana independiente
de cuatro segundos; cabeceras y un byte con timeout de 300 ms exceden la ventana
de tres segundos; exceso con longitud declarada llega al lector (`HTTP_200`);
cuerpo truncado devuelve el detalle de la excepción de transporte.
No se afirma cuatro vulnerabilidades distintas ni filtración de credenciales.

Se cuenta antes de acumular, sin confiar en `Content-Length`, y se cancela al
superar 16 MiB. La espera del futuro cubre la recepción completa; vencimiento e
interrupción cancelan la operación. Exceso no conserva originales parciales y
devuelve código propio sin rechazo tributario. Transporte tiene descripción fija.
El límite exacto admite el ticket y conserva sus bytes originales; enviar y
consultar mantienen el estado 98. La interrupción conserva el flag del hilo.
No se agregan dependencias ni servicios.

## Sensibilidad y regresión

Ocho casos verdes tras el arreglo. El control preexistente de redirecciones se
mutó `NEVER → ALWAYS`: la prueba falla porque obtiene el ticket del segundo
destino en vez de `HTTP_307`. Restauración del cliente comparada byte por byte.
Regresión después de restaurar: dominio 130 y facturación 77, sin fallos/errores.

La primera ejecución no pudo crear sockets en el sandbox: no es evidencia de
defecto de producto. Las ejecuciones registradas usan permiso para servidor
efímero exclusivamente en `127.0.0.1`. Maven offline; análisis de dependencias
omitido localmente y pendiente de CI al registrar. No se interpreta CI como
auditoría independiente. Publicación/integración sujetas a CI del mismo head.

## No verificado

No se agotó memoria ni se midió el máximo del heap: el JDK puede entregar buffers
antes del control y la representación DOM/original tiene copias. No se limita
concurrencia, cabeceras, solicitud saliente ni tiempo de interpretación XML.
ACT-17 sigue parcial: vinculación, esquema/firma CDR y XML interno pendientes.
Sin AWS/SUNAT ni documentos reales; validación histórica real diferida por el
propietario hasta que exista un caso en producción.
