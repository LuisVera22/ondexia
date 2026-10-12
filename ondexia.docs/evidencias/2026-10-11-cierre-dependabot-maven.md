# Compatibilidad del wrapper Maven 3.10.0

Adaptación de #17 sobre develop `87c583e5adf36ae608a2a8015bf20697174807ba`.
[Plan vigente](../especificaciones/2026-10-11-cierre-dependabot.md) y
[resultados y huellas](2026-10-11-cierre-dependabot-maven.json).

Solo cambia la URL de distribución a 3.10.0 y se actualiza el comentario español
que declaraba 3.9.16 como última estable. No se copia la eliminación de comentarios
ni el cambio completo de finales de línea de mvnw.cmd incluidos en el PR del bot.
Se verificó igualdad byte a byte del wrapper Windows con HEAD; su huella consta
en el JSON. No se ejecutó Windows real: no se atribuye esa cobertura al Linux CI.

El wrapper descarga y ejecuta Maven 3.10.0 con Java 21.0.12.1. Reactor y 656
pruebas correctos (170/327/41/41/77), con 0 fallos, errores u omisiones. API/panel
usan el directorio de contratos alternativo; emisor su directorio real para
certificados de prueba. Base externa local identificada como pruebas-sinteticas,
sin tocar bases de clientes, migraciones ni contratos versionados.

No se ha reproducido un defecto de negocio atribuible a 3.9.16: este lote verifica
compatibilidad y no inventa una prueba roja. Tampoco verifica la nueva política
de orígenes de credenciales anunciada por Maven, AWS ni aceptación SUNAT.
NVD se omitió solo en local; el CI completo debe ejecutar sus seis análisis.
Los logs del reactor se conservan como resúmenes de resultados con huellas de
los originales locales; la salida de versión se conserva completa.

CI del PR y del merge pendientes al registrar. #17 se cerrará como sustituido
solo después de esos verdes y de comprobar que conserva su head/delta esperado.
