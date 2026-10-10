# Aplicación de la investigación al backlog de fundamentos

Estado: ejecución progresiva autorizada; ACT-13 en verificación local, demás
actividades pendientes salvo evidencia específica. La investigación documental y la
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


## 5. Seguimiento del primer lote

ACT-13: regresión de cuatro configuraciones inválidas y diez casos JWT previos;
fallo sin arreglo y verificación local después. Ver
[especificación](../../especificaciones/2026-10-10-audiencia-jwt.md) y
[evidencia](../../evidencias/2026-10-10-audiencia-jwt.md).
CI, integración y auditoría independiente pendientes. F01 tiene inventario inicial
en §10 del plan; no está completo. ACT-14/15 y los demás puntos no se cierran
por esta corrección. No se ha probado configuración desplegada en AWS.
