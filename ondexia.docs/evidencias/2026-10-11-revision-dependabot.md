# Revisión individual de Dependabot, 2026-10-11

Complementa ACT-23 y el [triaje anterior](2026-10-10-triaje-dependabot.md).
Base inspeccionada: develop `a0980e25382fbeefab47128498d2afaf2ef8036b`.
[Inventario, heads, checks y fuentes oficiales](2026-10-11-revision-dependabot.json).

## Resultado y alcance

Las 18 propuestas #13–#30 siguen abiertas hacia main, con los heads registrados
en el JSON. Sus checks build consultados terminaron correctamente; son controles
de sus commits sobre main, no comprobaciones de las actualizaciones sobre develop.
En #15 el triaje previo verificó que el escáner fue omitido: no se atribuye cobertura
SCA a ese verde ni se extrapola su omisión a todos los demás PR.

Selección técnica: cinco candidatas a integrar, diez a adaptar y tres a posponer.
«Integrar» significa apta para preparar un lote y ejecutar sus comprobaciones;
ninguna versión propuesta ha sido instalada, integrada ni aceptada por esta revisión.
No hay descarte definitivo: no cerrar ramas con actualizaciones aún sin resolver.

## Seguridad que cambia la prioridad

Se ejecutó `pnpm audit --json` de los tres lockfiles actuales; las tres ejecuciones
devolvieron 1 con avisos válidos, sin error de infraestructura. Las salidas comprimidas,
huellas y conteos están en [el resumen reproducible](2026-10-11-auditorias-dependabot.json).

| Aplicación | Bajos | Moderados | Altos | Críticos |
| --- | ---: | ---: | ---: | ---: |
| Web | 4 | 23 | 17 | 2 |
| Panel | 4 | 19 | 16 | 2 |
| Landing | 0 | 2 | 2 | 0 |

Son conteos informados por la herramienta para cada árbol, no vulnerabilidades
únicas en producción. Web y panel comparten avisos; no sumar como incidencias distintas.
Todos los avisos web/panel tienen rutas marcadas dev. En landing fast-uri es dev;
smol-toml es ruta no dev de Astro, lo que tampoco implica que el paquete se ejecute
en el sitio estático servido desde S3. Falta análisis de alcanzabilidad y exposición.

Ambos árboles Angular contienen:
- piscina 5.2.0 por @angular/build, [GHSA-67c8-pqhq-4rmx](https://github.com/advisories/GHSA-67c8-pqhq-4rmx),
  crítico; el aviso declara corrección desde 5.3.2.
- proxy-addr 2.0.7 por CLI/MCP/Express,
  [GHSA-jqcg-44mw-7w3h](https://github.com/advisories/GHSA-jqcg-44mw-7w3h),
  crítico; el aviso declara corrección desde 2.0.8.

Landing tiene fast-uri 3.1.6 y smol-toml 1.8.0. Uno de los avisos fast-uri
[GHSA-58mr-gqgx-xq4g](https://github.com/advisories/GHSA-58mr-gqgx-xq4g)
no declara versión corregida: no inventar una garantía de solución a partir de otro aviso.

La auditoría actual incluye herramientas de desarrollo; el CI existente ejecuta
`pnpm audit --prod --audit-level=high`. Por ello el verde anterior no acredita
ausencia de estos avisos ni se deduce un fallo del CI actual sin ejecutarlo.
No se ha demostrado explotación, exposición AWS ni impacto sobre datos.
El acceso a las alertas privadas del repositorio no está disponible por el conector;
esto no significa que no existan alertas.

## Decisión por propuesta

Esfuerzo relativo: bajo = cambio localizado y regresión disponible; medio =
coordinación o caracterización adicional; alto = migración de plataforma.
No son horas, presupuesto aprobado ni retorno financiero medido.

| PR | Dependencia propuesta | Decisión y prioridad | Esfuerzo | Motivo y comprobaciones |
| --- | --- | --- | --- | --- |
| [#13](https://github.com/LuisVera22/ondexia/pull/13) | ArchUnit 1.5.1 | integrar · P1 | Bajo | Corrige detección de dependencias, incluidas excepciones capturadas; ejecutar reglas arquitectónicas y regresión backend. |
| [#14](https://github.com/LuisVera22/ondexia/pull/14) | download-artifact 8.0.1 | adaptar · P1 | Medio | Coordinar con #19; comprobar nombres, formatos y descarga. Mantener rechazo ante huella incorrecta; sin despliegue. |
| [#15](https://github.com/LuisVera22/ondexia/pull/15) | Dependency-Check 13.0.0 | adaptar · P1 | Medio | Validar caché H2 y transición de esquema de 12.2.2, ejecución efectiva del escáner y recuperación; no omitir análisis. |
| [#16](https://github.com/LuisVera22/ondexia/pull/16) | setup-java 6.0.1 | adaptar · P1 | Bajo | Comprobar runtime Node 24 de la acción, Java 21, caché Maven y verificación de distribución en runner real. |
| [#17](https://github.com/LuisVera22/ondexia/pull/17) | Maven 3.10.0 | adaptar · P1 | Medio | Endurece modelos y credenciales; preservar comentarios españoles y finales de línea del wrapper. Verificar reactor, repositorios y herramientas. |
| [#18](https://github.com/LuisVera22/ondexia/pull/18) | pnpm/action-setup nuevo SHA | adaptar · P1 | Bajo | Validar procedencia del commit y diferencias; conservar packageManager, orden setup y lock congelado. Comparación oficial recibió 404. |
| [#19](https://github.com/LuisVera22/ondexia/pull/19) | upload-artifact 7.0.1 | adaptar · P1 | Medio | Coordinar con #14; archive=true conserva contrato, pero requiere ensayo de intercambio de artefactos y múltiples rutas. |
| [#20](https://github.com/LuisVera22/ondexia/pull/20) | Qute 3.40.1 | adaptar · P2 | Medio | Notas de 3.40.1 no prueban beneficio específico de Qute; revisar cambios acumulados y renderizados de XML/documentos con datos fijados. |
| [#21](https://github.com/LuisVera22/ondexia/pull/21) | cache/restore 6.1.0 | adaptar · P1 | Medio | La propuesta deja save en v4; revisar ambas acciones y ensayo de caché completa/fallo previo. No duplicar arreglo de caché existente. |
| [#22](https://github.com/LuisVera22/ondexia/pull/22) | AWS SDK BOM 2.55.11 | adaptar · P1 | Medio | Salto amplio; contrastar cambios acumulados, S3/originales y firma. Pruebas locales no demuestran IAM ni funcionamiento AWS. |
| [#23](https://github.com/LuisVera22/ondexia/pull/23) | sitemap 3.7.4 | integrar · P1 | Bajo | Corrección aplicable a trailingSlash=never; comprobar sitemap/canonical con URL esperada fijada, además de build. |
| [#24](https://github.com/LuisVera22/ondexia/pull/24) | Astro 7.3.6 | adaptar · P1 | Medio | Comprobar cambios acumulados, salida estática, rutas y activos. Corregir dependencias transitivas verificadas; no atribuir exposición SSR a landing estática. |
| [#25](https://github.com/LuisVera22/ondexia/pull/25) | @types/jasmine 7 | posponer · P2 | Medio | Motor Jasmine 5 actual: no aceptar API de tipos 7 sin coordinación y compatibilidad demostrada. Reevaluar al migrar motor o necesitar corrección concreta. |
| [#26](https://github.com/LuisVera22/ondexia/pull/26) | TypeScript 6 landing | posponer · P2 | Medio | check 0.9.10 acepta TS6, pero faltan revisión de migración/configuración y beneficio concreto. No afecta Angular; reevaluar con necesidad o fin de soporte. |
| [#27](https://github.com/LuisVera22/ondexia/pull/27) | Angular CLI 22 aislado | posponer · P2 | Alto | Angular restante sigue en 21; Node local 22.22.0 es inferior al mínimo 22.22.3 declarado. No usar este salto aislado para resolver avisos; reevaluar migración coordinada. |
| [#28](https://github.com/LuisVera22/ondexia/pull/28) | PostCSS 8.5.29 | integrar · P1 | Bajo | Parche compatible de correcciones CSS; validar ambos manifiestos/rangos del panel, lock, build y vistas pertinentes. |
| [#29](https://github.com/LuisVera22/ondexia/pull/29) | Zone 0.16.3 | integrar · P1 | Medio | Angular core 21.2.24 acepta ~0.15.0 o ~0.16.0. Probar async/fakeAsync, temporizadores, eventos y navegadores; no afirmar soporte de navegadores abandonados. |
| [#30](https://github.com/LuisVera22/ondexia/pull/30) | reporter Jasmine 2.3.0 | integrar · P2 | Bajo | Peers admiten motor Jasmine 5; verificar suite del panel y reporte. No es arreglo de seguridad demostrado. |

## Orden de ejecución dentro del backlog existente

1. ACT-19/23: caracterizar y corregir primero los avisos críticos/altos de la cadena
   de herramientas. Elevar este trabajo a P0 de evaluación y remediación según
   alcanzabilidad; no declarar incidente de producción. Buscar actualizaciones
   compatibles de Angular 21 y dependencias transitivas, sin asumir que #27 resuelve
   los avisos ni imponer Angular 22. Repetir auditoría completa, pruebas y builds;
   documentar avisos residuales y diferencias respecto a --prod.
2. P1: parches seleccionados (#13, #23, #28 y #29), en lotes pequeños desde develop.
   Para #23 fijar sitemap/canonical esperado según la configuración y la URL de negocio,
   sin derivarlo de la salida observada. Para #13 comprobar reglas arquitectónicas,
   porque cambia qué dependencias detecta.
3. ACT-19: migración coordinada del escáner/caché y acciones (#15/#21, #14/#19,
   #16/#18); conservar análisis efectivo y contratos de artefactos. Validar intercambio
   de artefactos sin activar CD ni desplegar.
4. Maven/AWS/Qute/Astro según cambios acumulados y utilidad demostrada; reporter
   después de riesgos mayores. Reexaminar las tres propuestas pospuestas al existir
   necesidad concreta, incompatibilidad resuelta, fin de soporte o aviso aplicable.

Cada lote tendrá especificación proporcional, comprobaciones de compatibilidad
y regresión. Una corrección de comportamiento necesita prueba previa fallida y
posterior correcta; una actualización de versión sin defecto demostrado no
justifica inventar una prueba roja. Integrar solo tras CI completo del mismo head
sobre develop; comprobar el CI del merge antes de cerrar su rama.
Las propuestas bot se cerrarán únicamente al quedar resueltas o descartadas con
motivo registrado; no borrarlas por antigüedad ni integrar sobre main.

## Fuentes y límites

Notas oficiales de ArchUnit, OWASP Dependency-Check, Maven, Quarkus, AWS SDK,
Astro, PostCSS, Angular y acciones GitHub enlazadas en el inventario. Se leyeron
también los action.yml de los SHA propuestos y los metadatos de versiones del
registro npm, conservados con huellas. Runtime Node 24 de una acción no equivale
a Node 22 del proyecto. No se ha comprobado la versión efectiva del runner futuro.

La comparación oficial de los commits pnpm devolvió 404; no se considera completada.
Las notas de una última versión no cubren automáticamente todos los cambios de
un salto amplio. La migración TypeScript 6 y los cambios acumulados AWS/Qute/Astro
quedan por contrastar; la lectura de peers no sustituye ejecución.

No verificado: compatibilidad ejecutada de versiones propuestas, integridad de
paquetes descargados, ausencia de otras vulnerabilidades/backend, alertas privadas,
explotación/alcanzabilidad, operación nueva del bot desde rama por defecto,
AWS, soporte contractual o auditoría independiente. Main conserva su configuración
hasta una publicación autorizada; no se cambia por esta revisión.
ACT-23/F23 y ACT-19 permanecen parciales.

