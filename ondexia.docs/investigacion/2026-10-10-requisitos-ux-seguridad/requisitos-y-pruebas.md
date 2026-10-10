# Especificar necesidades, historias, casos de uso y pruebas

Estado: guía propuesta. Fuentes R01–R09 del [registro](README.md).
Los formatos y ejemplos de aplicación son elaboración para Ondexia.

## 1. Instrumentos diferentes, conectados

| Instrumento | Pregunta que responde | Contenido |
|---|---|---|
| Necesidad | ¿Qué problema y para quién? | Evidencia, contexto, impacto, proceso actual, alternativas y preguntas |
| Historia de usuario | ¿Qué incremento de valor entregamos? | Actor, capacidad, beneficio, alcance y aceptación; conversación registrada |
| Regla de negocio | ¿Qué está permitido y bajo qué condiciones? | Predicado, fuente, versión, excepciones y consecuencias |
| Caso de uso | ¿Cómo alcanza el actor su objetivo con el sistema? | Flujo principal, alternativas, fallos, garantías y estados |
| Criterio de aceptación | ¿Qué debe cumplirse para aceptar el cambio? | Condición observable y comprobable, ligada al requisito |
| Caso de prueba | ¿Cómo comprobamos una propiedad concreta? | Preparación, datos, acciones, resultados esperados y evidencia |
| Tarea técnica | ¿Qué trabajo de implementación hace falta? | Archivos/fronteras afectadas, dependencia y verificación |

Una historia no es cada botón, una tarea no es un requisito de negocio y un
diagrama de casos de uso no sustituye sus especificaciones. Una historia puede
modificar varios casos de uso; un caso de uso puede entregarse en varias historias.
La conversación complementa la documentación; no justifica dejar decisiones sin
registrar. [Agile Alliance](https://agilealliance.org/glossary/user-stories/),
[Cockburn](https://alistaircockburn.com/Unifying%20us%20uc%20sm.pdf).

En este proyecto, SDD significa trabajar desde especificaciones versionadas.
BDD aporta descubrimiento y ejemplos; TDD aporta retroalimentación al implementar.
No son certificaciones ni mecanismos que hagan correcta una especificación por
el simple hecho de escribirla.

## 2. Secuencia de elaboración

1. Capturar el comentario original y su fuente, sin reemplazarlo por una solución.
2. Identificar actor, objetivo, proceso actual, frecuencia e impacto. Separar dato,
   hipótesis y preferencia. Comparar no construir, cambiar proceso y desarrollar.
3. Delimitar sistema y participantes: qué controla Ondexia y qué depende de un
   banco, proveedor, SUNAT, dispositivo o intervención humana.
4. Inventariar objetivos por actor. Seleccionar el corte prioritario; no producir
   fichas completas para funciones que aún no se construirán.
5. Escribir el recorrido exitoso. Separar acción del actor y respuesta del sistema.
6. Descomponer cada verbo ambiguo: validar, autorizar, calcular, guardar, enviar.
   Definir entrada, condición, autoridad, respuesta y efecto.
7. Revisar cada paso buscando alternativas, rechazo conocido, fallo técnico y
   resultado desconocido; indicar dónde se retoma o termina.
8. Modelar reglas combinadas con tablas de decisión y comportamiento temporal con
   estados/transiciones. Separar estado de interfaz, estado de negocio y externo.
9. Añadir permisos, aislamiento, concurrencia, transacciones, historia, datos y
   condiciones no funcionales medibles.
10. Derivar ejemplos y pruebas con expectativas fijadas antes de ejecutar.
11. Revisar con negocio y con una perspectiva distinta a la implementación;
    actualizar decisiones y trazabilidad cuando cambien.

## 3. Historia de usuario bien formada

Propuesta de formato: «Como [actor concreto], necesito [capacidad], para
[resultado útil]». Ese enunciado inicia la conversación; no contiene el flujo entero.
Añadir problema/fuente, alcance, exclusiones, reglas y casos de uso referenciados,
criterios, ejemplos, dependencias, incertidumbre y medida de resultado.

Revisión INVEST: valor reconocible, tamaño revisable, posibilidad de estimar y
comprobar; negociar detalles sin renegociar restricciones aprobadas. Si hay
dependencia real, registrarla en lugar de simular independencia. Dividir por
resultados funcionales, no en «crear tabla», «crear API» y «crear pantalla» como
historias de usuario. [Bill Wake](https://xp123.com/invest-in-good-stories-and-smart-tasks/).

Ejemplo con una decisión ya aprobada:

> Como vendedor que está preparando una venta, necesito registrar y seleccionar
> un cliente después de revisar los datos consultados de su RUC, para continuar
> el cobro sin volver a preparar la venta.

La historia enlaza al caso de uso y a decisiones previas; no establece por sí
sola qué ocurre con proveedor caído, cliente inactivo o permiso revocado.

## 4. Especificación completa de caso de uso

Campos mínimos para un flujo con dinero, permisos o integraciones:

- Identificador, título con objetivo, versión, estado y fuente aprobada.
- Alcance y nivel: proceso de negocio, objetivo del usuario o subfunción.
- Actor principal, secundarios, interesados y permisos.
- Disparador; precondiciones; garantías mínimas y de éxito.
- Datos de entrada/salida y reglas identificadas.
- Flujo principal numerado, con intención del actor y respuesta del sistema.
- Extensiones ligadas a un paso: condición, detección, respuesta, efecto y destino.
- Estados/transiciones, cancelación, expiración y recuperación.
- Concurrencia, idempotencia, límites transaccionales y efectos externos.
- Requisitos no funcionales verificables, datos sensibles, auditoría.
- Ejemplos/pruebas vinculados, variantes, exclusiones y decisiones pendientes.

Adaptación propia de la estructura de
[Cockburn](https://www.cs.otago.ac.nz/coursework/cosc461/uctempla.htm). No hay un
número universal correcto de pasos. Separar subfunciones cuando mejora lectura y
reutilización, conservando la referencia y las garantías del objetivo principal.

Una precondición establece el punto de partida del caso, no elimina verificaciones
necesarias. Si la sesión o el permiso puede cambiar durante el flujo, especificar
la nueva comprobación al confirmar. Una extensión puede ser otro camino exitoso,
no solo un error. Los clics y componentes concretos pertenecen al diseño de
interacción, excepto cuando sean parte necesaria del contrato: insertar una
tarjeta física o pulsar una confirmación financiera tiene significado observable.

## 5. Cómo describir una validación

Usar una ficha por decisión relevante:

| Campo | Pregunta |
|---|---|
| Entrada | ¿Qué valor, formato, origen y versión se comprueban? |
| Regla | ¿Qué predicado exacto y excepciones deciden? |
| Autoridad | ¿Documento aprobado, usuario autorizado, proveedor o configuración? |
| Momento | ¿Antes de consultar, al confirmar, dentro de transacción o al recibir resultado? |
| Aprobación | ¿Qué habilita? No confundir formato válido con autorización |
| Rechazo | ¿Qué código/mensaje y qué corrección se permite? |
| Indeterminado | ¿Qué sucede si no hay respuesta o no se conoce el efecto? |
| Efectos | ¿Qué puede cambiar y qué debe permanecer intacto? |
| Comprobación | ¿Qué ejemplos distinguen aceptación, rechazo e incertidumbre? |

Para «RUC válido» distinguir formato, dígito de control cuando corresponda,
coincidencia con la respuesta, atestación vigente y registro permitido por reglas.
Una expresión regular no demuestra que exista el contribuyente. Para «tarjeta
correcta» distinguir lectura y datos, estado del emisor, autenticación y permiso
de operar; no basta que el número tenga una longitud determinada.

## 6. Corrección: verificación y validación

Verificar exige consistencia con la especificación; validar exige contrastar que
la necesidad y solución son adecuadas. Una especificación puede ser coherente y
resolver el problema equivocado. [IREB](https://cpre.ireb.org/en/downloads-and-resources/glossary).

Lista de revisión propuesta:

1. **Fuente:** cada regla proviene de una decisión o referencia identificable.
2. **Semántica:** términos y resultados sin ambigüedad; ninguna condición termina
   en «según corresponda» sin definición.
3. **Consistencia:** flujo, tablas, estados, mensajes y contratos no se contradicen.
4. **Cobertura del alcance:** cada condición y salida significativa está
   especificada; incluyen rechazo, timeout, cancelación y efectos parciales.
5. **Observabilidad:** hay forma de comprobar éxito, rechazo y ausencia de efectos.
6. **Viabilidad:** se conocen límites de integraciones y costos; pendientes visibles.
7. **Revisión:** el propietario valida decisiones de negocio y el contraste técnico
   busca casos que la implementación podría omitir.

Se puede exigir cobertura de todas las reglas y salidas del modelo acordado.
No se puede prometer cubrir todas las situaciones del mundo ni todos los caminos
de un sistema con bucles. Las combinaciones omitidas requieren razón y riesgo
registrados, no una afirmación de completitud absoluta.

La revisión no se cierra por consenso verbal solamente: registrar qué escenario
se recorrió, quién validó la decisión y qué discrepancias o preguntas quedan.
Un recorrido de mesa con datos sintéticos puede detectar huecos antes del código;
la prueba ejecutada después contrasta la implementación, no corrige automáticamente
una regla de negocio mal definida.

## 7. Diseño de pruebas

Seleccionar técnicas de acuerdo al riesgo; las siguientes son un mapa práctico,
no cuotas de tests. La referencia general es
[ISTQB CTFL 4.0.1, capítulo 4](https://istqb.org/wp-content/uploads/2024/11/ISTQB_CTFL_Syllabus_v4.0.1.pdf).

| Tipo de decisión | Aplicación a Ondexia |
|---|---|
| Particiones de equivalencia | RUC admisible/no admisible; cliente propio/ajeno; usuario autorizado/no autorizado |
| Límites | Longitudes, escala decimal, umbral comercial/fiscal aprobado, cero y máximo admitido |
| Tabla de decisión | BIEN/SERVICIO, historia de stock, unidad; permiso y empresa; requisitos de factura |
| Transiciones | Emisión, aceptación, rechazo, canje, anulación, cierre de caja; eventos tardíos e inválidos |
| Secuencias y fallos | Guardar y perder respuesta; reintentar; cambio de contexto durante una consulta |
| Concurrencia | Correlativos, doble canje, cliente duplicado y ventas concurrentes con stock |
| Seguridad/abuso | Identificadores ajenos, campos prohibidos, payloads y límites de consumo |
| Exploración y uso | Comprensión de estados, errores, móvil, teclado y recuperación |

Una tabla puede minimizar combinaciones que no cambian el resultado; registrar
las condiciones indiferentes y las combinaciones imposibles. Pairwise no sustituye
las combinaciones completas de una autorización crítica. Cobertura de líneas no
demuestra cobertura de reglas. Las pruebas de API no demuestran usabilidad.

Cada caso de prueba tiene ID, requisito/regla/extensión, nivel, datos explícitos,
estado inicial, pasos, resultado por paso relevante, resultado final, efectos
prohibidos, limpieza, versión, ejecución y evidencia. «Funciona correctamente»
no es un resultado esperado.

Los oráculos pueden ser ejemplos aprobados, cálculo decimal independiente,
contrato documentado, referencia externa verificada o invariantes. Una respuesta
actual, un snapshot recién generado o un mock configurado para devolver lo que
esperamos no prueban la regla por sí mismos. Los mocks ejercitan nuestra reacción;
un ensayo separado debe contrastar la premisa del proveedor.

Para corrección: fallo pertinente sin arreglo → pasa con arreglo → regresión.
Para un control existente: mutación aislada que lo contradice → fallo → restauración
→ pasa. Un error de compilación o de entorno no sustituye el fallo del comportamiento.
Gherkin en español expresa ejemplos; no es obligatorio instalar Cucumber para
automatizar las pruebas pertinentes. [Cucumber](https://cucumber.io/docs/gherkin/reference/).

## 8. Aplicación inmediata al alta de cliente en la venta

Fuente: decisiones DEC-01 y requisitos de la
[especificación existente](../../especificaciones/2026-10-08-ajustes-ventas/requisitos.md).
Conservar su distinción entre confirmado, propuesto e implementado; esta guía
no eleva propuestas complementarias a decisiones aprobadas.

Recorrido: venta preparada → Nuevo cliente → ingresar RUC → validar entrada →
buscar cliente propio → consultar si no existe → revisar datos → Registrar y usar
→ confirmar registro → seleccionar cliente → continuar venta conservada.

Para completarlo deben trazarse, como mínimo: cliente existente activo/inactivo,
permiso ausente o revocado, respuesta con otro RUC, consulta caída, ingreso manual
según contrato vigente, datos editados, respuesta tardía, empresa/local cambiado,
cancelación, registro fallido, conflicto concurrente y respuesta de guardado perdida.

«Cerrar el modal» no equivale a deshacer un registro ya confirmado. «Consultar»
no equivale a crear ni emitir. Un timeout al guardar exige averiguar si se creó
antes de repetir. La especificación debe contrastarse con pruebas existentes y
cubrir sus huecos; no copiar la implementación como si fuera autoridad de negocio.
