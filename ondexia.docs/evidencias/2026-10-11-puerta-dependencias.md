# Sensibilidad de la puerta del análisis de dependencias

Base develop `9681a1e0cfd19366603a784423c50edbe9098898`.
[Especificación](../especificaciones/2026-10-11-puerta-dependencias.md).
[Resultados y huellas de registros](2026-10-11-puerta-dependencias.json).

Los controles actuales funcionan en el alcance ensayado. No hay corrección
funcional ni una prueba roja atribuida a un defecto nuevo: se añade regresión y
sensibilidad mediante mutaciones. Los cuerpos de los pasos se extraen de CI/CD,
con Bash -e y pipefail. Maven temporal registra argumentos y devuelve un código
prefijado; no ejecuta el escáner ni usa credenciales reales.

Pasaron siete casos: producción sin clave detiene antes de Maven; desarrollo/CI
sin clave avisan la omisión; rama con análisis aplazado lo declara; con clave no
se envía skip; fallos del sustituto se propagan tanto con análisis como sin él.
Los argumentos esperados y códigos 17/23 se fijan en las entradas/reglas,
no se derivan del resultado observado. La clave ficticia no aparece en salida.

Tres mutaciones independientes produjeron un fallo cada una: retirar exit de
producción, ocultar fallo con `|| true` en CI, y ocultarlo en CD. Tras cada ensayo
se restauró el workflow y se comparó byte a byte. Los 26 casos de herramientas
pasaron con las fuentes restauradas. Los comentarios de los workflows enlazan
la prueba, sin alterar comandos, condiciones ni política de omisión vigente.

CI completo pendiente al redactar; PR y CI posterior de develop se contrastarán
contra su commit exacto y se enlazarán en el PR. No cambia API/OpenAPI/modelos,
interfaz, importes, migraciones ni datos.

No verificado por este ensayo: detección de una CVE real por el motor, vigencia
y cobertura de NVD, semántica del runner fuera del shell, despliegue AWS y
auditoría independiente. No se interpreta un CI verde con skip como análisis
ejecutado; los logs Analysis Complete de cada módulo se consultan por separado.
ACT-19 sigue parcial respecto a esos límites; no se cierra F20/F23/F24 global.
