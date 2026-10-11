# Parches compatibles de Karma y cobertura

[Especificación](../especificaciones/2026-10-11-avisos-karma.md),
[resultados y huellas](2026-10-11-avisos-karma.json),
[fuentes npm versionadas](2026-10-11-avisos-karma-fuentes.json),
[oráculo reproducible](2026-10-11-comprobar-avisos-karma.py).
Base develop `cc3222efb4c6ca6033f472c036566c83815be16c`.

## Cambio y evidencia

Solo cambian los lockfiles de web/panel: body-parser 1.20.6 → 1.20.8,
qs 6.15.3 → 6.16.0, brace-expansion 1.1.18 → 1.1.21 y engine.io
6.6.9 → 6.6.11, con las referencias de sus padres. Karma admite body-parser
^1.19.0; su parche admite qs ~6.16.0. Minimatch admite brace-expansion ^1.1.7;
Socket.IO admite engine.io ~6.6.0. No hay overrides o dependencias directas nuevas;
importers/manifiestos idénticos, Babel y datos de navegadores conservados.

La auditoría completa contiene los seis avisos GHSA objetivo antes; el oráculo
devuelve 1. Después no contiene ninguno y el oráculo devuelve 0. Los esperados
son los avisos fijados en la especificación previa, no conteos deducidos de la
resolución. El oráculo rechaza auditoría filtrada y respuesta de error con 2,
y un crítico distinto de los seis objetivos con 1. Son tres casos negativos,
sin ejecución de exploit ni pruebas contra terceros.

| Aplicación | Altos antes/después | Moderados antes/después | Críticos | Regresión |
| --- | --- | --- | --- | --- |
| Web | 4 → 1 | 3 → 0 | 0 | 144 pruebas correctas |
| Panel | 4 → 1 | 3 → 0 | 0 | 5 pruebas correctas |

Instalación congelada y builds correctos en ambos. Auditoría --prod con umbral
high correcta. La auditoría completa conserva código 1 por braces; no se afirma
que pase toda la auditoría ni que se haya eliminado toda deuda de seguridad.
El script comprueba estos seis avisos y críticos; es herramienta de evidencia
manual del lote, no un control nuevo instalado en CI.

## Preparación y límites

La resolución inicial deduplicó también Babel/browserslist. Se retiró esa
deduplicación; un recorte intermedio produjo YAML inválido, detectado antes de
instalar y restaurado desde la base. El lockfile final conserva las entradas
originales ajenas al alcance y valida sintaxis, alcance y coherencia con instalación
--frozen-lockfile. Se repitieron auditorías, builds y suites sobre esa versión final;
no se reutilizan resultados de la variante amplia o inválida.

Permanece GHSA-vfj7-8cjw-p6xm en braces 3.0.3 por Karma/chokidar.
El registro devolvió 404 para braces 3.0.4 y qs 6.15.4; el arreglo de qs usa
la versión publicada 6.16.0 mediante body-parser compatible. No se acepta
excepción para braces: sigue pendiente de caracterización y alternativa proporcional.
La landing conserva sus avisos y no cambia aquí. Los 18 PR del bot no se resuelven
por este lote; ninguna rama del bot se cierra o elimina.

CI completo del head y del merge pendientes al redactar, con cierre registrado
en el PR después de comprobarlos. No cambia main, API/modelos, dinero, migraciones
ni datos; no se despliega. Sin auditoría independiente, alcance de explotación,
configuración AWS ni garantía ante avisos futuros. ACT-19/23 siguen parciales.
