# Harness de desarrollo de Ondexia

Estado: **reglas de trabajo aprobadas por el propietario el 2026-10-10**.
Aplican a desarrollo, soporte y revisión desde esta decisión. No declaran que el
producto existente cumpla ya todos los controles ni autorizan nuevas funcionalidades.

## Contrato y persistencia

Leer completo [CLAUDE.md](../../CLAUDE.md), esta guía y los documentos pertinentes
antes de trabajar. Prevalecen las instrucciones del propietario y las decisiones
vigentes; los informes de auditoría son evidencia a contrastar. Identificar y
consultar contradicciones de negocio, sin resolverlas por preferencia técnica.

Esta carpeta es la entrada **versionada** del harness metodológico. Las fuentes y
formatos detallados están en la [investigación](../investigacion/2026-10-10-requisitos-ux-seguridad/README.md)
y sus [plantillas](../investigacion/2026-10-10-requisitos-ux-seguridad/plantillas.md).
El [plan de fundamentos](../planes/2026-10-10-fundamentos-arquitectura-sdlc.md) y el
[backlog](../investigacion/2026-10-10-requisitos-ux-seguridad/backlog.md) conservan
sus actividades pendientes: aprobar el método no equivale a ejecutarlas.

## 1. De una opinión a una decisión de negocio

Registrar comentario original, fuente, actor, contexto, problema e incertidumbre.
Distinguir hechos, hipótesis y decisiones. Describir proceso actual y resultado
buscado, frecuencia, impacto y alternativas: no construir, cambiar proceso,
configurar, adaptar, comprar o desarrollar.

Evaluar viabilidad y rentabilidad por separado. Diferenciar ahorro del cliente de
beneficio para Ondexia; incluir implementación, operación, soporte, riesgos y
costos recurrentes. Registrar supuestos, fuente de cifras, horizonte y sensibilidad.
Sin datos suficientes, declarar retorno desconocido y proponer cómo obtenerlos;
no inventar estimaciones, clientes o garantías. Reutilizar decisiones aprobadas
sin pedirlas otra vez. Consultar las decisiones de negocio nuevas antes del
trabajo que dependa de ellas. Usar la [ficha de oportunidad](plantillas/oportunidad-de-negocio.md).

## 2. Especificación antes de código

Una historia expresa actor, necesidad y valor con aceptación verificable. Un caso
de uso especifica el proceso; una prueba demuestra una propiedad de ese proceso.
No son intercambiables ni mantienen necesariamente una relación uno a uno.

Para una capacidad nueva o una regla modificada, versionar objetivo, alcance,
fuente, actor y permisos, disparador, precondiciones, garantías mínimas/de éxito,
flujo principal y extensiones. Cada validación identifica dato, regla, autoridad,
momento y respuesta al aceptar, rechazar o quedar indeterminada. «Validar que sea
correcto» no es una especificación suficiente. Vincular cada extensión al paso,
condición, respuesta, efectos y punto donde retoma o termina.

Cuando corresponda, incluir estados y transiciones prohibidas, cancelación,
expiración, concurrencia, duplicados, idempotencia, transacciones, efectos externos,
recuperación y compensación. Un timeout no demuestra rechazo; no repetir efectos
inciertos. Los requisitos no funcionales llevan umbral, fuente y método de medida.
Una precondición no exime de comprobar permisos o datos que pueden cambiar.

Usar SDD para mantener intención, contratos, decisiones y tareas versionados;
BDD para descubrir y acordar ejemplos positivos, negativos y límites. Gherkin es
opcional. Aplicar TDD a lógica nueva o de riesgo cuando aporte valor, sin fingir
que escribir escenarios ya los ejecuta. Para un defecto, reutilizar la especificación
y acotar la regresión; no exigir un PRD completo para un cambio documental.

**Antes de implementar una funcionalidad, presentar el plan al propietario.**
Explicitar decisiones resueltas, preguntas pendientes y tareas independientes.
Los ejemplos didácticos, incluido el cajero, no son políticas de Ondexia.

## 3. Diseño y arquitectura

Respetar arquitectura hexagonal, contratos y dirección de dependencias existentes;
aplicar SOLID, encapsulamiento y código legible con responsabilidad y efectos
explícitos. Evitar herencia sin relación de sustitución y abstracciones sin necesidad.
Justificar patrones y decisiones en función del problema y costo de mantenimiento.
No introducir eventos, CQRS, microservicios o nuevos proveedores por adoptar una etiqueta.

Usar identificadores, archivos, comentarios y documentación en español según las
convenciones vigentes de cada tecnología. No renombrar contratos o paquetes
históricos en masa. Registrar contradicciones de nomenclatura o granularidad de
casos de uso como decisiones pendientes del plan de fundamentos.

Conservar precisión decimal: `NUMERIC(18,6)` / `BigDecimal`, nunca `double` o `float`.
Mantener API, OpenAPI y modelos Angular sincronizados en el mismo cambio. Una
migración aplicada permanece intacta. Las deudas existentes no son excepciones
nuevas autorizadas ni se dan por resueltas con documentación.

## 4. Pruebas y evidencia

Definir resultados esperados desde reglas, decisiones y datos sintéticos explícitos,
antes de observar el sistema. Registrar entorno, commit, configuración, preparación,
acciones, resultados por paso, efectos que no deben ocurrir y limpieza segura.
Aplicar particiones, límites, tablas de decisión, estados, concurrencia y casos de
abuso según riesgo; cubrir flujos significativos sin prometer todas las rutas posibles.

**Cada corrección demuestra fallo sin el arreglo y éxito con él.** El fallo debe
corresponder a la propiedad, no a un entorno roto. Para controles que ya funcionan,
comprobar sensibilidad con una mutación aislada, detectar el fallo y restaurar el
control. No inferir esperados del comportamiento observado. Registrar comandos,
resultados y artefactos; si no es posible ejecutar una comprobación, declararla
no verificada y señalar el trabajo pendiente, sin afirmar cierre comprobado.

No añadir pruebas redundantes para cambios puramente documentales o cosméticos:
verificar contenido, enlaces o presentación según el alcance. Un mock no prueba
RLS efectivo, una configuración AWS ni aceptación SUNAT. El conteo de pruebas,
la cobertura o un CI verde no prueban controles omitidos o de otro commit.

Trazar necesidad/regla → caso de uso/extensión → aceptación → prueba/control →
evidencia del commit. La relación puede ser múltiple; las tareas técnicas pueden
referenciar invariantes sin inventar historias comerciales. Usar la
[ficha de cambio](plantillas/cambio-verificable.md) y la
[trazabilidad](plantillas/trazabilidad-negocio-sdlc.md).

## 5. UX/UI y accesibilidad

Diseñar para completar tareas, entender estados y recuperar errores. Reutilizar
el sistema visual del [documento 10](../10-convenciones-de-interfaz.md). Especificar
estados vacío, carga, éxito, rechazo, permisos, fallo e incertidumbre; conservar
trabajo del usuario y evitar acciones financieras duplicadas.

Adoptar **WCAG 2.2 AA como objetivo de accesibilidad**, sin afirmar conformidad
hasta comprobar los criterios aplicables y el proceso completo. Verificar teclado,
foco visible y su retorno, semántica, etiquetas, contraste, errores vinculados a
campos, mensajes de estado y reflujo. Para diálogos, aplicar patrones W3C APG y
probar teclado/foco, no solo capturas.

En cambios de interfaz, comprobar las tareas afectadas en móvil y escritorio:
320, 390, 768 y 1440 píxeles CSS, altura reducida, orientación y zoom cuando sean
pertinentes. El mínimo AA de objetivos es 24 × 24 píxeles CSS con sus excepciones;
44 × 44 es nuestra referencia de diseño táctil, no el mínimo AA.

Evaluar prototipos y cambios importantes con tareas neutrales y usuarios pertinentes,
consentimiento y datos seguros. Registrar éxito, errores, comprensión, recuperación
y fricción; distinguir revisión heurística de investigación con usuarios reales.
No inventar participantes ni conclusiones estadísticas. Medir resultados y costo
antes de ampliar alcance; no rediseñar todo el producto por preferencia estética.

## 6. Seguridad e información

Usar OWASP Top 10:2025 y API Security Top 10:2023 para riesgos; ASVS 5.0.0 para
requisitos verificables; WSTG 4.2 para procedimientos; SAMM 2.0 y NIST SSDF 1.1
para mejora del proceso. Versionar los identificadores al seleccionar controles.
Un subconjunto priorizado de ASVS no acredita conformidad con un nivel completo.

Por cambio relevante, identificar activos, datos sensibles, identidades, fronteras,
amenazas y recuperación. Priorizar exposición, impacto, probabilidad y costo de
control/operación. No relajar aislamiento, autorización, precisión o custodia fiscal
por ahorro. Tampoco comprar herramientas o añadir arquitectura sin un problema
justificado. La seguridad incluye personas, accesos de soporte, retención,
confidencialidad, integridad, disponibilidad y recuperación, además del código.

Verificar controles efectivos en servidor y almacenamiento: mínimo privilegio,
denegación por defecto, acceso por objeto/propiedad/operación, aislamiento,
validación de entradas, cargas por contenido y límites, XML/ZIP, sesiones, secretos,
registros sin datos sensibles y tratamiento de excepciones. Seleccionar únicamente
los controles aplicables y documentar exclusiones razonadas. Para proveedores,
vincular configuración y prueba; no asumir una función activada por haberla comprado.

En DevSecOps, registrar qué comprobaciones se ejecutaron, omitieron o fallaron,
sus versiones y resultados para el commit. No llamar «análisis de seguridad correcto»
a un CI que omitió el escáner. Los hallazgos estáticos y verificaciones externas
pendientes de la investigación siguen pendientes; no son pruebas de explotación.

## 7. Entrega, soporte y mejora

Aplicar GitFlow y Conventional Commits como indica `CLAUDE.md`: contenido en
español, sin marcas de herramientas ni `Co-Authored-By`. Verificar rama, diff y
controles del mismo commit; proteger `main`, datos existentes y cambios ajenos.
Separar CI del PR de CI de la rama integrada. La aprobación de este método no
concede permiso de publicación, despliegue o eliminación de recursos.

Mantener auditoría en una sesión independiente, sin heredar la narrativa de
implementación. Transferir requisito vigente, commit y evidencia comprobable;
sus conclusiones se contrastan y no sustituyen decisiones del propietario.
Declarar revisión independiente pendiente si no se ejecutó en esas condiciones.

En soporte, contener y recuperar dentro del alcance autorizado; registrar incidente,
causa, regresión y actualización de especificaciones/runbooks. Observar defectos
escapados, retrabajo, resultados de negocio y costos reales para ajustar el proceso.

## Reutilización en otra sesión

1. Obtener el repositorio y verificar carpeta, rama, commit y estado de trabajo.
2. Leer el contrato y esta entrada; seleccionar fuentes y plantillas pertinentes.
3. Comprobar capacidades instaladas y restricciones; no asumir agentes, modelos,
   conectores o credenciales. Consultar runtime/onboarding si está disponible.
4. Restaurar únicamente componentes necesarios y verificar comandos, dependencias,
   configuración y controles antes de declarar el entorno operativo.

Existe aquí `/workspace/.ondexia-harness`: su núcleo es un planificador offline,
no ejecuta estas etapas ni impone automáticamente estas reglas. Su código, memoria,
catálogo completo de habilidades y `/workspace/.ondexia-setup` no quedan transferidos
por versionar esta guía. Para reutilizar ese piloto hay que transferir esos
componentes por separado, excluyendo secretos, cachés y evidencias personales;
volver a comprobar sus dependencias y configuración. No se ha verificado la
restauración en otra máquina. Estas reglas y plantillas sí viajan con el repositorio.
