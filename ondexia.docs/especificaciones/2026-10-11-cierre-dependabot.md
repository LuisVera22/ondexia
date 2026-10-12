# Resolver las propuestas pendientes de Dependabot

El propietario autoriza completar la revisión y cerrar todas las ramas del bot.
Base: develop `2c0b23a50ef47de2cf6dfff2255467ba880b4221`.
Complementa ACT-23 y su revisión individual; no sustituye el backlog.

## Lotes y criterios

1. Interfaz: adaptar #28 PostCSS 8.5.29, #29 Zone 0.16.3 y #30 reporter 2.3.0
   en web/panel para mantener versiones coherentes. Angular 21 admite la serie
   de Zone; reporter admite Jasmine 5. PostCSS tiene casos concretos de listas
   con comentarios y escape de cierre style: fijar oráculos de sus tests
   oficiales, demostrar rojo/verde. Caracterizar microtareas, temporizadores,
   cancelación y eventos con NgZone; mutación aislada de cancelación detectada.
   Instalaciones congeladas, builds y suites de ambas interfaces.
2. Backend: adaptar #17 Maven 3.10.0; no adoptar en este ciclo #20 Qute ni #22 AWS
   preservando comentarios, wrapper Windows, contratos, datos y migraciones.
   Ejecutar reactor, arquitectura y XML/originales; la compatibilidad local y CI
   no acredita IAM, AWS real ni aceptación SUNAT. No inventar rojo para versiones
   sin defecto productivo reproducido.
3. CI: coordinar #14/#19 upload/download con contrato de archivos y prueba real
   de intercambio en GitHub sin despliegue; #16 setup-java y #21 restore/save
   coordinados. Verificar SHAs del proveedor, runner/runtime, Java 21, caché,
   análisis efectivo y huellas de los archivos intercambiados.
4. Cerrar como no adoptadas en este ciclo #15, #18, #20, #22 y #24–#27 con motivos y
   condiciones de reevaluación registrados. No equivalen a actualizaciones
   integradas ni a cierre de toda la deuda. No desactivar Dependabot ni sus
   avisos; no cambiar main para alterar su configuración activa.

Por lote: PR hacia develop, CI completo y controles del mismo head, merge con
expected_head_sha, CI completo del commit integrado. Antes de cerrar una
propuesta comprobar nuevamente head y archivos; si cambió, reevaluar. Eliminar
exclusivamente cada rama inventariada, sin force, después de registrar su
resolución. Verificar ausencia en remoto y tratar la eliminación automática
del bot como ausencia ya resuelta, sin repetirla. Nunca borrar novedades sin
revisar ni declarar todas integradas si alguna se cierra como diferida.
