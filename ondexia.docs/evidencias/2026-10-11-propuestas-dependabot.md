# Configuración de propuestas de Dependabot

Base develop `04076afda5012eb6fb61d083014fe733dd17549d`.
[Especificación](../especificaciones/2026-10-11-propuestas-dependabot.md).
[Resultados y huellas](2026-10-11-propuestas-dependabot.json).

Antes: cuatro casos ejecutados, diez fallos de subcasos (cinco destinos ausentes
y cinco prefijos Actualiza), sin errores. Después: cuatro casos correctos,
30 pruebas de herramientas correctas. Mutación semanal a diaria detectada por
un caso, archivo restaurado byte a byte y pruebas correctas después. Las áreas,
límites y resultados esperados proceden de las reglas vigentes y datos fijados
en la prueba. PyYAML ya disponible leyó el archivo local; no se añadió dependencia
de CI ni se presenta esa lectura como validación de opciones por GitHub.

Se declara target-branch develop y prefix build(dependencias) en las cinco
entradas. Se conservan revisión semanal/lunes, límite cinco y directorios.
La prueba es estructural: no simula traducción, integración ni ejecución del bot.
No se actualiza una dependencia en este corte.

GitHub confirma default_branch main y mantiene los 18 PR #13–#30 abiertos hacia
main, con sus heads consultados de nuevo. No se modificaron ni cerraron. No se
reescribieron commits históricos. El prefijo no traduce descripciones del bot;
la adopción de un cambio sigue requiriendo feature desde develop y commit español.

CI completo pendiente al redactar; los resultados del mismo head y del merge
se registrarán en el PR. No cambia API, UI, dinero, migraciones o datos.

No verificado: nueva propuesta real, aceptación de opciones por GitHub,
configuración operativa nueva, compatibilidad individual de paquetes o auditoría
independiente. La consulta del documento de opciones en github/docs recibió 404;
no se presenta como fuente consultada. Configuración en develop no significa
publicación en la rama por defecto. Main no se modifica; su publicación queda
fuera de este PR. Tampoco se infiere el destino de actualizaciones de seguridad.
ACT-23/F23 permanecen parciales.
