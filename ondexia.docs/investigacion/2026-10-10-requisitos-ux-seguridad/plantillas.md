# Plantillas de trabajo y revisión

Estado: formatos adoptados por el [harness existente mediante el documento 03, §11](../../03-estructura-repositorio.md#11-desarrollo-soporte-y-evidencia-del-harness-existente) el 2026-10-10. No son formularios que deban rellenarse todos
para cada ajuste. Usar campos pertinentes y justificar exclusiones en cambios de
riesgo. Reutilizar decisiones y evidencias existentes; no duplicarlas.

## 1. Ficha de necesidad e historia de usuario

```text
ID / título / versión / estado:
Comentario original y fuente:
Actor, contexto y proceso actual:
Problema observado / hipótesis / datos faltantes:
Frecuencia e impacto conocidos:
Alternativas: no construir, proceso, configuración, desarrollo:
Beneficio para cliente / beneficio para Ondexia:
Costos iniciales, recurrentes, soporte y supuestos:
Decisión, responsable y fecha:

Historia: Como ..., necesito ..., para ...
Incluye / excluye:
Casos de uso y reglas referenciados:
Criterios de aceptación identificados:
Ejemplos positivos, negativos y límites:
Dependencias / riesgos / preguntas:
Medida de resultado, línea base y fuente:
Revisión de valor, tamaño, estimación y verificabilidad:
```

## 2. Caso de uso completo

```text
ID / objetivo / versión / estado / fuente:
Alcance y nivel:
Actor principal / secundarios / interesados:
Permisos y contexto:
Disparador:
Precondiciones:
Garantías mínimas:
Garantías de éxito:
Entradas, salidas, datos sensibles y reglas:

Flujo principal:
Paso | Acción/intención del actor | Respuesta y decisión del sistema

Extensiones:
ID | Paso | Condición detectada | Respuesta | Efectos | Retoma/termina en

Validaciones:
Dato | Regla | Autoridad | Momento | Acepta | Rechaza | Indeterminado

Estados/transiciones válidas y prohibidas:
Cancelación, expiración y recuperación:
Concurrencia, identidad de operación y duplicados:
Transacción y efectos externos/compensación:
Requisitos no funcionales y método de medición:
Variantes de datos/canal:
Ejemplos y casos de prueba:
Preguntas pendientes / decisión necesaria / responsable:
```

Evitar «validar que sea correcto» sin regla y fuente. Diferenciar un rechazo
confirmado de un timeout. Si se detecta una salida sin especificar, abrir pregunta
y aislar trabajo dependiente; no fijarla copiando el comportamiento actual.

## 3. Tabla de decisión y transición

```text
ID / reglas fuente / versión:
Condiciones y dominios de valores:
Orden de evaluación cuando afecta al resultado:
Combinaciones posibles y descartadas, con justificación:
Tabla: condiciones | resultado | efectos | extensión | pruebas
Significado de sí/no/desconocido/irrelevante:

Estado | Evento | Condición | Acción/efecto | Siguiente estado | Pruebas
Eventos duplicados/tardíos/fuera de orden:
Estados terminales y pendientes:
Recuperación y transiciones prohibidas:
```

## 4. Caso de prueba reproducible

```text
ID / título / versión:
Requisito, regla, criterio y extensión:
Riesgo / nivel / técnica:
Base de prueba y oráculo independiente:
Entorno, commit, configuración y dependencias:
Precondiciones y datos sintéticos exactos:
Preparación:
Paso | Acción | Resultado esperado relevante
Resultado final y efectos que NO deben ocurrir:
Limpieza o aislamiento sin destruir datos existentes:
Resultado observado, ejecución y evidencia:
Rojo sin arreglo / verde con arreglo, si es corrección:
Mutación aislada / detección / restauración, si verifica control existente:
Limitaciones y verificaciones externas pendientes:
```

Resultados de ejecución se registran después; nunca se trasladan como expectativas
sin contraste. Diferenciar no ejecutado, bloqueado por entorno, fallo del producto
y correcto. Capturar evidencia sanitizada y no secretos.

## 5. Revisión y estudio de UX/UI

```text
Tarea, actor, contexto y fuente de fricción:
Objetivo de investigación / hipótesis:
Estado inicial, estados alternativos y efectos:
Flujo y prototipo/versión evaluada:
Dispositivo, viewport, orientación, entrada y conectividad:
Participantes pertinentes / necesidades de acceso:
Tarea neutral, sin indicar controles:
Criterios de éxito y errores críticos:
Protocolo, ayudas e intervenciones permitidas:
Consentimiento y datos sintéticos:
Resultado por participante y condiciones:
Hallazgo | Evidencia | Impacto | Frecuencia observada | Recuperación
Cambios propuestos / alternativas / costo:
Comprobaciones de accesibilidad y límites:
Medida antes/después, denominador y responsable:
```

## 6. Control de seguridad y decisión económica

```text
ID / requisito fuente y versión exacta:
Activo, clasificación y frontera de confianza:
Amenaza/caso de abuso / impacto / exposición:
Control requerido y alcance:
Código o configuración que lo activa:
Premisa externa y forma de comprobarla:
Prueba positiva / negativa / de sensibilidad:
Estado: propuesto, configurado, localizado, ejecutado, no aplicable o pendiente:
Evidencia, commit, entorno y límite:
Opciones de solución y compatibilidad con arquitectura:
Costo de implementar / operar / mantener / revisar alertas:
Supuestos de ahorro/riesgo y datos desconocidos:
Decisión y riesgo residual, responsable y revisión:
Procedimiento de fallo, recuperación y diagnóstico:
```

No marcar ASVS satisfecho por un comentario, una herramienta instalada o una prueba
que fija el fallo en lugar del control esperado. No asignar nivel de cumplimiento
global a un subconjunto de requisitos.

## 7. Matriz de trazabilidad compartida

```text
Necesidad/decisión | Historia | Regla | Caso de uso/paso/extensión |
Criterio | Ejemplo | Prueba/control | Código/contrato |
Commit/PR/CI | Evidencia/límite | Versión entregada | Resultado de negocio
```

Son relaciones múltiples, no una fila obligatoria por cada test. Puede partirse
de un requisito técnico o incidente sin forzar una historia comercial ficticia.
Una necesidad descartada conserva su decisión y no tiene que llegar a código.


## 7. Ficha de cambio y evidencia

Complementa la ficha del harness existente. Registrar los mismos datos en un solo
artefacto del cambio; no exigir una segunda ficha por ubicación del entorno.

```text
ID / objetivo / fuente y decisión vigente:
Repositorio / rama / commit base / commit evaluado:
Alcance, exclusiones, riesgo y método SDD/BDD/TDD elegido:
Requisitos / casos de uso / extensiones / aceptación / contratos:
UX y seguridad pertinentes / configuración / pruebas:
Oráculo independiente / datos sintéticos / comando / entorno / resultado:
Corrección: fallo sin arreglo y éxito con él:
Control existente: mutación aislada detectada y restaurada:
Pruebas fallidas, correctas, omitidas y no ejecutadas:
Verificaciones externas pendientes y dependencias:
Revisión independiente: sesión, alcance, commit y hallazgos, o pendiente:
CI del PR / CI de rama integrada / commit comprobado, cuando corresponda:
Decisiones pendientes / siguiente paso autorizado / recuperación:
Resultado de negocio observado y medida de seguimiento:
```

## 8. Trazabilidad y seguimiento

Complementa la tabla del harness existente. Reutilizar IDs y evidencias; mantener
relaciones de varios a varios y declarar pendiente lo que no tenga comprobación.

| Necesidad/regla y fuente | Decisión y versión | Caso de uso/extensión | Aceptación y esperado independiente | Control/prueba | Evidencia y commit | Resultado de negocio y pendiente |
|---|---|---|---|---|---|---|
| Referencia vigente | Aprobada o pendiente, con fuente | Flujo y excepción pertinentes | Definido antes de observar | ID y archivo/configuración | Comando, entorno y resultado, o no ejecutado | Medida, fuente, responsable y siguiente comprobación |
