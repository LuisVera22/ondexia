# Aplicación de la investigación al backlog de fundamentos

Estado: ejecución progresiva autorizada; revisión de las 23 actividades actualizada
en §7 al 2026-10-11. ACT-13 cerrado en su alcance; las demás conservan pendientes.
La investigación documental y la
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
| ACT-14 · F17/F24 | P0 | Ensayo de clave Cognito nueva y diseño de actualización segura; evidencia de recuperación y costos; retirar premisa externa incorrecta. [Caracterización local](../../evidencias/2026-10-10-rotacion-jwt.md) realizada; recuperación automática y panel pendientes | SEC-03; entorno controlado |
| ACT-15 · F16/F18/F24 | P0 | Matriz negativa de permisos/aislamiento por lectura y escritura, cambios de contexto, objetos y rutas; rol real restringido y controles externos pendientes. [Primer corte SQL local](../../evidencias/2026-10-10-aislamiento-multiempresa.md): almacen y rol ondexia_app; no cierra el resto de rutas/tablas. [Identidad de resultados de emisión](../../especificaciones/2026-10-11-identidad-resultados-emision.md): empresa/operación del mensaje antes de transición; no sustituye RLS | SEC-04; contrato de errores vigente |
| ACT-16 · F16/F24/F25 | P0 | Ensayo de bytes falsos, tamaño/dimensiones y reutilización de subida; definir publicación y referencia histórica inmutable; corregir defectos demostrados | SEC-05/06; ensayo S3 aislado si necesario |
| ACT-17 · F18/F24 | P1 | Flujo de datos de XML/ZIP y pruebas hostiles locales; controles parser/transformación y consumo; diferenciar entradas externas e internas. [Primer corte del lector CDR](../../especificaciones/2026-10-11-lectura-cdr-acotada.md): lectura acotada y diagnósticos fijos; [recepción HTTP acotada](../../especificaciones/2026-10-11-descarga-soap-acotada.md) verificada localmente. XML interno, presupuesto global de recursos y vinculación pendientes | SEC-07 |
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
recomendación técnica: Codex. Estado: **evaluación individual documentada; ejecución pendiente**;
registrar una propuesta no acredita su compatibilidad ni autoriza integrar en main.

### Línea base del triaje, 2026-10-10

En ese triaje GitHub mostró 18 ramas `dependabot/*`, asociadas a los PR abiertos #13–#30,
creados por `dependabot[bot]` el 2026-10-09. Todos apuntan a `main` y ninguno
tiene integración automática activada. La configuración de esa base no declara
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


## 6. Avance de ACT-23

[Revisión individual y auditorías actuales](../../evidencias/2026-10-11-revision-dependabot.md):
cinco candidatas a integrar, diez a adaptar y tres pospuestas. Los 18 PR siguen
abiertos hacia main; ninguna versión propuesta se ha ejecutado en develop.
La auditoría completa de los lockfiles detecta avisos críticos en piscina y
proxy-addr de herramientas Angular: priorizar su caracterización/remediación
en ACT-19/23 antes de mantenimiento cosmético, sin afirmar explotación ni
equivalencia con el análisis --prod del CI. ACT-23 permanece parcial.

[Triaje inicial de las 18 propuestas](../../evidencias/2026-10-10-triaje-dependabot.md):
heads y checks consultados, recomendaciones condicionadas y migraciones mayores
pospuestas. Confirmado en el log de #15: análisis Dependency-Check omitido aunque
build esté verde. Se coordina con ACT-19. Faltan avisos oficiales/release notes,
aplicabilidad, pruebas en develop y decisión final; no se integró ni cerró un PR.

[Corte de configuración de propuestas](../../especificaciones/2026-10-11-propuestas-dependabot.md), 2026-10-11: develop y prefijo convencional comprobados localmente; no traduce el texto nativo del bot ni actualiza los 18 PR existentes. Configuración activa/propuesta real pendientes; no se cambia main.

## 7. Seguimiento actualizado de las 23 actividades, 2026-10-11

ACT-13: regresión de cuatro configuraciones inválidas y diez casos JWT previos;
fallo sin arreglo y verificación local después. Ver
[especificación](../../especificaciones/2026-10-10-audiencia-jwt.md) y
[evidencia](../../evidencias/2026-10-10-audiencia-jwt.md).
CI e integración de ACT-13 comprobados en el PR #33. La auditoría independiente
permanece pendiente en ACT-22/F26. F01 tiene inventario inicial
en §11 del plan y revisión actual en §12; no está completo. ACT-14/15 y los demás puntos no se cierran
por esta corrección. No se ha probado configuración desplegada en AWS.

Base y método: [plan, §12](../../planes/2026-10-10-fundamentos-arquitectura-sdlc.md).
La tabla mantiene los criterios de §1–3 y actualiza su estado; no crea otro catálogo.

| ID | Estado | Contraste / alcance | Próximo entregable y dependencia |
|---|---|---|---|
| ACT-01 | Parcial | Plantillas y requisitos/diseno/tareas de ajustes de ventas: necesidad real y decisiones; cadena incompleta | Aplicar matriz hasta regla, extensión, prueba y medida |
| ACT-02 | Parcial | Alta contextual: consulta exacta, revisar antes de guardar, permisos, duplicado y cambio de contexto | Especificación íntegra con tabla de decisiones, cancelación y guardado incierto; no inferir políticas nuevas |
| ACT-03 | Parcial | Spec de alta: 13 casos sobre revisión, permisos, respuesta tardía/otro RUC, existente/inactivo, manual, 409, doble pulsación y atestación | Trazar cada regla/extensión; fallo tras guardar sin respuesta, reintento, conservación de venta y efectos API |
| ACT-04 | Parcial | Cortes de dinero/historia/JWT/SQL/CDR/HTTP/resultado registran rojo-verde o mutación pertinente | Completar negativos, límites y concurrencia según ACT-03; oráculos independientes |
| ACT-05 | Pendiente | Valor hipotético escrito; sin ficha económica con frecuencia/costos reales | Datos del propietario y análisis cliente/Ondexia; no inventar adopción ni tarifa |
| ACT-06 | Parcial | Fuentes de mostrador/alta/registro/previsualización identificadas; estados consultando/guardando/revisando/fallo | Matriz tarea→componente→estado→recuperación, permisos y dispositivos |
| ACT-07 | Parcial | Modal implementa Escape, foco inicial, ciclo Tab y retorno; alta tiene labels/alert/status. Inspección no prueba interacción/contraste | Ensayo teclado/lector/reflujo/errores; asociar errores al campo y probar estados dinámicos; objetivo WCAG 2.2 AA |
| ACT-08 | Pendiente | No hay prototipo nuevo contrastado con ACT-06/07; vistas actuales no sustituyen ese entregable | Prototipo de fricción prioritaria después del diagnóstico |
| ACT-09 | Pendiente | No hubo ronda con participantes; capturas del implementador no son estudio de usuarios | Acordar participantes y tareas neutrales; registrar ayuda/error/recuperación |
| ACT-10 | Parcial | Registro/previsualización y responsividad tienen pruebas/capturas de sus cortes | Seleccionar ajustes por diagnóstico/ronda; demostrar interacción y sensibilidad afectada |
| ACT-11 | Pendiente | Sin línea base comparable ni resultado de ronda | Medir antes/después en condiciones equivalentes; ACT-05/09/10 |
| ACT-12 | Parcial | Seguridad inventaría activos y SEC-01–12; no selecciona todos los IDs ASVS/configuración/prueba | Matriz priorizada ASVS 5.0.0 y límites; no declarar nivel 2 por selección parcial |
| ACT-13 | Cerrado | PR #33 integrado: cuatro configuraciones inválidas y diez casos JWT; falla cerrada/regresión y sensibilidad en alcance local/CI | Mantener regresión; AWS efectivo/auditoría siguen fuera de este cierre, ACT-22/F24 |
| ACT-14 | Parcial | PR #34 caracteriza claves nuevas/JWKS inmutable; reconstruir decoder local recupera; refresco automático/panel pendientes | Recuperación compatible con red privada/costos y prueba, sin añadir NAT por defecto |
| ACT-15 | Parcial | PR #36 rol restringido/almacen; #46 empresa/operación de resultados; no todas las rutas/objetos | Matriz negativa lectura/escritura/contexto/objeto; distinguir SQL, mensaje y AWS |
| ACT-16 | Parcial | PR #31/#40 conservan versión histórica; [corte de contenido](../../evidencias/2026-10-11-contenido-logos.md): falso PNG/formato/truncado rechazados, lectura/expansión acotadas y 55 pruebas locales correctas | [Escritura única](../../especificaciones/2026-10-11-escritura-unica-logos.md) configurada y comprobada localmente en firma SDK/cliente/doble/CORS; contrastar S3 efectivo y publicación previa a confirmación; AWS/CDN y presupuesto global no verificados |
| ACT-17 | Parcial | PR #44/#45 limitan CDR y recepción HTTP con entradas hostiles locales; #43 verifica XML local | XML interno, presupuesto global y vinculación/autenticidad del resultado; no SUNAT real |
| ACT-18 | Parcial | SesionService usa PKCE/sessionStorage; Terraform declara revocación y refresh rotation; JWT offline tiene límites | Precisar políticas/contratos y comparar SPA/BFF con costo; no dar rotación por ausente |
| ACT-19 | Parcial | CI advierte/omite SCA sin credencial; deploy ya bloquea producción sin NVD_API_KEY; caché corregida | [Sensibilidad local de la puerta](../../evidencias/2026-10-11-puerta-dependencias.md): 7 casos y 3 mutaciones detectadas/restauradas; no duplica puerta. Motor/CVE real y vigencia/cobertura NVD no acreditados por el sustituto; verde con skip no es SCA |
| ACT-20 | Parcial | SHA/TypeScript estricto/auditoría pnpm; sin SAST/IaC elegido/ejecutado ni cabeceras reales comprobadas | Selección proporcional y prueba; Terraform declarativo no es respuesta servida |
| ACT-21 | Parcial | Procedimiento de originales preparado; no clasificación/soporte/retención/incidentes generales | Propuesta y decisiones del propietario; ensayos sintéticos, tiempos pendientes; caso histórico real diferido |
| ACT-22 | Pendiente | Sin auditoría independiente de este commit; informes antiguos no sustituyen evaluación actual | Sesión/revisor separados, commit y reglas sin narrativa; contrastar hallazgos |
| ACT-23 | Parcial | PR #35 triaje; #13–#30 siguen abiertos/base main; [revisión individual](../../evidencias/2026-10-11-revision-dependabot.md) con notas oficiales, compatibilidad declarada y auditorías actuales; configuración en develop declara destino y prefijo | Priorizar avisos críticos/altos de herramientas; ejecutar versiones seleccionadas en develop; contrastar operación real/publicación de configuración y mensajes españoles; cerrar solo ciclos terminados |

**Balance ACT: 23 revisados, 1 cerrado en su alcance, 17 parciales, 5 pendientes.**
Con Fxx: 51 entradas revisadas, 1 cerrada, 41 parciales y 9 pendientes. Son IDs
vinculados, no 51 funcionalidades independientes ni 50 defectos. Un ACT cerrado
no cierra automáticamente el criterio amplio Fxx.

Orden actualizado en §12.3 del plan: logos, sensibilidad DevSecOps/bot, dinero,
recuperación, convenciones y casos de uso/UX. Economía, participantes y auditoría
requieren fuentes pertinentes. La revisión documental de todos los IDs está
realizada; implementar y aceptar sus pendientes sigue siendo trabajo del backlog.

F17: [frontera transaccional de publicación](../../evidencias/2026-10-11-confirmacion-emision.md)
corregida con prueba previa fallida y posterior correcta. PostgreSQL real con bus
simulado caracteriza rollback y fallo posterior al commit. ACT-14 y F17 siguen
parciales: esto no resuelve refresco JWKS, entrega automática ni resultados tardíos
de otro intento. No se añadieron infraestructura ni decisiones comerciales.

F17: [duplicados y reintentos](../../evidencias/2026-10-11-reintentos-emision.md)
cuentan con 18 casos nuevos, tres mutaciones detectadas y restauradas y 170
pruebas de dominio correctas. Se corrige la afirmación documental de que el
bloqueo fuera de EN_COLA discrimina intentos anteriores. Correlación/conciliación,
recuperación y concurrencia siguen pendientes; el balance de estados no cambia.
