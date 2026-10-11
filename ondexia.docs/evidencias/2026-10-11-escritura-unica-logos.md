# Escritura única de nuevas subidas de logos

Base develop `87c05bb145706a886b317e2f303b5193452c585d`.
[Especificación](../especificaciones/2026-10-11-escritura-unica-logos.md).
[Resultados y SHA-256 de registros](2026-10-11-escritura-unica-logos.json).

Defecto confirmado localmente: la autorización firmada no exigía creación
exclusiva, el cliente no enviaba esa condición y el doble permitía reemplazar
la misma clave. Eso contradice la conservación del logo por clave histórica.
No se afirma que se haya explotado en AWS.

Antes de corregir, tres pruebas backend y una web fallaron por esos comportamientos.
La prueba de CORS falló al retirar la nueva cabecera (dos casos, un fallo).
Después de corregir pasaron 60 pruebas backend acotadas, 144 web y 19 de
herramientas. El SDK real firma `content-type;host;if-none-match` sin petición
de red y con credenciales ficticias; el cliente envía `If-None-Match: *` y
propaga HTTP 412 sin reintento. El doble rechaza reemplazo, conserva contenido
validable y admite claves independientes. No es un ensayo real de S3.

Sensibilidad de controles existentes: origen CORS cambiado a `*` produjo un
fallo; quitar `clone()` produjo una excepción de imagen inválida al modificar
el arreglo del emisor. Ambos archivos se restauraron y se compararon byte a
byte. Las cinco pruebas del doble/firma volvieron a pasar tras la restauración;
las 19 de herramientas también pasaron con CORS restaurado.

OpenAPI regenerado: solo descripción de cabeceras en la URL; modelos Angular
regenerados sin diff estructural y transporte actualizado en el mismo cambio.
La prueba web inicial sin Chromium y dos errores de compilación al preparar los
tests se corrigieron; no se cuentan como demostración del defecto. Los registros
Maven publicados son extractos de resultados/diagnósticos, sin cuerpos HTTP.

CI completo pendiente al redactar; los enlaces, commit exacto y resultados de
PR/develop se registran en el PR al terminar, sin reutilizar otro head.
SCA se omitió únicamente en la ejecución local acotada; no equivale a CI completo.

No verificado: S3 efectivo, CORS desplegado, CDN, publicación anterior a
confirmación, acciones privilegiadas/delete, costos y auditoría independiente.
Terraform no está instalado localmente; formato/validación se comprueban en CI.
La consulta de documentación pública AWS recibió 403; no se considera consultada.
Sin despliegue ni cambios de migraciones. Las autorizaciones anteriores pueden
seguir sin condición hasta caducar; no se recuperan bytes ya sobrescritos.
ACT-16/F06/F24 permanecen parciales.
