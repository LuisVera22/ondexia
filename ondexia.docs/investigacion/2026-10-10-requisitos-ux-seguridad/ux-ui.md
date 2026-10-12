# UX/UI para tareas comerciales, escritorio y móvil

Estado: propuesta aplicable al piloto; no rediseño implementado.
Fuentes U01–U07 y S15 del [registro](README.md).

## 1. Objetivo y diagnóstico limitado

La calidad de interfaz se evaluará por terminar tareas correctamente, comprender
consecuencias y recuperarse de errores. La estética y consistencia visual ayudan,
pero no reemplazan esos resultados. Las heurísticas son criterios de inspección;
no prueban que un cajero encuentre una acción o entienda un estado.
[Nielsen Norman Group](https://www.nngroup.com/articles/ten-usability-heuristics/).

Ondexia ya dispone de tokens, componentes, convenciones de texto/foco, reglas de
tabla y pruebas de responsividad. Doc. 10 declara estas decisiones. Eso no demuestra
un proceso completo de UX ni conformidad de accesibilidad. No se realizaron nuevas
pruebas de uso, lector de pantalla ni inspección visual de todas las vistas aquí.
Las capturas anteriores son evidencia visual de escenarios concretos.

Evitar sustituir tipografía, colores o componentes sin identificar el problema.
El primer corte será punto de venta, alta contextual y detalle/previsualización:
son recorridos observados por el socio y atraviesan tareas de alto impacto.

## 2. Proceso de diseño que propongo

1. **Comprender:** observar cómo se vende hoy; dispositivo, interrupciones,
   aprendizaje, frecuencia, conectividad y necesidades de acceso. Registrar lo
   observado separado de lo que el usuario interpreta o solicita.
2. **Definir:** objetivo de tarea, errores costosos, usuarios y restricciones.
   Priorizar problemas, no pantallas para decorar.
3. **Diseñar:** flujo, arquitectura de información y estados; boceto o prototipo
   suficiente para comprobar la incertidumbre principal.
4. **Contrastar:** tareas neutrales con usuarios pertinentes; anotar abandono,
   errores, ayuda, comprensión y recuperación.
5. **Entregar y medir:** implementación con componentes existentes, revisión de
   interacción/accesibilidad, regresión y seguimiento de resultado.

Se adapta el enfoque de exploración/definición y desarrollo/entrega del
[Design Council](https://www.designcouncil.org.uk/resources/the-double-diamond/).
Un ajuste pequeño puede completar estas etapas con una observación, un boceto y
una comprobación breve; no requiere un proyecto de investigación separado.

## 3. Principios traducidos a Ondexia

Las siguientes son recomendaciones propias para nuestras tareas, informadas por
las heurísticas; no constituyen reglas de negocio nuevas.

| Principio | Aplicación concreta | Evidencia necesaria |
|---|---|---|
| Contexto visible | Empresa, local, caja y tipo de documento comprensibles al confirmar | Usuario identifica dónde y qué emitirá; pruebas de cambio de contexto |
| Estado comprensible | Distinguir preparando, guardando, registrado, en emisión, aceptado y rechazado | Usuario explica qué ya ocurrió y qué sigue pendiente |
| Acción principal inequívoca | Una acción principal por etapa; texto expresa consecuencia | No confundir Registrar y usar con emitir la venta |
| Control y recuperación | Cancelar antes de guardar; corregir dato; reintentar con estado conocido | Sale sin perder la venta; no duplica operación |
| Consistencia | Igual operación conserva nombre y patrón; diferencias fiscales visibles cuando afectan decisiones | Revisión transversal de módulos |
| Prevención proporcional | Revisión de cliente/importes/tipo; advertencias que importan al momento oportuno | Menos errores reales; no confirmaciones repetidas para todo |
| Reconocimiento | Etiquetas persistentes, acciones encontrables, datos necesarios cercanos | No necesita recordar datos de otra pantalla |
| Eficiencia | Teclado para uso frecuente y controles táctiles para móvil | Completa tarea por ambos medios sin atajos obligatorios |
| Jerarquía | Total y siguiente acción se localizan; ayuda secundaria no domina | Comprensión probada, sin ocultar restricciones críticas |
| Error útil | Qué ocurrió, qué se conserva, cómo resolverlo | Recuperación sin asistencia ni mensajes internos |

La regla de no editar libremente documentos emitidos no se cambia con un botón
«Deshacer». El patrón de recuperación debe respetar canje, anulación y mecanismos
aprobados, en lugar de importar una heurística fuera de contexto.

## 4. Contrato de cada vista y componente

Cada tarea debe definir estado inicial, carga, listo, vacío, validación fallida,
sin permiso, fallo de servicio, éxito, respuesta incierta y datos desactualizados.
No todos son aplicables a cada componente; justificar exclusiones.

| Elemento | Recomendación y comprobación |
|---|---|
| Formulario | Etiquetas asociadas, obligatoriedad explícita, ejemplo cuando haga falta; conservar datos al fallar; no usar placeholder como única etiqueta |
| Error | Mensaje junto al campo y vínculo semántico; resumen si hay varios errores; llevar foco a un lugar útil sin saltos repetidos |
| Buscador | Distinguir sin resultados y no pudo buscar; loading claro; selección por teclado; respuestas tardías no reemplazan una consulta nueva |
| Botón | Nombre accesible; tamaño usable; estado de envío visible; impedir doble envío es apoyo de UX, no garantía de idempotencia servidor |
| Modal | Foco inicial coherente, recorrido interno, cierre y devolución de foco; fondo inerte; salida y estado de guardado definidos |
| Tabla/listado | Jerarquía y acciones consistentes; mantener búsqueda disponible cuando esté vacío; mobile conserva datos/acciones esenciales |
| Importe | Moneda, precisión y separadores coherentes; cálculo decimal aprobado; no ocultar descuentos o efecto financiero |
| Estado fiscal | Texto además de color; diferenciar registro local y aceptación externa; indicar acción disponible |
| Detalle de documento | Registro comprensible primero, previsualizar/imprimir como acción; estado financiero/fiscal e historia claros |
| Confirmación irreversible | Explicar objeto, monto y consecuencia; evitar confirmaciones genéricas; permitir revisión conforme a la regla aprobada |

El resumen de errores puede tomar como referencia el
[patrón de GOV.UK](https://design-system.service.gov.uk/components/error-summary/).
Los modales deben contrastarse con
[W3C APG](https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/), incluido teclado y
retorno del foco. Ni añadir `aria-modal` ni hacer una captura demuestra que funcione.

## 5. Móvil y accesibilidad

Propongo **WCAG 2.2 AA como objetivo técnico** de las tareas y componentes del
piloto. No se afirma conformidad global hasta evaluar páginas y procesos completos.
[W3C WCAG 2.2](https://www.w3.org/TR/2024/REC-WCAG22-20241212/).

| Criterio | Comprobación resumida |
|---|---|
| 1.4.3 | Contraste de texto 4.5:1; texto grande 3:1, con condiciones/excepciones del criterio |
| 1.4.10 | Reflujo a 320 píxeles CSS sin desplazamiento en dos dimensiones, salvo contenido que lo requiera |
| 1.4.11 | Contraste no textual 3:1 cuando corresponda a controles/estados necesarios |
| 2.1.1 / 2.1.2 | Tarea operable por teclado y sin trampas |
| 2.4.3 / 2.4.7 / 2.4.11 | Orden de foco, foco visible y no totalmente oculto por contenido del autor |
| 2.5.8 | Objetivos de al menos 24×24 píxeles CSS o excepción válida, incluido espaciado |
| 3.3.1 / 3.3.2 / 3.3.4 | Identificación e instrucciones de error; prevención en transacciones financieras |
| 4.1.2 / 4.1.3 | Nombre/rol/valor y anuncios de estado adecuados |

Son una selección para empezar, no todos los criterios AA. Para acciones táctiles
principales recomiendo 44×44 píxeles CSS como decisión de diseño propuesta; **no**
se presentará 44 como el mínimo AA de 2.5.8. Documentar excepciones de componentes
densos en lugar de reducir todos los objetivos a iconos difíciles de pulsar.

Diseño mobile: prioridad de contenido y tarea antes de columnas; teclado virtual
sin tapar confirmación/error; modal puede ocupar la pantalla cuando sea necesario;
totales/acciones fijos solo si no ocultan contenido o foco. No depender de hover,
gestos de arrastre ni orientación específica como único camino.

Matriz inicial propuesta: ventanas reales de 320, 390, 768 y 1440 píxeles; altura
reducida; retrato/paisaje; zoom 200 % y reflujo equivalente a 400 % en escritorio.
Probar contenido largo, varios pagos, nombres extensos, teclado, errores y estados
de carga, no solo venta vacía. Los anchos son un conjunto práctico, no evidencia
de que se cubren todos los teléfonos.

La previsualización A4/ticket conserva geometría de papel; separarla del formulario
operativo y permitir una visualización adecuada. Una excepción por contenido
bidimensional no justifica desbordamiento horizontal de toda la aplicación.

## 6. Sistema visual proporcional

Extender el sistema actual, con inventario de tokens/componentes usados y estados.
Documentar tipografía y jerarquía, espaciado, color semántico, foco, densidad,
botones, formularios, diálogos, tablas, avisos y estados fiscales.

Cada componente tendrá propósito, variantes necesarias, comportamiento de
teclado, estados, regla mobile y ejemplo. Distinguir reutilización visual de reglas
de negocio: un componente de tabla no decide quién puede anular un comprobante.

Evitar librerías duplicadas o un catálogo de variantes sin uso. Figma puede ayudar
si reduce incertidumbre y mantenimiento, pero no se ha utilizado ni se requiere
como condición previa para esta investigación. Una captura aislada no constituye
un sistema de diseño.

## 7. Pruebas de uso y colaboración con el propietario

El propietario puede aportar «quería hacer X, busqué Y, ocurrió Z, esperaba W» y
contexto/dispositivo. Traduciremos eso en problema y hipótesis comprobable. No se
le pide decidir bibliotecas, ARIA o breakpoints.

Primera ronda propuesta: participantes reales o probables con distintos niveles
de experiencia, uso en tienda y necesidades de acceso. Una ronda pequeña de 4–6
personas es una decisión de alcance para encontrar fricciones, **no** muestra
estadística ni garantía de descubrir un porcentaje de problemas. Ajustar según
diversidad y hallazgos. [GOV.UK](https://www.gov.uk/service-manual/user-research/using-moderated-usability-testing).

Tareas neutrales sugeridas:

- «Atiende esta compra de un cliente que todavía no está registrado».
- «La consulta de datos no respondió; continúa según lo que permita el sistema».
- «Necesitas saber si este documento se registró y si fue aceptado por SUNAT».
- «Revisa el documento antes de imprimirlo desde el teléfono».

No decir «pulsa Nuevo cliente», «abre el modal» o «selecciona el botón azul» cuando
queremos medir descubrimiento. Estudiar comprensión sin solicitar emisión real.
Usar datos sintéticos y consentimiento para grabar; no capturar credenciales.

Registrar resultado, errores críticos, ayuda/intervenciones, ruta elegida,
comprensión y tiempo. Pensar en voz alta puede alterar tiempos: no comparar esos
tiempos como si fueran operación cotidiana medida sin moderación.

Severidad propuesta: bloquea tarea; provoca resultado financiero/fiscal equivocado;
genera retraso o asistencia; molestia visual. Priorizar por impacto, frecuencia y
recuperación. No convertir preferencias personales en defectos automáticamente.

## 8. Medición económica de UX

Elegir objetivo → señal → medida, como propone
[HEART](https://research.google/pubs/measuring-the-user-experience-on-a-large-scale-user-centered-metrics-for-web-applications/).
Para el piloto: finalización sin ayuda, errores de emisión/selección, tiempo de
tarea bajo condiciones comparables, solicitudes de soporte y valoración de claridad.
Registrar denominadores; cinco opiniones positivas no representan a todos los clientes.

Ejemplo de evaluación, no cifra observada: ahorro operativo = operaciones aplicables
× reducción de minutos × costo por minuto × adopción, descontando capacitación y
mantenimiento. El ahorro del cliente no es ingreso de Ondexia. Revisar también
retención, conversión o soporte con datos antes de atribuir retorno comercial.

Comenzar con anotaciones de estudios y métricas agregadas necesarias; no instalar
grabación de sesiones o analítica invasiva por defecto. Los comentarios del
propietario complementan, pero no sustituyen, observación y pruebas técnicas.

## 9. Cierre de un ajuste de UX/UI

Problema/fuente y alcance trazados; flujos/estados definidos; revisión mobile,
teclado y accesibilidad pertinente; contraste de uso cuando la incertidumbre lo
requiera; capturas útiles; regresión pertinente; sensibilidad de controles afectados.
Limitaciones declaradas, especialmente lectores de pantalla, dispositivos reales
y escenarios no evaluados. Ningún rediseño visual cambia reglas del negocio por
implicación.
