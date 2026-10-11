# Resolución de las propuestas de Dependabot

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
