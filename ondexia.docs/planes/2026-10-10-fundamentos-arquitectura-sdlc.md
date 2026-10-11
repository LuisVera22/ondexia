# Plan de fundamentos de arquitectura, código y desarrollo de producto

Fecha: 2026-10-10. Estado: ejecución progresiva autorizada por el propietario;
primer lote en curso, decisiones pendientes explícitas.

Base inspeccionada: `/workspace/ondexia`, rama `develop`, commit
`6f7ae796af30dc10ccb6307c482097a24e3c53de`.

Este documento propone actividades y criterios; no modifica las reglas de negocio
aprobadas ni convierte una recomendación en un estándar vigente. No se ha realizado
una refactorización funcional. La revisión es preliminar y pertenece a la sesión de
implementación: **no es una auditoría independiente**.

## 1. Resultado que se busca y alcance

Dejar una cadena verificable entre necesidad, decisión de negocio, requisitos,
casos de uso, diseño, pruebas, implementación, entrega y resultado medido. Las
convenciones deben permitir revisar una contribución sin depender de la memoria
de quien la escribió; las restricciones importantes deben tener controles ejecutables.

Se revisan backend, frontend, contratos, persistencia, emisión asíncrona,
documentación, harness y proceso de desarrollo y soporte. Se conserva el plan por
fases del producto: este trabajo añade controles y corrige riesgos por cortes
acotados, sin sustituir el alcance comercial por una reescritura del sistema.

No se propone adoptar microservicios, CQRS, event sourcing, más agentes o nuevos
servicios de nube por defecto. CMMI se utiliza como referencia para gestión de
requisitos, decisiones, configuración, calidad, medición y mejora; no se afirma
certificación ni un nivel de madurez.

## 2. Base consultada y evidencia

Se leyó entero [CLAUDE.md](../../CLAUDE.md). Se consultaron las secciones pertinentes
de [estructura](../03-estructura-repositorio.md),
[arquitectura backend](../08-arquitectura-backend.md),
[interfaz](../10-convenciones-de-interfaz.md),
[plan del producto](../12-plan-primer-producto.md) y
[emisión electrónica](../14-emision-electronica.md), junto con código y pruebas.

Existen `/workspace/.ondexia-harness`, `/workspace/.ondexia-setup` y
`/workspace/auditoria-independiente`. Se consultaron los documentos de negocio,
evaluación de oportunidades, SDD/BDD/TDD y la plantilla de trazabilidad del harness.
Su ubicación externa al repositorio y sus referencias a versiones anteriores
requieren reconciliación. Su existencia no demuestra agentes, modelos ni
conectores operativos. El piloto declara ejecución deshabilitada
(`can_execute = false`).

Inventario preliminar por extensión y ubicación: 308 archivos Java de producción
y 140 TypeScript de producción, incluidos modelos generados. Es un conteo
orientativo; no mide cohesión ni complejidad y no sustituye la revisión semántica.

| Hallazgo | Evidencia verificable | Implicación y límite |
|---|---|---|
| Hay arquitectura hexagonal parcialmente controlada | `ArquitecturaTest.java`: nueve controles de dependencias, ubicación de adaptadores y uso de tecnologías | No corresponde afirmar que no se aplica arquitectura. Faltan controles sobre ciclos, dependencias entre áreas y otras propiedades |
| El dominio tiene comportamiento y encapsulamiento en varias entidades | `DocumentoVenta` usa campos privados y copias de listas; `Cliente` y `Empresa` contienen validaciones y operaciones de negocio; el módulo de dominio no declara dependencias de producción | No permite concluir que todos los agregados preserven invariantes o que toda copia sea profunda |
| La regla de granularidad de casos de uso discrepa del código | Doc. 08 pide un caso de uso por clase y un método público; `DocumentosDeVenta` contiene emitir, obtener, recientes y series disponibles; `Identidad` también agrupa operaciones | Hay que resolver la convención y revisar motivos de cambio. Tener varios métodos no demuestra por sí solo una violación de responsabilidad única |
| Hay contradicciones documentales vigentes | Doc. 03 usa ejemplos `ProductoService` y `EmitirBoletaUseCase`; doc. 08 usa infinitivos españoles y describe dos módulos donde hoy hay cinco | Se necesita una sola convención aplicable. Los apartados marcados como históricos no se tratarán como reglas vigentes |
| El frontend hace aritmética monetaria binaria | `punto-de-venta.component.ts`: precio `number`, `Math.round`, multiplicación de cantidad por precio, división por 1.18 y conversiones con `Number`; DTO de ventas con importes `number` | Contradice la precisión decimal exigida. En esta revisión no se reprodujo todavía una discrepancia monetaria concreta |
| La impresión depende de datos actuales además del logo histórico | `DocumentoVentaAdaptador` reconstruye el cliente buscando su registro actual; el detalle carga la empresa actual para cabecera y dirección | Riesgo de variación histórica al modificar cliente o emisor. Falta reproducirlo y acordar los campos que deben conservarse |
| La emisión usa una combinación de objetos y eventos | Puerto `BusDeEmision`, adaptadores S3 y memoria, publicación después de confirmar la transacción, doc. 14 | Es orientación a objetos con un tramo asíncrono por eventos; no demuestra que todo el sistema sea dirigido por eventos |
| Hay una ventana de fallo asíncrono reconocida | Doc. 14 §2.1: una publicación fallida después del commit puede dejar el documento en cola sin orden; existe recuperación por reintento | Revisar garantías y recuperación antes de decidir si hace falta outbox u otro mecanismo |
| Hay controles frontend, pero falta una convención ejecutable de código completa | TypeScript y plantillas estrictos; controles existentes de tono y forma; no se encontraron scripts/dependencias de lint/formato en el paquete web revisado | No confundir diseño visual, tipado estricto y calidad de arquitectura. Se requiere una revisión adicional de las otras aplicaciones |
| La generación de contratos no cubre necesariamente todos los DTO | Modelos de catálogo generados y DTO escritos a mano en servicios de ventas y configuración; afirmaciones amplias en doc. 03 | Inventariar cobertura antes de afirmar sincronización completa de clientes |

### Comprobación ejecutada en esta revisión

Se ejecutó `ArquitecturaTest` en el reactor de dominio y API: **9 pruebas,
0 fallos, 0 errores, 0 omitidas**. Comando reproducible desde `apps/backend`:

```bash
source /workspace/.ondexia-setup/activar-develop.sh
./mvnw -B -s /workspace/.ondexia-setup/maven.xml \
  -pl ondexia.api -am -Ddependency-check.skip=true \
  -Dtest=ArquitecturaTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Esta ejecución omite deliberadamente el análisis de dependencias y no es el CI
completo. No se ejecutaron mutaciones de estos nueve controles en esta revisión.
Tampoco se verificaron AWS, SUNAT real, rendimiento en producción, dispositivos
físicos, rentabilidad ni todos los módulos y flujos de extremo a extremo.

## 3. Estándares que hay que concretar

### 3.1 Nombres, ubicación y convenciones

La propuesta parte de nombres de negocio en español y evita renombrados masivos.
Se documentarán las excepciones reales de bibliotecas, nombres externos y código
generado; no se autorizarán excepciones adicionales por conveniencia sin resolver
su compatibilidad con `CLAUDE.md`.

| Elemento | Convención propuesta | Ejemplo o condición |
|---|---|---|
| Clases y tipos Java/TypeScript | `PascalCase` con concepto o acción del negocio | `DocumentoVenta`, `EmitirDocumentoVenta`, `DatosDeCliente` |
| Métodos, propiedades y variables | `camelCase`; verbo para operación, sustantivo para dato; booleano comprensible | `registrarYUsar`, `importeTotal`, `permiteVentaSinStock` |
| Enumerados y constantes | `MAYUSCULAS_CON_GUIONES_BAJOS` cuando lo exija la convención del lenguaje | `NOTA_DE_VENTA`; preservar códigos SUNAT como valores externos |
| Archivo Java | Mismo nombre exacto del tipo público | `DocumentoVenta.java` |
| Archivo Angular | `kebab-case` español y sufijo de función consistente | `detalle-documento.component.ts`, `ventas.api.service.ts`, `datos-alta-cliente.ts` |
| Paquetes Java | Minúsculas, por área y responsabilidad; vocabulario único | Mantener y registrar `application`, `domain`, `infrastructure` como legado pendiente de decisión, sin inventar paquetes paralelos |
| Carpetas frontend | Área funcional y responsabilidad; componentes compartidos solo si realmente se reutilizan | Evitar carpetas genéricas que acumulen reglas de todas las áreas |
| Puertos y adaptadores | Nombre del propósito y tecnología solo en el adaptador | `BusDeEmision`; acordar una sola forma para los adaptadores de persistencia |
| Tablas, columnas y restricciones nuevas | `snake_case` español; prefijos de restricciones y longitud definidos | No cambiar nombres o comentarios de migraciones ya aplicadas |
| Rutas API y códigos de error nuevos | Vocabulario de negocio estable y patrón documentado | Mantener compatibilidad; un cambio de nombre es un cambio de contrato |
| Pruebas | Nombre que describa condición y resultado | `rechazaCambioABienSiExisteHistoriaDeStock` |
| Documentos y decisiones | Nombre descriptivo, fecha cuando sea evidencia, estado y versión | Diferenciar propuesta, decisión aprobada, implementación y evidencia |
| Commits | Conventional Commits; descripción y cuerpo en español | `fix(ventas): conserva los importes decimales`; sin marcas de herramientas ni `Co-Authored-By` |

Comentarios: explicar una restricción, motivo o decisión que el código no expresa;
evitar narrar cada línea. Documentar contratos públicos, invariantes y límites de
integración cuando aporten información. No imponer comentarios vacíos por porcentaje.

### 3.2 Diseño de objetos y SOLID

- **Encapsulamiento:** campos privados; operaciones que expresan intención y
  preservan invariantes; evitar setters públicos que permitan cualquier estado.
  Proteger colecciones y referencias mutables. Separar la construcción válida de
  la reconstitución desde persistencia y documentar qué valida cada camino.
- **Entidades y objetos de valor:** identidad cuando corresponda, igualdad
  definida, objetos de valor inmutables, límites de agregado y transacción
  explícitos. Identificar dinero, cantidades, documentos e identificadores que
  necesitan tipos propios; no envolver todo dato sin una restricción real.
- **Responsabilidad única:** revisar razones de cambio y mezcla de operaciones,
  no solamente longitud. Resolver si el estándar de un caso de uso por clase
  sigue vigente y qué consultas pueden agruparse.
- **Abierto/cerrado y polimorfismo:** usar contratos para variaciones reales,
  como adaptadores de un puerto; no añadir jerarquías o interfaces sin consumidores
  y alternativas justificadas.
- **Sustitución e interfaces:** los adaptadores deben respetar precondiciones,
  resultados y errores del puerto. Interfaces pequeñas por capacidad consumida;
  pruebas del contrato cuando existan varias implementaciones.
- **Inversión de dependencias:** dominio sin frameworks ni transporte;
  aplicación orquesta; adaptadores convierten DTO y tecnología. Revisar también
  dependencias entre áreas, no solo entre capas.
- **Herencia:** favorecer composición. Una herencia de negocio exige relación
  sustituible y contrato probado. La herencia técnica de entidades JPA se revisa
  por separado; no demuestra una jerarquía de negocio incorrecta.
- **Errores y efectos:** errores tipificados de negocio separados de fallos de
  infraestructura; transacción, autorización e idempotencia con propietario
  identificable; evitar efectos implícitos en accesores o renderizado.

### 3.3 Arquitectura y eventos

El modelo inicial a contrastar es: dominio orientado a objetos, aplicación por
casos de uso, adaptadores hexagonales y emisión asíncrona por S3. Se documentarán
límites de áreas, dependencias permitidas, contratos entre módulos y secuencias de
venta, numeración, stock, pago y emisión.

Una orden de emisión no se confundirá con un evento de negocio. Para cada mensaje
se especificarán identidad, versión, productor, consumidor, duplicados, orden,
reintentos, expiración, recuperación y correlación. No se prometerá entrega
exactamente una vez. La publicación después del commit requiere una decisión
explícita sobre la ventana de fallo y su recuperación.

### 3.4 Negocio, análisis funcional y pruebas

Una solicitud de cliente comienza como evidencia de una necesidad, no como una
orden de construir. El análisis debe incluir problema, actor, proceso actual,
frecuencia, efecto, alternativas, restricciones y datos faltantes.

El caso económico separará **valor para el cliente** de **rentabilidad para
Ondexia**. Registrará inversión, costos recurrentes, soporte, adopción esperada,
ingreso o ahorro incremental, horizonte, moneda y escenarios. Cada entrada se
etiqueta medida, estimada o desconocida. ROI y recuperación solo se calculan con
una base explícita; no se inventan tarifas, demanda o umbrales de aprobación.

Un caso de uso incluirá actor y permiso, disparador, precondiciones,
postcondiciones, flujo principal, alternativas, errores, estados, concurrencia,
impacto en datos e integraciones. Los casos de prueba se derivan de reglas y
ejemplos aprobados, no del resultado que actualmente devuelve el sistema.

SDD se utilizará para especificar cambios con alcance, reglas, ejemplos,
contratos y aceptación. BDD expresa ejemplos de negocio observables; TDD ayuda
a implementar y corregir reglas con evidencia. Son complementarios y se aplican
en proporción al riesgo, sin convertir todo cambio de texto en una suite nueva.

## 4. Backlog de actividades

Prioridades propuestas: **P0** para riesgo monetario, histórico o contradicción que
afecta decisiones de implementación; **P1** para diseño y controles del proceso;
**P2** para extensión y medición después del piloto. No significa que cada P0 sea
un defecto demostrado ni que todas las funcionalidades deban detenerse.

Estado inicial: elementos pendientes salvo la inspección preliminar y las nueve
pruebas descritas arriba. El avance posterior se registra en §10; no implica
cierre del conjunto. Una actividad se cierra con su
entregable y evidencia, no con la afirmación de haber seguido una práctica.

| ID / prioridad | Actividad y entregable | Criterio de aceptación | Depende de |
|---|---|---|---|
| F01 · P0 | Completar inventario de reglas, módulos, contratos, pruebas y deudas; matriz documento/código/control | Cada regla relevante tiene fuente, estado vigente/histórico, área, evidencia y responsable; cobertura faltante explícita | — |
| F02 · P0 | Resolver contradicciones de idioma, nombres, paquetes, casos de uso y generación de clientes; decisión registrada y documentos 03/08 reconciliados | Un mismo cambio recibe la misma instrucción en todos los documentos vigentes; legado y excepciones identificados; sin tocar migraciones aplicadas | F01 |
| F03 · P0 | Caracterizar dinero en interfaz, API y dominio con ejemplos independientes; informe y pruebas de límites. [Primer corte ejecutado](../evidencias/2026-10-10-precision-monetaria.md): cuatro divergencias en total de línea web; dominio y sensibilidad verificados. F03 parcial; API, pagos mixtos y demás límites pendientes | Se ejercitan seis decimales, importes grandes admitidos, cantidades fraccionarias, descuentos, pagos mixtos y redondeo; se identifica una divergencia reproducible o se declara que no se encontró | F01 |
| F04 · P0 | Resolver el incumplimiento de representación decimal de extremo a extremo; decisión técnica, código y contrato. [Primer corte del mostrador](../especificaciones/2026-10-10-decimales-mostrador.md): catálogo textual, cálculo y cobro exactos; F04 parcial, otras pantallas y respuesta histórica pendientes | No hay aritmética monetaria binaria en el alcance corregido; escala/redondeo aprobados; pruebas fallan antes y pasan después; OpenAPI/modelos Angular juntos; compatibilidad evaluada | F03, decisión de redondeo |
| F05 · P0 | Caracterizar integridad histórica de emisor, cliente y representación del documento; matriz de campos y pruebas. [Primer corte F05](../especificaciones/2026-10-10-historia-comprobantes.md): cliente actual y línea histórica por HTTP; emisor por inspección, impresión/XML/legado pendientes | Tras cambiar datos maestros se contrasta documento anterior, reimpresión y XML con valores esperados fijados antes; campos históricos, actuales y legado acordados | F01 |
| F06 · P0 | Corregir conservación histórica cuando se confirme el defecto; instantáneas, migración nueva y representación coherente. [Primer bloque F06](../especificaciones/2026-10-11-conservacion-emision.md): decisión aprobada, captura al emitir; [bloque de archivos originales](../especificaciones/2026-10-11-archivos-originales.md): XML/ZIP/CDR por intento, recuperación del legado y ensayo AWS pendientes | Prueba roja/verde; cambios posteriores no alteran campos acordados; sin reconstruir como históricos valores actuales desconocidos; política para documentos antiguos explícita | F05, decisión de conservación |
| F07 · P1 | Consolidar ficha de oportunidad de negocio del harness | Un comentario real produce problema, actor, proceso, frecuencia, impacto, alternativas incluida no construir, hipótesis y preguntas pendientes | F01 |
| F08 · P1 | Consolidar evaluación económica y criterio de priorización | Dos perspectivas económicas separadas; escenarios y sensibilidad reproducibles; datos desconocidos visibles; propietario decide construir, experimentar, posponer o descartar y fija umbrales | F07 |
| F09 · P1 | Elaborar mapa de procesos y responsabilidades del primer producto | AS-IS/TO-BE de vender, cobrar, emitir, canjear y anular; actores, datos y excepciones conectados con el alcance de las fases existentes | F07 |
| F10 · P1 | Crear especificación y plantilla de casos de uso; aplicar al piloto | Flujos y permisos completos; reglas numeradas, pre/postcondiciones, estados y fallos; revisión funcional del propietario; fuera de alcance explícito | F09 |
| F11 · P1 | Definir requisitos no funcionales y presupuesto operativo | Seguridad, aislamiento, dispositivos, accesibilidad, tiempos, recuperación y costos tienen condición de medición y valor aprobado o pendiente; ninguna cifra inventada se presenta como obligación | F08, F10 |
| F12 · P1 | Documentar arquitectura real con vistas de contexto, módulos y dependencias | Diagrama y matriz coinciden con los cinco módulos backend y aplicaciones frontend inventariados; responsabilidades, datos y fronteras claras; propuestas separadas | F01 |
| F13 · P1 | Definir límites de negocio y contratos entre áreas | Venta/stock/caja/emisión/identidad tienen dueño de invariantes y transacciones; dependencias permitidas y prohibidas, ciclos encontrados y decisión de corrección | F10, F12 |
| F14 · P1 | Publicar estándar Java/OOP/SOLID con revisión de clases representativas | Ejemplos correctos e incorrectos del repositorio; encapsulamiento, reconstitución, igualdad, herencia, puertos, errores y granularidad de casos de uso comprobables | F02, F13 |
| F15 · P1 | Publicar estándar Angular/TypeScript y límites de presentación | Componentes, servicios, estado, formularios, DTO y reglas puras tienen responsabilidades; ciclo de vida reactivo y manejo de errores definidos; prueba sobre punto de venta | F02, F10, F12 |
| F16 · P1 | Completar estándar de datos y contratos | Precisión, zona horaria, nulabilidad, permisos/RLS, constraints, idempotencia, transacciones, evolución API y migraciones inmutables documentados; inventario de DTO manuales/generados | F03, F05, F13 |
| F17 · P1 | Revisar garantías de emisión y recuperación; decisión técnica de publicación | Se demuestra comportamiento ante rollback, fallo después del commit, duplicado, resultado tardío y reintento; se decide recuperación existente u outbox con costo/riesgo, sin imponer infraestructura | F11, F12, F13 |
| F18 · P1 | Definir estrategia SDD/BDD/TDD y catálogo de pruebas por riesgo | Para el piloto hay ejemplos principales, alternativos, negativos, límites, concurrencia y permisos; oráculos independientes; criterio para unitarias/integración/contrato/E2E y verificaciones manuales | F10, F11, F16 |
| F19 · P1 | Ampliar controles de arquitectura de backend y frontend | Dependencias/ciclos y restricciones acordadas se comprueban en módulos aplicables; una mutación aislada que rompe cada nueva regla produce fallo y la restauración pasa | F13, F14, F15 |
| F20 · P1 | Elegir y configurar lint/formato y revisión estática proporcional | Herramientas compatibles y reproducibles; incorporación gradual sin reformateo masivo; incumplimiento introducido falla; restauración pasa; restricciones del dominio no se reducen a estilo | F02, F14, F15 |
| F21 · P1 | Unificar trazabilidad y cambios de especificación | Necesidad → decisión → requisito → ejemplo → prueba → cambio/contrato → PR/commit/CI → versión → medida; relaciones múltiples permitidas; un cambio de regla identifica pruebas/docs afectados | F07, F10, F18 |
| F22 · P1 | Reconciliar y versionar el harness y su arranque | Guías/plantillas útiles persistidas en el repositorio; caches, secretos y estado local excluidos; sesión limpia reproduce arranque; catálogo distingue operativo, propuesto y no verificado | F02, F18, F21 |
| F23 · P1 | Precisar GitFlow, soporte y puertas de CI/CD | Feature→develop y release→main; hotfix desde versión publicada con retorno a develop; convención de commits; controles del mismo head; merge sin eludir protecciones; rama cerrada tras CI posterior | F02, F19, F20, F21 |
| F24 · P1 | Completar operación, seguridad y diagnóstico del piloto | Permisos, datos sensibles, dependencias, correlación, registros y recuperación tienen pruebas o procedimiento; SAST/SBOM y otros controles se clasifican por disponibilidad real; AWS pendiente explícito | F11, F16, F17, F23 |
| F25 · P1 | Ejecutar piloto de fundamentos en un corte de ventas | Emitir y consultar/reimprimir un documento aplica estándares aprobados; cambio acotado; evidencia roja/verde o mutación según caso; pruebas pertinentes y CI completos; sin nueva funcionalidad comercial implícita | F04, F06 si aplica, F10, F14–F24 |
| F26 · P1 | Contrastar piloto en auditoría independiente | Sesión separada sin narrativa heredada; recibe commit y reglas aprobadas; hallazgos reproducibles contrastados; clasificación y resolución del propietario; limitaciones explícitas | F25 |
| F27 · P2 | Extender controles y deuda al resto del sistema por riesgo | Lotes separados para configuración, almacén, consultas, administración y facturación; cada lote tiene alcance, evidencia y CI; sin renombrado global como sustituto de revisión | F26 |
| F28 · P2 | Medir resultado y mantener mejora continua | Línea base y revisión de defectos escapados, soporte, tiempo de cambio y resultado de negocio; dueño, fuente y periodicidad acordados; actualizar backlog por evidencia | F08, F21, F25 |

## 5. Orden de ejecución y límites de cada lote

### Lote 1 — Línea base y decisiones que desbloquean el trabajo

Completar F01 y presentar F02. Entregar matriz de contradicciones, cobertura de
contratos y mapa de archivos del piloto. Resolver idioma de paquetes técnicos,
granularidad de casos de uso y fuente normativa. No cambiar todos los nombres.

En paralelo lógico, pero sin asumir agentes operativos, especificar y ejecutar
F03 y F05. Una caracterización fallida abre una corrección concreta. No reproducir
un error de importe no elimina el incumplimiento de precisión observado en el
frontend: F04 sigue siendo necesario. El riesgo histórico debe reproducirse antes
de afirmar un defecto funcional y delimitar F06.

Salida: diagnóstico reproducible y decisiones registradas. Cada corrección de
dinero o historia será un PR acotado, no un cambio conjunto de toda arquitectura.

### Lote 2 — Negocio, funcional y diseño del piloto

Aplicar F07–F18 a **emitir una venta y consultar/reimprimir su documento**.
Reutilizar las reglas aprobadas, incluida la separación BIEN/SERVICIO y los
tipos de documento; no volver a decidirlas por preferencia técnica.

Entregables: ficha de oportunidad, evaluación económica con incertidumbre,
proceso, casos de uso, ejemplos BDD, contratos y decisiones de arquitectura.
Cada documento tendrá dueño y criterio de revisión. Lo desconocido se pregunta
al propietario; no se rellena con supuestos ocultos.

Salida: una especificación que permita implementar y probar sin inferir reglas
del código actual. No hace falta modelar toda la aplicación antes de este corte.

### Lote 3 — Controles y preparación reproducible

Aplicar F19–F24 solo a las reglas y áreas acordadas para el piloto; después
ampliar. Separar configuración de formato, controles de dependencias y cambios
funcionales para que el PR pueda revisarse.

Salida: CI que detecta infracciones introducidas a propósito, trazabilidad
utilizable, harness versionado y arranque comprobado desde una sesión limpia.
Una herramienta propuesta no se declara instalada hasta ejecutarla.

### Lote 4 — Corrección y demostración

Ejecutar F04, F06 cuando corresponda y F25. Ninguna corrección se cierra solo
porque las pruebas existentes pasen: debe verse el fallo sin el arreglo.
Para comportamientos correctos se exige sensibilidad mediante mutación aislada.

Salida: PR revisable sobre develop, pruebas pertinentes, CI del PR para el
mismo head y CI del commit integrado. No implica despliegue en AWS.

### Lote 5 — Contraste, extensión y seguimiento

F26 antes de escalar el patrón; F27 por área y riesgo; F28 después de disponer
de medidas. El auditor recibe las reglas aprobadas, el código y los límites de
la evaluación, sin heredar la explicación justificativa del implementador.
Sus documentos son evidencia, no sustituyen decisiones del propietario.

### Estimación y capacidad

No hay estimación cerrada del programa completo: faltan inventario semántico,
decisiones de compatibilidad y datos económicos. F01 debe producir una estimación
por actividad con alcance, supuestos, rango y riesgo. Los lotes son orden de
dependencias, no promesa de semanas ni tareas para agentes ya instalados.
Una actividad amplia se divide en PRs verificables antes de ejecutarse; no se
acepta un PR que mezcle convenciones, formato, cambios de contrato y nuevas reglas.

## 6. Puertas de entrada y salida del desarrollo

### Para comenzar a codificar una funcionalidad

1. Necesidad, problema y alcance entendidos; viabilidad y valor evaluados en
   proporción al costo. Decisiones de negocio aprobadas o pendientes explícitas.
2. Reglas, casos de uso, ejemplos y aceptación fijados independientemente del
   comportamiento observado. Contratos y efectos en datos identificados.
3. Diseño del cambio revisable: responsabilidades, dependencias, transacción,
   precisión, historia y efectos externos. Decisiones relevantes registradas.
4. Plan de implementación y comprobación presentado al propietario. Rama y base
   verificadas; respetar develop y el flujo acordado para contribuciones remotas.

Para un defecto urgente se usa la vía de soporte proporcional: impacto y alcance,
reproducción, prueba roja, arreglo mínimo, prueba verde y retorno de la corrección
a las ramas necesarias. No se elimina la trazabilidad por ser un hotfix.

### Para cerrar un cambio

- Requisitos y ejemplos satisfechos; pruebas de corrección o sensibilidad
  registradas según corresponda; expectativas no derivadas de la ejecución.
- Precisión decimal, permisos/aislamiento e integridad histórica verificados en
  el alcance afectado. Documentación y contratos sincronizados.
- Controles pertinentes y CI completos para el commit revisado. No reutilizar
  resultados de otro head para autorizar un merge.
- PR integrado en develop según autorización, CI posterior comprobado y rama
  cerrada según el flujo. main y publicación requieren el flujo de release.
- Evidencia, limitaciones y riesgo residual declarados; despliegue y resultados
  comerciales no se afirman a partir de pruebas locales.

## 7. Responsabilidades y decisiones pendientes

El propietario decide negocio, alcance, presupuesto, redondeo, conservación de
datos, prioridades y excepciones que contradigan el contrato. Codex puede preparar
inventario, diseño, implementación, controles y evidencia dentro de la autorización.
El contraste independiente se asigna a otra sesión/revisor; no es un agente
instalado que esta propuesta dé por disponible.

Decisiones que debe llevar F02 al propietario con alternativas y ejemplos reales:

1. Mantener temporalmente los paquetes técnicos en inglés como excepción
   documentada o planificar su migración; nunca autorizarlo tácitamente.
2. Conservar «un caso de uso por clase» o sustituirlo por una regla de cohesión
   explícita con excepciones limitadas para consultas.
3. Determinar campos que deben conservarse históricamente y tratamiento de
   documentos anteriores sin instantánea, después de F05.
4. Fijar umbrales económicos y no funcionales con datos, después de F08/F11.

Las decisiones de redondeo ya vigentes deben localizarse antes de solicitar
otra; solo se consultará una decisión nueva si existe un caso no cubierto.

## 8. Qué no demuestra este plan

No demuestra aplicación completa de SOLID ni cumplimiento de todos los requisitos;
no demuestra rentabilidad; no acredita CMMI; no confirma configuración productiva,
integraciones fiscales reales ni auditoría independiente. Demuestra una inspección
preliminar, nueve controles ejecutados y un backlog que permite cerrar esas
incertidumbres con entregables y criterios concretos.

## 9. Investigación complementaria solicitada antes de implementar

La [investigación de requisitos, UX/UI y seguridad](../investigacion/2026-10-10-requisitos-ux-seguridad/README.md)
profundiza en historias, casos de uso completos, pruebas, interacción y controles
OWASP actuales. Incluye fuentes primarias, plantillas, ejemplo didáctico de cajero
y actividades ACT-01–ACT-23 vinculadas a este backlog. Es documentación propuesta;
no demuestra controles ejecutados ni autoriza funcionalidades nuevas.


## 10. Priorización añadida: revisión de Dependabot, 2026-10-10

Por solicitud del propietario se incorpora **ACT-23**, vinculada a
F01/F20/F23/F24, en el
[backlog detallado, §5](../investigacion/2026-10-10-requisitos-ux-seguridad/backlog.md#5-act-23-revisión-y-selección-de-actualizaciones-de-dependabot).
Se conserva el catálogo existente. La tarea evalúa los 18 PR #13–#30 y produce
una decisión técnica individual: integrar, adaptar, posponer o descartar.

Prioridad general P1; el triaje comienza en el inventario inicial junto con
ACT-14/15, después de atender el cierre del bloque ACT-13. Elevar a P0 únicamente
las propuestas con riesgo de seguridad aplicable confirmado o bloqueo real de
controles indispensables. Coordinar escáner/CI con ACT-19. Las actualizaciones
compatibles elegidas preceden a cambios generales de estilo y a mejoras
cosméticas; los saltos mayores sin necesidad demostrada pueden quedar en P2.
Los riesgos P0 de dinero e historia mantienen prioridad por evidencia.

La inspección confirmó que todos esos PR apuntan a main y sus títulos usan
`Actualiza`; alinear las nuevas propuestas con develop y Conventional Commits
forma parte de ACT-23. No se ha evaluado todavía la compatibilidad ni el CI de
cada propuesta; ninguna se declara adecuada para integrar por este registro.
No se cambia código ni configuración de Dependabot en este lote documental.

## 11. Ejecución autorizada del primer lote, 2026-10-10

El propietario autorizó comenzar el backlog. Se conserva este catálogo y los
IDs ACT del backlog detallado. La revisión completa y la auditoría independiente
no están realizadas. Base: develop `0df3f6d57a8b0dcece6f7667ce605c7fa307dc15`.

### F01: inventario inicial reproducible

Conteo por `src/main/java` y `src/test/java` del reactor. Archivos de prueba no
equivalen a casos ejecutados; cantidad de archivos no demuestra calidad.

| Módulo | Archivos Java de producción | Archivos Java de prueba |
|---|---|---|
| `ondexia.domain` | 101 | 14 |
| `ondexia.api` | 153 | 45 |
| `ondexia.admin` | 18 | 10 |
| `ondexia.consultas` | 19 | 6 |
| `ondexia.facturacion` | 17 | 7 |

| Área/regla | Contrato/implementación localizada | Estado y siguiente comprobación |
|---|---|---|
| Audiencia JWT obligatoria | CLAUDE; SeguridadPasarelaConfig; FirmaDelTokenIT | ACT-13: defecto reproducido y arreglo local en verificación; CI/revisión independiente pendientes |
| Firma/emisor/uso/audiencia configurados | FirmaDelTokenIT, perfil AWS | Suite focal; no representa inventario completo de autenticación ni AWS real |
| Rotación de claves | JWKS inmutable de SeguridadPasarelaConfig | ACT-14 pendiente: caracterizar clave nueva y recuperación sin asumir infraestructura |
| Aislamiento/permisos | RLS, contexto y pruebas de grants/permisos existentes | ACT-15 pendiente: matriz negativa de lectura/escritura y conexiones reutilizadas |
| Importes decimales | Dominio BigDecimal; catálogo del mostrador textual y cálculo decimal exacto en el primer corte F04 | F03/F04 parciales: consulta histórica, cajas, notas de crédito y otras pantallas pendientes; evidencia del corte enlazada en F04 |
| Historia de documentos | DocumentoVentaAdaptador carga cliente por ID actual; logo conservado por cambio previo | F05/F06 pendientes: caracterizar datos maestros e identificar campos históricos acordados |
| Emisión y recuperación | EmisionElectronica.afterCommit; BusDeEmision S3/memoria | F17 pendiente: rollback, caída poscommit, duplicados, resultados tardíos y recuperación |
| Contratos HTTP | OpenAPI exportado y modelo de almacén regenerado | F16 pendiente: catálogo de DTO manuales/generados y consistencia de toda API |
| Arquitectura | Nueve restricciones de ArquitecturaTest; cinco módulos del reactor | F12/F19 pendientes: ciclos, fronteras entre áreas y contratos semánticos |
| Convenciones/manual | Ampliación local 717246d, rama feature/manual-arquitectura-hexagonal | Manual preparado, aún no integrado; no cerrar F02/F14 por documentación |
| UX/UI | Documento 10, vistas de ventas y pruebas existentes | ACT-06/07 pendientes: tareas, estados, teclado/foco y móvil; no conformidad WCAG declarada |
| DevSecOps | ci.yml/deploy.yml; producción rechaza falta NVD_API_KEY | ACT-19 pendiente: sensibilidad y evidencia del control; evitar duplicar la puerta existente |

F01 queda **en curso**: falta inventario detallado de reglas/controles por área,
consumidores y configuración efectiva. ACT-13 se registra en su
[especificación](../especificaciones/2026-10-10-audiencia-jwt.md) y
[evidencia](../evidencias/2026-10-10-audiencia-jwt.md). Los demás puntos conservan
estado pendiente; no se deduce un defecto funcional solo por observar código.
