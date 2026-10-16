# Landing interactiva · producto y escritura

**Estado del documento.** La especificación vigente es la revisión del
2026-10-15 al final de este documento, registrada antes de implementar esa
revisión. Los apartados anteriores conservan el historial del 2026-10-14.
Quedan sustituidos donde exigen variantes móviles, tres figuras independientes
fijadas o parallax de ±12 píxeles. Su evidencia no verifica la revisión nueva.

## Necesidad

Tras desplegar y revisar la versión candidata anterior, el propietario solicita
una presentación más interactiva inspirada en la página de ChatGPT, con capturas
reales del sistema y una escritura más adecuada. No se alteran precios, reglas
de negocio, autenticación ni emisión fiscal.

Base: develop `69290ea19f6a27279460c57d271c73e3c5d6a5b7`.
Rama: `feature/landing-interactiva`. La autorización se refiere al diseño y las
interacciones; no supone que SUNAT, correo o condiciones comerciales pendientes
se hayan verificado ni autoriza desplegar AWS en esta ejecución.

## Referencia y límites de observación

Se consultó la [página oficial de ChatGPT](https://chatgpt.com/es-ES/overview/?openaicom_referred=true).
Su estructura vincula acciones, imágenes de tareas dentro del producto,
explicaciones de utilidad y siguientes pasos. El contenido y los enlaces se
contrastaron en la página; el lector web no permite observar las animaciones
exactas. Selectores, transiciones y ampliación son propuestas para Ondexia,
no efectos copiados ni comportamientos externos dados por comprobados.

## Plan presentado

1. Mostrar el producto Angular real con capturas nuevas y datos de demostración.
2. Sustituir la portada en dos columnas por un titular breve y una demostración
   central. Relacionar cada pantalla con su tarea y utilidad.
3. Incorporar selección manual de pantallas, ampliación y transiciones de entrada.
4. Mantener teclado, foco, móvil, movimiento reducido y contenido sin JavaScript.
5. Verificar build, restricciones de seguridad y navegación con evidencia.
6. Entregar vista local y capturas para evaluación; no desplegar en AWS.

## Convenciones de escritura

Se complementan doc 10 §2 y las notas SEO existentes:

- Abrir con una tarea reconocible, no con una lista de módulos o un eslogan vacío.
- Escribir una idea por titular y una explicación breve por pantalla.
- Usar verbos concretos: preparar, cobrar, revisar, consultar, previsualizar.
- Explicar lo que permite hacer una función y mostrarlo en el producto.
- Evitar encadenar infinitivos en párrafos que solo repiten el titular.
- Mantener acciones en infinitivo, español formal y términos fiscales precisos.
- No asumir demostración asistida, atención 24/7, ahorro, certificaciones,
  demanda de clientes o rentabilidad sin evidencia.
- Distinguir capturas reales de interfaz con datos sintéticos de una captura de
  producción o de una maqueta. Mostrar la identificación cerca de la galería.

Las escenas de venta, registro y documento representan el mismo recorrido. La
emisión real ante SUNAT continúa en ensayo según la documentación vigente.

## Interacciones y casos de uso · histórico del 2026-10-14

**Explorar el producto.** Las tres escenas permanecen visibles como capítulos.
El desplazamiento actualiza el indicador; enlaces y Anterior/Siguiente permiten
navegar directamente. El salto solicitado coloca el foco en la escena y el
desplazamiento por sí solo no lo mueve. Si el script no carga, los artículos y
sus enlaces ancla siguen disponibles. La evolución detallada se especifica abajo.

**Ampliar la pantalla.** Abrir un diálogo de la escena actual con nombre accesible.
Escape y Cerrar lo cierran; el foco vuelve al control que abrió el diálogo. El
enlace al original permanece como alternativa. El contenido no se fabrica ni
se modifica por encima de la captura. Cada apertura comienza desde arriba,
aunque se haya desplazado otra escena antes.

**Recorrer la página.** Las entradas y cambios usan los tokens de movimiento
existentes. Con movimiento reducido no hay animación de desplazamiento. El
contenido no se oculta estáticamente a la espera de JavaScript. En móvil, cargar
una captura tomada a ancho móvil; evitar encoger una pantalla de escritorio hasta
hacer ilegible el texto.

## Seguridad y arquitectura

La landing sigue siendo estática y aislada de API, Cognito y datos. El propietario
solicita interacciones: se sustituye la decisión anterior de cero JavaScript por
mejora progresiva local mínima, sin frameworks, analítica, proveedores o red remota.

La CSP vigente deniega scripts por `default-src 'none'`. Permitir únicamente
`script-src 'self'` para archivos generados por Astro. No permitir scripts inline,
eval, conexiones ni iframes; conservar el resto de restricciones. Verificar el
comportamiento con cabeceras equivalentes a la política Terraform, no solo leyendo
la cadena. Aplicar esa política en AWS continúa pendiente del despliegue revisado.

## Aceptación y oráculos · histórico del 2026-10-14

- Tres escenas reales, identificadas, con rutas de asset existentes y procedencia.
- Enlaces y Anterior/Siguiente recorren las escenas sin recargar la página.
- Navegación nativa por teclado, Escape y retorno del foco funcionan según el caso de uso.
- Sin JavaScript, las escenas siguen visibles y los enlaces originales funcionan.
- Script local operativo bajo CSP; script inline y conexión externa rechazados.
- 320/390/768/1024/1440 px sin desbordamiento horizontal de página; ampliación móvil
  puede desplazarse dentro del diálogo y conserva control de cierre accesible.
- Texto normal con contraste 4,5:1; pesos, radio y marca del doc 10 conservados.
- Capturas provenientes de componentes Angular, sin HTML del producto inventado.

Los esperados se definen aquí antes de observar el resultado. La evidencia debe
separar bloqueos por CSP, defectos funcionales y problemas de preparación. CI,
AWS, dispositivos físicos, conversión y WCAG completa solo se declaran verificados
si realmente se ejecutan sus comprobaciones.

## Evolución: recorrido por escenas · histórico del 2026-10-14

La revisión del 2026-10-15 sustituye este recorrido donde prescribe capturas
móviles, tres figuras separadas y movimiento de ±12 píxeles. Se conserva para
interpretar la evidencia de esa candidata, no como aceptación vigente.

El propietario aclara que busca contenido interactivo «casi como diapositivas».
Se prepara una revisión local sobre la candidata 4350294, sin publicarla. Se
presentó una elección opcional entre avanzar al bajar y avanzar con botones;
sin respuesta después del plazo de consulta, se toma como propuesta el recorrido
al bajar, manteniendo controles manuales. Una respuesta posterior puede ajustar
esta decisión de presentación; no afecta reglas del negocio.

Plan presentado: tres escenas que cuentan preparación, registro y consulta;
capturas con movimiento suave, navegación manual alternativa y movimiento
reducido sin parallax. Se reutilizan los PNG acreditados, sin inventar interfaz.

### Flujo y extensiones

1. El visitante encuentra las tres escenas en el orden de la venta. Todas siguen
   en el documento: avanzar no oculta ni sustituye artículos completos.
2. Al bajar, el indicador señala la escena alcanzada; no mueve el foco ni anuncia
   cada cambio al lector de pantalla. No hay temporizador ni avance autónomo.
3. Los enlaces de escenas y Anterior/Siguiente permiten avanzar directamente.
   El avance solicitado coloca el foco en la escena y deja su título visible
   bajo la cabecera y la navegación. Los extremos no permiten rebasar 1 y 3.
4. Ampliar sigue abriendo la captura correspondiente desde arriba; Cerrar/Escape
   retornan el foco a su botón. Los originales mantienen enlaces móviles reales.
5. En una ventana amplia, la figura puede fijarse dentro de su artículo solo si
   cabe bajo los controles. En móvil, ventana baja, zoom que reduzca espacio o
   movimiento reducido, permanece en el flujo normal.
6. El parallax mueve solamente el marco de la captura entre −12 y +12 píxeles,
   ligado al desplazamiento nativo, sin bloquear rueda, teclas ni gestos. No
   modifica los PNG ni agrega una ilustración presentada como interfaz real.
7. Movimiento reducido elimina el parallax y la fijación de figuras. Si cambia mientras la
   página está abierta, la mejora se desactiva sin recargar ni perder contenido.
8. Sin JavaScript quedan las tres escenas y enlaces ancla operativos. Los botones
   que requieren código se habilitan únicamente después de inicializarlo.

La regla del doc 10 §6 sobre entradas/salidas de avisos y diálogos se conserva.
Este movimiento continuo se acota a la demostración pública por la solicitud de
una landing interactiva y se documenta en §16; no se generaliza a formularios,
comprobantes ni otras pantallas de trabajo.

### Oráculos del recorrido

- Tres artículos visibles tanto con JavaScript como sin él. Navegación por
  capítulos con `aria-current="step"`, sin roles de pestaña sobre varios paneles
  simultáneamente visibles.
- Primera, segunda y tercera escenas corresponden a los índices 1, 2 y 3 fijados
  en la especificación; enlace, avance manual y desplazamiento concuerdan.
- La transformación de la captura cambia al desplazar la escena y siempre queda
  dentro de ±12 px; una animación breve de entrada no satisface ese oráculo.
- Bajo movimiento reducido la transformación es cero y la figura no está fija.
- Cada navegación manual termina con título y foco visibles; no hay cambio
  automático de foco por desplazamiento.
- 320/390/768/1024/1440 px, altura reducida y zoom: sin desbordamiento horizontal
  de página ni controles o contenido inaccesibles por una fijación excesiva.
- Persisten ampliación, seguridad CSP, contenido identificado y originales.

El build anterior se conserva fuera del repo para contraste. La revisión nueva
debe demostrar esos comportamientos en navegador y registrar los pendientes.

## Revisión vigente · 2026-10-15 · ventana central y capturas de computador

### Necesidad, alcance y estado

El propietario solicita capturas completas de **computador también en móvil**,
una animación sincronizada con el desplazamiento y estudiar una forma adecuada
de incorporar morphing. El plan presentado se concreta aquí antes del código.
Estado: especificación pendiente de implementación y validación; no constituye
evidencia de comportamiento, rendimiento ni compatibilidad ya comprobados.

La demostración utiliza una sola ventana central grande y tres capítulos:
preparar la venta, revisar el registro y previsualizar el documento. Se conservan
la escritura tarea → demostración → utilidad → acción, los datos sintéticos
identificados y la separación de API, autenticación y proceso fiscal. No se
alteran precios, CTA comercial, contacto, condiciones ni el ensayo ante SUNAT.
No se autoriza publicación en AWS.

### Capturas y composición

- Utilizar PNG completos del frontend real a **1440 × 1000**, sin recortes del
  componente ni reconstrucción de HTML: `venta-completa.png`,
  `registro-completo.png` y `documento-completo.png`.
- En cualquier ancho, incluido móvil, solicitar esos mismos archivos de
  computador. Ajustar la imagen completa conservando su proporción; no sustituir
  el asset por una captura móvil, recortar con `cover` ni deformar los píxeles.
- La ventana central contiene las tres capas de imagen para los fundidos. Su
  representación accesible es un único `role="img"` con nombre y descripción
  compartidos de la escena actual; las capas duplicadas son decorativas para
  tecnologías de asistencia. El marco o fondo SVG también es decorativo.
- Mantener controles compartidos estables fuera de las capas que cambian:
  capítulos, ampliación y «Ver sin animación». Un cambio por desplazamiento no
  oculta controles enfocables ni mueve el foco. El contenido no es una aplicación
  operativa ni permite registrar datos desde la landing.

### Flujo y alternativas

1. La base HTML muestra tres artículos completos y enlaces ancla en el orden de
   la tarea. Sin JavaScript, con movimiento reducido, con «Ver sin animación»,
   con altura inferior a 700 píxeles CSS o si no cabe la composición bajo los
   controles, se mantiene este flujo normal, sin fijación ni animación de la
   demostración. El progreso de la alternativa estática dice «3 escenas».
2. Habilitar la presentación central solo después de inicializar las capacidades
   necesarias, con altura de al menos 700 píxeles CSS y ajuste efectivo de ventana,
   texto y controles. El ancho móvil por sí solo no obliga a cambiar de imagen
   ni impide la mejora si cabe. Recalcular al redimensionar o cambiar preferencias.
3. El desplazamiento nativo determina un progreso continuo `p` entre 0 y 1 sobre
   el tramo de la demostración. Bajar avanza y subir reproduce los mismos estados
   en reversa. No introducir temporizadores, avance autónomo, captura de rueda,
   bloqueo de teclas/gestos ni desplazamiento simulado.
4. Los tres enlaces de capítulos ofrecen navegación directa. Indicar el capítulo
   alcanzado mediante `aria-current="step"`; el desplazamiento por sí solo no
   cambia el foco ni anuncia cada fotograma. Los saltos solicitados conservan
   título y foco visibles. No utilizar semántica de pestañas para el recorrido.
5. «Ver sin animación» desactiva la presentación central, la fijación y el
   movimiento, y deja visibles los tres artículos sin perder el punto de lectura
   ni el foco. El cambio de `prefers-reduced-motion` se atiende con la página
   abierta. No hace falta una cuenta ni un servicio para esta alternativa.
6. Ampliar abre la captura de computador correspondiente ajustada al diálogo.
   Ofrecer «Tamaño real» a 1440 píxeles CSS con desplazamiento horizontal y vertical
   dentro del diálogo, y una opción para volver a ajustar. Mantener controles de
   cierre accesibles, Escape y retorno del foco al botón de apertura. Cada
   reapertura comienza ajustada y con ambos desplazamientos en cero. El enlace
   al PNG original permanece disponible sin JavaScript.

### Sincronización, fundidos y morphing

Usar animaciones WAAPI pausadas, con duración de referencia `D` y
`Animation.currentTime = p × D`. La duración es una coordenada, no un reloj que
avance. Un listener pasivo de `scroll` solicita como máximo un trabajo pendiente
con `requestAnimationFrame` para coordinar lecturas y actualizaciones. No afirmar
que rAF limita la frecuencia de los eventos ni garantiza fluidez. Las mediciones
geométricas se actualizan cuando cambie el espacio para separar las lecturas del
tramo de las actualizaciones continuas. El SVG puede provocar layout y pintura:
se mide ese coste, sin afirmar que toda la composición ocurra fuera del hilo principal.

La elección evita depender de `animation-timeline` o `ScrollTimeline` como
requisito de lectura. Son opciones nativas válidas donde exista soporte, pero
la investigación de compatibilidad no permite suponer disponibilidad uniforme,
en particular Firefox estable. Si falla la mejora, conservar la base estática.

Los fundidos de las capturas tienen offsets `[0, 0.2, 0.3, 0.7, 0.8, 1]`:
venta legible entre 0 y 0.2; fundido venta → registro entre 0.2 y 0.3; registro
legible entre 0.3 y 0.7; fundido registro → documento entre 0.7 y 0.8; documento
legible entre 0.8 y 1. El capítulo actual es el más próximo a sus hitos 0, 0.5 y
1, resolviendo un empate hacia el siguiente. Las capturas solo se ajustan de
forma proporcional y se componen por opacidad; el movimiento decorativo acompaña
al marco, sin perspectiva, estiramiento ni morphing de la interfaz.

El morphing opcional se limita al marco o fondo SVG, sin dependencia externa.
Definir tres formas en `p = 0`, `0.5` y `1` con idéntica estructura
`M Q Q Q Q Z`: mismo número, tipo y orden de comandos, y el mismo número de
parámetros. Interpolar sus coordenadas por tramos, ligadas al mismo progreso.
Una sustitución discreta de la forma no acredita morphing. El SVG no recorta
contenido esencial ni intercepta interacción; su coste debe medirse antes de
conservar el efecto. La ausencia de soporte deja un contorno estático.

### Investigación y decisión técnica

Fuentes primarias consultadas el 2026-10-15; sus recomendaciones orientan esta
propuesta, no verifican la implementación de Ondexia:

| Fuente | Hallazgo y consecuencia |
|---|---|
| [MDN: Animation.currentTime](https://developer.mozilla.org/en-US/docs/Web/API/Animation/currentTime) | Permite posicionar una animación incluso pausada; sirve para recorrer estados desde `p`. |
| [Chrome: animaciones ligadas al desplazamiento](https://developer.chrome.com/docs/css-ui/scroll-driven-animations) | El progreso puede seguir el desplazamiento en ambos sentidos. La vía nativa ofrece ventajas de ejecución; no supone que una vía JavaScript las herede. |
| [MDN: animation-timeline](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/animation-timeline), [ScrollTimeline](https://developer.mozilla.org/en-US/docs/Web/API/ScrollTimeline), [BCD de CSS](https://github.com/mdn/browser-compat-data/blob/main/css/properties/animation-timeline.json) y [BCD de la API](https://github.com/mdn/browser-compat-data/blob/main/api/ScrollTimeline.json) | MDN los marca con disponibilidad limitada; los datos consultados registran Firefox como `preview`. No convertirlos en requisito ni afirmar soporte de Firefox estable. Revisar de nuevo al evaluar navegadores. |
| [MDN: Document.scroll](https://developer.mozilla.org/en-US/docs/Web/API/Document/scroll_event) | rAF coordina actualizaciones, pero no reduce por sí solo la frecuencia frente a `scroll`. |
| [web.dev: guía de animaciones](https://web.dev/articles/animations-guide) | Preferir `transform` y `opacity`; medir layout y pintura al incorporar SVG, recortes u otras propiedades. No presuponer que `clip-path` o `d` se componen sin coste. |
| [W3C SVG 2: propiedad d](https://www.w3.org/TR/SVG/paths.html#DProperty) | La interpolación suave exige la misma estructura de comandos en los paths. |
| [W3C: Understanding 2.3.3](https://www.w3.org/WAI/WCAG22/Understanding/animation-from-interactions.html) y [MDN: prefers-reduced-motion](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/At-rules/@media/prefers-reduced-motion) | Ofrecer desactivación y respetar la preferencia. 2.3.3 es de nivel AAA: adoptarlo aquí no demuestra conformidad completa ni cambia el objetivo general WCAG 2.2 AA. |

### Aceptación y oráculos fijados antes del código

| Progreso | Opacidad venta / registro / documento | Capítulo actual |
|---|---|---|
| 0 % | 1 / 0 / 0 | Preparar la venta |
| 25 % | 0.5 / 0.5 / 0 | Revisar el registro |
| 50 % | 0 / 1 / 0 | Revisar el registro |
| 75 % | 0 / 0.5 / 0.5 | Previsualizar el documento |
| 100 % | 0 / 0 / 1 | Previsualizar el documento |

- Los tres PNG tienen dimensiones naturales de 1440 × 1000. En
  320/390/768/1024/1440 píxeles CSS, `currentSrc` corresponde a los mismos assets
  de computador, completos y con proporción conservada; no a variantes móviles.
- Los cinco estados de la tabla se observan al bajar y al volver a subir. Con
  `p` inmóvil, las animaciones permanecen pausadas y `currentTime` no avanza al
  esperar; cambiar el tiempo de reloj no sustituye mover el progreso.
- Si se conserva morphing, el path intermedio difiere de ambos extremos del
  tramo, mantiene `M Q Q Q Q Z` y coincide al recorrer el mismo `p` en reversa.
  Los PNG y sus hashes no cambian por el efecto.
- En la presentación central existe una sola representación accesible de imagen
  y descripción actual; las capas decorativas no duplican anuncios. Ningún
  control enfocado desaparece al desplazar o cambiar capítulo.
- Sin JavaScript, con movimiento reducido, al pulsar «Ver sin animación», con
  altura inferior a 700 o sin ajuste efectivo, se muestran los tres artículos
  en flujo normal, sin fijación ni animación de la demostración. Probar cambios
  de preferencia y tamaño mientras la página está abierta, además del inicio.
- El diálogo ofrece ajuste y tamaño real de 1440 con paneo interior; reabre
  ajustado en origen cero, se cierra con Escape/Cerrar y devuelve el foco. El
  estado ampliado no introduce desbordamiento horizontal de toda la página.
- Con la CSP del sitio, los archivos locales funcionan y scripts inline, eval
  y conexiones permanecen rechazados. No se solicitan recursos de terceros.
- Registrar un perfil de rendimiento del recorrido, navegador, dimensiones y
  condiciones, con tareas largas observadas y trabajo de layout/pintura. Medir
  el coste del morphing; retirarlo si perjudica la tarea. No declarar «60 fps» ni
  ausencia de pausas sin medición suficiente. No hay resultado de perfil aún.

Reutilizar los comprobadores del harness existente al implementar; no crear una
suite paralela para convertir esta prosa en evidencia. Registrar por separado
fallos, mutación de sensibilidad, restauración y límites cuando se ejecuten.
Al fijar esta especificación, build, navegadores, accesibilidad y rendimiento
de la revisión nueva estaban pendientes. Las evidencias del 2026-10-14 permanecen
históricas; el resultado posterior se enlaza al final. La CSP conserva
`default-src 'none'` y `script-src 'self'`, sin ampliar API, autenticación ni red.
AWS no se ha modificado ni verificado y la publicación requiere su revisión propia.

## Resultado de la revisión del 2026-10-15

La candidata implementa el contrato anterior en la misma rama de trabajo. La
[evidencia de esta revisión](../evidencias/2026-10-15-escenario-landing.md) conserva
procedencia, fallos previos, correcciones, mutaciones restauradas, revisión
independiente, comprobaciones de navegador y perfil descriptivo. Es una propuesta
local para evaluación del propietario; no acredita CI, publicación AWS, soporte
de otros navegadores, teléfonos físicos ni conformidad WCAG completa. La
aceptación visual y el efecto sobre conversión siguen pendientes.


## Revisión del recorrido continuo — 2026-10-15

Sustituye la composición de ventana central descrita arriba para la siguiente
iteración. Autoridad: capturas y explicación del propietario recibidas el 15 de
octubre. No modifica reglas comerciales, fiscales ni autenticación.

### Flujo y oráculos de aceptación

1. En la portada, Vender / Consultar / Imprimir seleccionan la captura de punto
   de venta / registro / documento respectivamente y sus elementos ilustrativos.
   Hover y foco ofrecen la misma vista; clic o toque conservan la selección.
2. En escritorio con espacio suficiente, el scroll nativo mantiene la captura
   centrada. En el primer 16 % del recorrido salen los complementos; entre el
   16 y el 30 % la captura se desplaza a la derecha y aparece el acordeón.
3. Entre el 30 y el 100 %, tres tramos iguales abren Venta / Registro / Documento.
   Cada barra representa el progreso dentro de su tramo, no tiempo transcurrido.
   El centro de cada tramo debe mostrar 50 % y su PNG correspondiente. Subir
   revierte todos los estados; quedarse quieto no avanza. Al finalizar se libera
   la fijación y aparece Herramientas. Los encabezados también navegan por clic
   y teclado. El foco no se mueve por scroll.
4. Herramientas ofrece tarjetas con flechas, desplazamiento táctil y enlaces a
   las capturas originales. No recortar las pantallas ni presentar ilustraciones
   como capturas. Planes dirige a /planes.html con los importes y condiciones vigentes.
5. Privacidad y seguridad ofrecen contacto para consultas sin presentar una
   política aprobada o certificación. El alcance tributario en ensayo se conserva.
   El cierre mantiene Solicitar acceso y ofrece Ingresar a la aplicación existente.

### Alternativas y límites

Sin JavaScript, movimiento reducido, ancho inferior a 1024 px o altura inferior
a 700 px, conservar los tres artículos y originales en flujo normal. Los verbos
son enlaces ancla en esa alternativa. Conservar ampliación, Escape, foco de
retorno y tamaño real. No forzar desplazamiento horizontal de la página.
El acordeón no oculta un control enfocado al avanzar: solo cambia el párrafo,
con encabezados estables. El cambio dinámico de tamaño o preferencia conserva
la escena y el foco. Un cambio de política CSP que bloquee JavaScript debe dejar
el contenido disponible. No se conectan servicios ni se despliega AWS.

### Verificación

Extender el harness de interactividad existente; sus controles anteriores quedan
reproducibles como históricos. Fijar los oráculos anteriores antes de implementar,
registrar fallo sobre el build previo, ejecutar escritorio, móvil, teclado,
alternativas y CSP sobre la corrección. La inspección propia no se denomina
auditoría independiente. Capturas provenientes de frontend real y datos sintéticos.
