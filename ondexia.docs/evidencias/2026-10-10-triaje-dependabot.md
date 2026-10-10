# ACT-23: triaje inicial de Dependabot

Fecha: 2026-10-10. Base local: develop `0df3f6d57a8b0dcece6f7667ce605c7fa307dc15`.
Estado: análisis inicial, no integración. Complementa [ACT-23 del backlog](../investigacion/2026-10-10-requisitos-ux-seguridad/backlog.md#5-act-23-revisión-y-selección-de-actualizaciones-de-dependabot).

## Estado observado y límites

Se consultaron los 18 PR abiertos #13–#30 y los check-runs de sus heads exactos.
Todos tienen un check build completado con éxito; apuntan a main y no tienen
integración automática activada. Es evidencia de esos heads y esa base: no autoriza
reutilizar resultados tras cambiar a develop ni sustituye pruebas del paquete.

Se contrastaron ci.yml, los manifiestos de administración/landing y las versiones
Maven del checkout. El CI declarado incluye web, landing, panel interno, backend,
contratos e infraestructura. No se afirma ejecución efectiva de cada paso para
los 18 PR: solo se detallaron jobs y log de #15.

En [CI de #15](https://github.com/LuisVera22/ondexia/actions/runs/37964915726), job
113936750543, head `eae6d4082b61388ef9b4b6bc2b49f757847baf80`, el log registra
NVD_API_KEY vacía y el aviso «OWASP Dependency-Check no se ejecutó». El build pasó,
pero la actualización del propio escáner **no fue analizada por ese escáner**.
No se deduce que falte la credencial en todo el repositorio: el contexto del bot
puede disponer de credenciales distintas. Acceso a secretos y SCA de los otros
17 PR: no verificados. Mantener la puerta de publicación existente de ACT-19;
no usar pull_request_target para ejecutar código del PR con secretos elevados.

El diff de [#27](https://github.com/LuisVera22/ondexia/pull/27/files) cambia
angular/cli de ^21.2.2 a ^22.2.1 y su lockfile; deja build y compiler-cli en la
familia 21. Esta diferencia exige revisar compatibilidad conjunta, aunque el
build de ese head esté verde. No se ha demostrado un fallo de ejecución.

## Decisión inicial por propuesta

«Integrar, condicionado» selecciona un candidato para validar, no certifica su
compatibilidad ni concede merge inmediato. Las recomendaciones se basan en alcance
y relaciones de versiones, todavía sin ensayo local ni revisión exhaustiva de
avisos de seguridad/release notes. Ningún caso se eleva a P0 por una CVE inventada.

| PR | Head consultado | Prioridad | Decisión inicial | Motivo y comprobación pendiente | Check build |
|---|---|---|---|---|---|
| [#13](https://github.com/LuisVera22/ondexia/pull/13) | `3def5cdb79d9fa75fdb47dcf9397ef2ef9f83ed5` | P1 · primer candidato | Integrar, condicionado a validación en develop | Parche ArchUnit; comprobar cambios en detección de dependencias y sensibilidad de las nueve reglas. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37964895568/job/113936681285) |
| [#14](https://github.com/LuisVera22/ondexia/pull/14) | `ea9d6ec71273ea7080c2a6a9d7cba21ac081f3aa` | P1 | Adaptar | Coordinar con #19; validar integridad, entradas y comportamiento de artefactos en CI/CD antes de renovar sus SHA. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37964909180/job/113936728151) |
| [#15](https://github.com/LuisVera22/ondexia/pull/15) | `eae6d4082b61388ef9b4b6bc2b49f757847baf80` | P1; diagnóstico ACT-19 inmediato | Adaptar | Priorizar análisis con ACT-19: el log confirma Dependency-Check omitido. Evaluar salto 12→13, formato de base/caché y análisis efectivo; no basta el check verde. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37964915726/job/113936750543) |
| [#16](https://github.com/LuisVera22/ondexia/pull/16) | `7ab4db6967e72a4671d81c0ad3cafe99cd2900f5` | P1 | Integrar, condicionado a validación en develop | Verificar Java 21, distribución, caché Maven y uso en todos los workflows; no alterar versiones por el número mayor de la acción. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37964929591/job/113936792913) |
| [#17](https://github.com/LuisVera22/ondexia/pull/17) | `a1890b3b510715a701d883f7d69739ec6fba82cd` | P2 | Posponer | Cambio Maven 3.9→3.10 sin necesidad demostrada. Evaluar plugins, wrapper y reproducibilidad antes de migrar. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37964934138/job/113936807943) |
| [#18](https://github.com/LuisVera22/ondexia/pull/18) | `fd046ef03139959a9426a06e5e1e2732ef3d2519` | P1 | Integrar, condicionado a validación en develop | Renovación de SHA de pnpm/action-setup; contrastar versión real, entradas y packageManager de las tres aplicaciones. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37964940176/job/113936827078) |
| [#19](https://github.com/LuisVera22/ondexia/pull/19) | `24a0e9b0bd538d1c621932654befc11fb82c3967` | P1 | Adaptar | Coordinar con #14; verificar carga/descarga y formato de artefactos, incluido informe de seguridad. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37964951266/job/113936863006) |
| [#20](https://github.com/LuisVera22/ondexia/pull/20) | `69ff095d930fe66e9b9e3c5c14eb36f78f72109c` | P1 | Integrar, condicionado a validación en develop | Evaluar Qute y consumidor en emisión: plantillas y XML contra ejemplos independientes; no tratar build como aceptación fiscal. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37964954670/job/113936873419) |
| [#21](https://github.com/LuisVera22/ondexia/pull/21) | `1c15062a5d436eeab0526917953f321b58b99d09` | P1 | Adaptar | Validar restore/save juntos y contrato de caché NVD existente; mantener controles de copia completa y reintentos. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37964966341/job/113936910796) |
| [#22](https://github.com/LuisVera22/ondexia/pull/22) | `77f78524641c99d58d36b8d2bcab52da7be97690` | P1 | Adaptar | Salto amplio del BOM AWS: revisar adaptadores S3 y módulos consumidores, IAM no acreditado por dobles locales; estimar alcance antes de elegir versión. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37964980609/job/113936957323) |
| [#23](https://github.com/LuisVera22/ondexia/pull/23) | `f12a7df346899b8949a0861ba0e56ab7ccea923a` | P1 · primer candidato | Integrar, condicionado a validación en develop | Parche sitemap; comprobar rutas, URLs y contenido generado de la landing. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37965016957/job/113937077316) |
| [#24](https://github.com/LuisVera22/ondexia/pull/24) | `c7173e4ddd616c2eee2a805a236eaac2ee1dc0a1` | P1 | Integrar, condicionado a validación en develop | Astro 7.2→7.3: comprobar versión de Node, check/build, integración Tailwind y comportamiento de la landing. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37965053550/job/113937196985) |
| [#25](https://github.com/LuisVera22/ondexia/pull/25) | `af5feffaf9adc5e88613585cf45cd0d8af63ebe4` | P2 | Posponer | Tipos Jasmine 5→7 mientras jasmine-core sigue en ~5.6; tratar como migración coordinada del contrato de pruebas. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37965087616/job/113937309651) |
| [#26](https://github.com/LuisVera22/ondexia/pull/26) | `78139d4bacbeb9ec090b344999792731b3bbe168` | P2 | Posponer | TypeScript 5→6: comprobar soporte de Astro/check y configuración; no adelantar sin necesidad o beneficio probado. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37965090516/job/113937319711) |
| [#27](https://github.com/LuisVera22/ondexia/pull/27) | `ffe80e160607077936e07195b8d140939cb22d1d` | P2 | Adaptar y posponer migración mayor | El diff cambia solo CLI a ^22.2.1; build/core/compiler continúan en 21. No adoptar como actualización aislada; especificar migración conjunta si se justifica. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37965127452/job/113937446658) |
| [#28](https://github.com/LuisVera22/ondexia/pull/28) | `f1c2d8a0bfabe8356b1d5ec0135a470af4cc4cf3` | P1 · primer candidato | Integrar, condicionado a validación en develop | Parche PostCSS; comprobar ambos manifiestos/rangos, lockfile, build y CSS resultante. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37965195853/job/113937681358) |
| [#29](https://github.com/LuisVera22/ondexia/pull/29) | `19d30a0bd9a7bc5fb92be29cf402327877eeab4f` | P2 | Posponer | Zone.js 0.15→0.16: verificar contrato de Angular 21 y pruebas asíncronas antes de cualquier cambio. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37965230104/job/113937799055) |
| [#30](https://github.com/LuisVera22/ondexia/pull/30) | `b91dff7fee221c9e6979fb7287fbc950756e579b` | P1 | Integrar, condicionado a validación en develop | Reporter Jasmine 2.1→2.3; comprobar compatibilidad con jasmine-core 5.6 y reporter en test:ci. | [success](https://github.com/LuisVera22/ondexia/actions/runs/37965256263/job/113937888096) |

## Orden de trabajo y aceptación

1. ACT-19: explicar y resolver el análisis omitido para las contribuciones del bot
   por un mecanismo seguro; alinear destino develop y títulos/commits. La ausencia
   de análisis en #15 es una limitación confirmada, no prueba de vulnerabilidad.
2. Primeros candidatos pequeños: #13, #23 y #28, en lotes separados por ecosistema.
   Comprobar primero si develop ya contiene la versión o la propuesta está obsoleta.
3. Revisar grupo Actions (#14/16/18/19/21) y #15 con cache/artefactos/escáner reales;
   no actualizar restore y save de forma incoherente.
4. Evaluar Qute/Astro/reporter (#20/24/30) y BOM (#22) por consumidores y costo;
   mantener los riesgos P0 de identidad, aislamiento, dinero e historia por delante.
5. Migraciones mayores #17/25/26/27/29 pospuestas hasta justificar beneficio,
   compatibilidad y esfuerzo. Reabrir prioridad si aparece un aviso aplicable.

Faltan release notes y avisos oficiales por versión, aplicabilidad y soporte,
esfuerzo estimado y pruebas de compatibilidad en develop. Cada propuesta debe
actualizar esta matriz con evidencia y decisión final. No cerrar ni borrar ramas
por esta selección; seguir las puertas de PR/CI/integración y autorizaciones
vigentes. No se modifica main, el código de terceros ni AWS.
