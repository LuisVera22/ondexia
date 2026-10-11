# Resolución de las propuestas de Dependabot

## Resultado final verificado

Las 16 propuestas del inventario están cerradas y no quedan ramas Dependabot
en remoto ni locales homónimas. Se consultó GitHub para cada PR y `git ls-remote`
para todas las referencias `refs/heads/dependabot/*`; no se infirió ausencia
desde referencias locales. Los heads se contrastaron antes de cada cierre y
eliminación; cuando el bot eliminó automáticamente una referencia, se verificó
su ausencia sin repetirla. Ningún PR bot se integró directamente sobre main.

Ocho propuestas adaptadas: #14/#16/#17/#19/#21/#28/#29/#30. Ocho no adoptadas
en este ciclo: #15/#18/#20/#22/#24/#25/#26/#27. Motivos y condiciones para retomar
las evaluaciones permanecen en el inventario y en cada PR; cerrar una rama no
demuestra compatibilidad de una versión descartada ni resuelve toda ACT-23.

| Lote | CI del PR completo verde | CI posterior de develop completo verde |
| --- | --- | --- |
| #60 Interfaz | [38180973076](https://github.com/LuisVera22/ondexia/actions/runs/38180973076) | [38181295647](https://github.com/LuisVera22/ondexia/actions/runs/38181295647) |
| #61 Maven | [38181482144](https://github.com/LuisVera22/ondexia/actions/runs/38181482144) | [38181894749](https://github.com/LuisVera22/ondexia/actions/runs/38181894749) |
| #62 Acciones | [38182171587](https://github.com/LuisVera22/ondexia/actions/runs/38182171587) | [38182481524](https://github.com/LuisVera22/ondexia/actions/runs/38182481524) |

Cada CI se contrastó con el commit exacto, todos sus jobs y sus pasos; los
merges usaron expected_head_sha. El último commit de código verificado es
`3c6cb284dea87adb5ac1f32c249907efd2749888`: 656 backend, 147 web, 8 panel,
35 herramientas y seis análisis efectivos correctos. Tres jobs finales verdes.
El transporte entre runners pasó tanto en PR como después del merge, con un
tar y varias rutas, bytes literales y digests coincidentes; caché anterior NVD
restaurada y analizada. No se provocó un digest remoto incorrecto.

[Resultado estructurado con heads y resolución individual](2026-10-11-cierre-dependabot-resultado.json).
Los tres feature de implementación terminaron y sus referencias remotas/locales
se eliminaron de forma segura después del CI posterior. El registro documental
final sigue el mismo ciclo de PR/CI y no añade pruebas de comportamiento.

No se ejecutaron CD, AWS ni Windows real, ni auditoría independiente/móvil.
Braces y smol-toml y los criterios de migración de las ocho no adoptadas siguen
en el backlog. Dependabot sigue habilitado: esto resuelve las propuestas del
ciclo, no impide nuevas propuestas semanales. La configuración de destino
develop no se atribuye a la rama por defecto mientras no se publique allí.

Los apartados siguientes conservan los cortes previos al CI; sus pendientes
temporales se resuelven con los resultados exactos anteriores, sin reescribir
la evidencia que existía al crear cada lote.

Por autorización del propietario se completan las decisiones y cierres de las
16 propuestas que estaban abiertas al comenzar este lote. No significa adoptar
todas las versiones ni cerrar las actividades de deuda técnica relacionadas.
[Inventario y decisión por PR](2026-10-11-cierre-dependabot-inventario.json),
[fuentes](2026-10-11-cierre-dependabot-fuentes.json),
[especificación](../especificaciones/2026-10-11-cierre-dependabot.md).

## Interfaz, primer lote

Se adaptan #28/#29/#30 sobre develop, con PostCSS 8.5.29, Zone 0.16.3 y reporter
2.3.0 en web y panel. Core Angular 21.2.24 admite ~0.16.0; los peers publicados
del reporter admiten Jasmine 5/Karma 6. No se adopta Angular 22 ni tipos Jasmine 7.

PostCSS: tres casos de comentarios en listas y escape style fallan con 8.5.26,
y pasan con 8.5.29. Los esperados literales proceden de los tests oficiales
8.5.26…8.5.29, no de salidas observadas. Comprobador incorporado al build de
ambas aplicaciones. No se demuestra una explotación en Ondexia.

Zone: tres controles de promesas/plazos, cancelación y evento con NgZone ya
pasaban con 0.15.1; no se presentan como defectos corregidos. Tres mutaciones
aisladas (plazo cambiado, cancelación omitida y registro fuera de NgZone) fallan
en sus respectivos tests. Restauración exacta y suite posterior correctas.

Instalación congelada, builds y suites correctos: 147 web y 8 panel. No se ha
ejecutado una auditoría visual móvil ni un navegador móvil real en este lote.
Los cambios propios son dependencias, controles y documentación; no templates.

El primer recorte del lock duplicó la clave PostCSS 8.5.29, ya presente como
transitiva. pnpm rechazó el lock antes de instalar. Se restauraron ambos desde
HEAD, se regeneraron, se deduplicó esa clave y se repitieron todos los controles.
Los cambios incidentales de Babel/browserslist se excluyeron. El error de
preparación no se cuenta como rojo de comportamiento ni se publicó.

Auditoría completa posterior: un alto de braces por aplicación; cero moderados
y críticos. Sigue pendiente, sin excepción aceptada. CI del PR y posterior en
develop pendientes al registrar. Artefactos y huellas en
[el resumen del lote](2026-10-11-cierre-dependabot-interfaz.json).

## Propuestas que no se adoptan en este ciclo

#15 requiere ensayo de migración/recuperación de la base del escáner; #18 no
permitió contrastar la comparación oficial de sus SHAs (404); #20 no acredita
una mejora concreta de Qute fiscal en el changelog global; #22 no acredita los
cambios acumulados de nuestros clientes del SDK; #24 presenta correcciones
principalmente SSR/HMR sin defecto reproducido en esta landing estática;
#25/#27 son saltos aislados de tipos/CLI incompatibles con una migración
coordinada; #26 cambia el compilador mayor sin necesidad concreta identificada.

Se conserva el motivo preciso, la versión y la condición para retomar cada
evaluación en el inventario. Estos cierres resuelven la propuesta actual:
ACT-19/23 y F15/F20/F24 siguen parciales. No se agregan reglas ignore ni se
desactiva Dependabot. No se afirma ausencia de CVE por mantener una versión.
Las propuestas seleccionadas se cierran solo tras la integración equivalente
y su CI posterior verde; las no adoptadas, tras publicar y comprobar la decisión.
Verificar el head nuevamente y la ausencia remota de cada rama al cerrar.
