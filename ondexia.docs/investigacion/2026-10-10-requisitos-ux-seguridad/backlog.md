# Aplicación de la investigación al backlog de fundamentos

Estado: propuesta, actividades **pendientes**. La investigación documental y la
inspección estática descritas en README están realizadas; no se confunden con
ejecutar las actividades siguientes. No se inicia funcionalidad nueva.

Este backlog detalla partes de F07–F11, F16–F26 del
[plan de fundamentos](../../planes/2026-10-10-fundamentos-arquitectura-sdlc.md).
Sus IDs se enlazan con Fxx en vez de mantener dos catálogos independientes de
trabajo realizado. P0 indica investigación/corrección prioritaria por impacto,
**no puntuación CVSS ni vulnerabilidad demostrada**. Algunas actividades producen
decisiones o evidencia antes de cualquier cambio de código.

## 1. Requisitos y casos de prueba

| ID / vínculo | Prioridad | Entregable y aceptación | Dependencia |
|---|---|---|---|
| ACT-01 · F07/F10/F18/F21 | P1 | Adoptar plantillas y matriz: una necesidad real queda enlazada a decisión, historia, reglas, flujo/extensiones y pruebas; preguntas/estados visibles | Revisión del formato |
| ACT-02 · F10/F18/F25 | P1 | Especificar íntegro el alta contextual de cliente desde reglas existentes; tabla de decisiones, permisos, respuestas tardías, cancelación y guardado incierto | ACT-01; decisiones vigentes |
| ACT-03 · F18/F21 | P1 | Contrastar suite actual con cada regla/extensión del piloto; identificar cubiertos, huecos, oráculos dudosos y premisas externas | ACT-02 |
| ACT-04 · F18/F19/F25 | P1 | Completar pruebas por riesgo; fallo sin arreglo para defectos y mutación aislada para controles existentes; contrato/API/SQL/UI según frontera | ACT-03; decisiones faltantes resueltas |
| ACT-05 · F08/F28 | P1 | Ficha económica del piloto: frecuencia, soporte, ahorro del cliente y rentabilidad Ondexia separados; escenarios sin precios ni adopción inventados | Necesidad priorizada; datos del propietario |

## 2. UX/UI

| ID / vínculo | Prioridad | Entregable y aceptación | Dependencia |
|---|---|---|---|
| ACT-06 · F11/F15/F25 | P1 | Inventario de tareas, componentes y estados de ventas; hallazgos vinculados a interacción, no solo apariencia | ACT-02 |
| ACT-07 · F11/F15/F25 | P1 | Revisión de accesibilidad del piloto con objetivo WCAG 2.2 AA: teclado, foco, errores, contraste, reflujo y estados; criterios pendientes explícitos | ACT-06 |
| ACT-08 · F15/F25 | P1 | Prototipo acotado de fricciones prioritarias utilizando sistema visual actual; cubre desktop/móvil/carga/error y consecuencias | ACT-06/07 |
| ACT-09 · F25/F28 | P1 | Ronda de uso con tareas neutrales y participantes pertinentes; resultados y ayudas registrados; hallazgos por impacto y recuperación | ACT-08; participantes disponibles |
| ACT-10 · F15/F20/F25 | P1 | Ajustes seleccionados con evidencias visuales y pruebas pertinentes de interacción; sensibilidad de controles afectados; sin rediseño general implícito | ACT-09; alcance presentado |
| ACT-11 · F28 | P2 | Comparación de resultado con línea base bajo condiciones equivalentes; errores, ayuda, tiempo y soporte; sin inferencia estadística indebida | ACT-05/09/10 |

## 3. Seguridad

| ID / vínculo | Prioridad | Entregable y aceptación | Dependencia |
|---|---|---|---|
| ACT-12 · F11/F16/F24 | P0 | Matriz de activos/amenazas/requisitos ASVS 5.0.0 con aplicabilidad, configuración/prueba/resultado y límites; reconciliar DTE con S3 y controles reales | Inventario F01 |
| ACT-13 · F19/F24/F25 | P0 | Caracterizar y cerrar aceptación con audiencia configurada vacía; comprobar falla cerrada y algoritmos/uso/audiencia de JWT | SEC-01/02; prueba de regresión |
| ACT-14 · F17/F24 | P0 | Ensayo de clave Cognito nueva y diseño de actualización segura; evidencia de recuperación y costos; retirar premisa externa incorrecta | SEC-03; entorno controlado |
| ACT-15 · F16/F18/F24 | P0 | Matriz negativa de permisos/aislamiento por lectura y escritura, cambios de contexto, objetos y rutas; rol real restringido y controles externos pendientes | SEC-04; contrato de errores vigente |
| ACT-16 · F16/F24/F25 | P0 | Ensayo de bytes falsos, tamaño/dimensiones y reutilización de subida; definir publicación y referencia histórica inmutable; corregir defectos demostrados | SEC-05/06; ensayo S3 aislado si necesario |
| ACT-17 · F18/F24 | P1 | Flujo de datos de XML/ZIP y pruebas hostiles locales; controles parser/transformación y consumo; diferenciar entradas externas e internas | SEC-07 |
| ACT-18 · F11/F12/F24 | P1 | Decisión de sesión/revocación/rotación y comparación SPA/BFF; tests de contrato y límites; no incorporar servidor adicional por defecto | SEC-08; política de sesión vigente |
| ACT-19 · F20/F23/F24 | P0 | Control de análisis de dependencias no ejecutado visible y puerta de publicación definida; prueba de falta de credencial y scanner fallido | SEC-10; reglas CI/CD |
| ACT-20 · F20/F24 | P1 | Elegir controles estáticos de código/secretos/IaC con prueba y costo; verificar cabeceras reales por sitio; no dar herramientas por instaladas | SEC-09/11/12 |
| ACT-21 · F11/F24/F28 | P1 | Clasificación, accesos de soporte, retención, respuesta a incidente y ensayo de recuperación; tiempos aprobados o pendientes y evidencia sanitizada | ACT-12; datos/entornos autorizados |
| ACT-22 · F26 | P1 | Contraste independiente del piloto contra decisiones/commit; discrepancias reproducibles y riesgo residual explícito | Piloto implementado y verificado |
| ACT-23 · F01/F20/F23/F24 | P1; elevar casos confirmados a P0 | Revisar los PR de Dependabot; decidir por dependencia integrar, adaptar, posponer o descartar, con compatibilidad, riesgo, costo, pruebas y evidencia; alinear destino develop y Conventional Commits | Inventario por PR; ACT-19 para controles de análisis; alcance técnico presentado |

## 4. Orden recomendado y puertas

1. Revisar matriz de estado y seleccionar el piloto. Consolidar ACT-01/02 y ACT-12;
   caracterizar P0 (13–16 y 19), porque afectan confianza y datos/operaciones.
2. Completar cobertura de requisitos/pruebas y diagnóstico UX (03, 06, 07).
3. Presentar cambios acotados, con costos/compatibilidad y decisiones necesarias;
   implementar cada corrección autorizada con su evidencia. Los P0 no se reúnen
   en una refactorización gigante.
4. Prototipar/contrastar ajustes de uso (08–10) y completar controles seleccionados
   de operación/código (17–21). Mantener fases y alcance comercial del producto.
5. Auditoría independiente (22) y medida de resultados (05/11); ajustar prioridades
   según hallazgos reales.

Propietario: valida necesidad, negocio, políticas no definidas y presupuesto.
Codex: prepara análisis, alternativas, diseño, controles y evidencia autorizados.
Usuarios: contrastan tareas mediante experiencia/observación. Auditor: sesión
separada sin narrativa heredada. No se afirma que estos roles sean agentes instalados.

El cierre de un lote exige especificación revisada, decisión registrada, evidencia
pertinente y CI del commit cuando haya código. Una investigación documental no
requiere fingir TDD. Los cambios se integran según GitFlow y Conventional Commits;
no tocar main, desplegar, instalar servicios o hacer pruebas ofensivas externas
por el solo hecho de figurar en este backlog.


## 5. ACT-23: revisión y selección de actualizaciones de Dependabot

Solicitud del propietario, 2026-10-10: incorporar esta revisión al backlog
existente y priorizarla junto con la deuda. Responsable del análisis y de la
recomendación técnica: Codex. Estado: **pendiente de evaluación individual**;
registrar una propuesta no acredita su compatibilidad ni autoriza integrar en main.

### Línea base verificada

GitHub muestra 18 ramas `dependabot/*`, asociadas a los PR abiertos #13–#30,
creados por `dependabot[bot]` el 2026-10-09. Todos apuntan a `main` y ninguno
tiene integración automática activada. La configuración actual no declara
`target-branch`, programa revisiones semanales y permite hasta cinco propuestas
por ecosistema/directorio; utiliza el prefijo `Actualiza`.

Estos dos desajustes deben corregirse dentro del lote: destino `develop` y
Conventional Commits con ámbito/descripción en español. No reescribir mensajes
históricos. Evaluar cómo aplicar la convención a los títulos y mensajes generados
por el bot y al commit de integración; comprobar el resultado real, sin afirmar
que un prefijo por sí solo traduce la descripción generada.

| Grupo de propuestas | PR | Qué revisar antes de decidir |
|---|---|---|
| GitHub Actions | #14, #16, #18, #19, #21 | Runtime del runner, permisos, cambios de entradas/salidas, integridad de artefactos y caché; mantener referencias por SHA |
| Backend y construcción | #13, #15, #17, #20, #22 | Java/Maven, contratos de ArchUnit, base y caché de Dependency-Check, plantillas Qute y SDK AWS; compatibilidad de reactor y adaptadores |
| Administración Angular | #25, #27, #28, #29, #30 | Compatibilidad conjunta de Angular, CLI, Zone.js y herramientas de pruebas; no actualizar paquetes relacionados como si fueran independientes |
| Landing | #23, #24, #26 | Compatibilidad Astro/sitemap/TypeScript, construcción y regresiones de navegación y contenido |

Enlaces: [PR de Dependabot](https://github.com/LuisVera22/ondexia/pulls?q=is%3Apr+is%3Aopen+author%3Aapp%2Fdependabot).
Las versiones, heads, estado y necesidad deben consultarse nuevamente al ejecutar;
esta línea base no constituye resultado del CI ni auditoría de vulnerabilidades.

### Prioridad frente a las actividades existentes

1. Terminar la revisión/integración del bloque ACT-13 en curso, manteniendo su
   auditoría independiente. Continuar F01 y la caracterización de autenticación
   y aislamiento ACT-14/15.
2. Hacer el triaje inicial de ACT-23 en ese primer lote de inventario: registrar
   avisos de seguridad aplicables y bloqueos reales de construcción/CI. Si una
   propuesta corrige un riesgo confirmado o desbloquea un control indispensable,
   elevar ese caso a P0 y atenderlo antes del siguiente lote no urgente.
3. Revisar las mejoras del escáner y del CI junto con ACT-19; contrastar primero
   la puerta de producción y la caché existentes, sin duplicarlas ni desactivar
   controles para obtener verde.
4. Ejecutar actualizaciones compatibles seleccionadas como P1, después de los
   riesgos P0 confirmados de identidad/aislamiento, dinero e historia
   (F03–F06 y ACT-16 según evidencia), y antes del reformateo o renombrado general
   y mejoras cosméticas. No detener caracterizaciones independientes por una
   migración de herramientas.
5. Posponer como P2 los saltos mayores sin necesidad demostrada que requieran
   migración amplia; reevaluar si cambia soporte, exposición o beneficio.

El orden es por riesgo y dependencias, no por número de PR ni por versión más
reciente. No se afirma que los 18 PR solucionen vulnerabilidades. El tiempo y
costo de migración se estimarán por propuesta; no se inventan cifras de retorno.

### Entregable, decisión y cierre

Una matriz por PR incluirá: dependencia y versiones; head actual y base;
aviso de seguridad y aplicabilidad o ausencia de evidencia; soporte;
compatibilidad y paquetes relacionados; beneficio, esfuerzo y riesgo de
regresión; decisión **integrar / adaptar / posponer / descartar**, motivo,
pruebas, CI y siguiente revisión cuando corresponda. Lo desconocido queda
explícito. Las decisiones nuevas de negocio, presupuesto o excepciones al
contrato se consultan al propietario.

Cada lote elegido debe presentar alcance y comprobaciones antes de implementar.
Validar desde `develop` actualizado en rama separada, sin mezclarlo con ACT-13
ni el manual de arquitectura. Una propuesta obsoleta se contrasta con las
versiones ya integradas antes de cerrarla. Para un arreglo de comportamiento,
prueba roja/verde; para un control ya correcto, mutación aislada y restaurada.
Para una actualización documental o de versión sin defecto demostrado,
comprobaciones de compatibilidad y regresión pertinentes, sin inventar TDD.

La recomendación de integrar requiere controles completos del mismo head,
revisión pertinente y autorización aplicable; después comprobar CI del commit
integrado en develop y eliminar únicamente la rama correspondiente si está
integrada y no tiene commits nuevos. Un descarte registra el motivo antes de
cerrar su PR y retirar su rama según autorización. No integrar automáticamente
los 18 PR, eludir protecciones, tocar main ni desplegar en AWS.
