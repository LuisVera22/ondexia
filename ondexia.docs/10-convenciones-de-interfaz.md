# Ondexia — Convenciones de interfaz

Lo que la aplicación ya hace y por qué. No es una guía de estilo aspiracional:
cada regla de aquí está implementada, y la mayoría tiene una prueba detrás.

Escrito para el momento en que haya que tocar algo dentro de seis meses y la
pregunta sea «¿esto está así por un motivo o porque salió así?».

---

## 1. Dónde vive cada cosa

| Qué | Dónde |
|---|---|
| Tokens de color, tipografía, movimiento | `apps/frontend/ondexia.web/src/styles.css`, bloque `@theme` |
| Reglas globales (foco, cursores, barras de desplazamiento) | mismo archivo, `@layer base` |
| Piezas con estado que no se resuelven con utilidades | mismo archivo, `@layer components` |
| Componentes compartidos | `src/app/shared/components/comunes/` |
| Marco de la aplicación | `src/app/shared/layout/` |

**Nada de esto se escribe dos veces.** Cuando algo se repitió —la colocación de
los paneles flotantes, las clases del botón primario— acabó divergiendo, y el
arreglo se hizo en una copia y no en la otra. Ver §8.

---

## 2. Tono

**Un solo registro, en la aplicación y en la landing: formal, impersonal,
preciso.** Se decidió en la iteración 7 (doc 12 §7.2) y se aplicó de una vez a
las dos superficies.

Lo que había antes no era otro registro, era **indecisión**: los errores
tuteaban («Tu sesión ya no es válida. Vuelve a ingresar»), «Sin permisos»
trataba de usted, el panel saludaba con «Buenos días», y la landing hablaba de
chamba, de que el comprobante salía al toque y de no paltearse. Dos registros en
la misma sesión se leen como dos productos.

| Regla | Se escribe | No se escribe |
|---|---|---|
| Las acciones, en infinitivo | «Emitir comprobante», «Cerrar caja» | «Emite tu comprobante» |
| Los mensajes, impersonales | «La sesión ha caducado. Es necesario ingresar de nuevo» | «Tu sesión ya no es válida. Vuelve a ingresar» |
| Ningún peruanismo ni coloquialismo | «Anular una venta emitida» | «No te paltees» |
| Sin exclamaciones, sin humor, sin saludos | «Panel» | «¡Buenos días!» |
| Los términos de SUNAT, exactos | «Boleta de venta electrónica», «adquirente», «comunicación de baja» | «boletita», «cliente» donde SUNAT dice adquirente |
| Los errores dicen qué hacer | «Para emitir factura el cliente debe tener RUC» | «Datos inválidos» |

La tabla de mensajes por código HTTP vive en `src/app/nucleo/errores.ts`, que es
la que más se ve. Es el sitio por el que empezar cuando el tono se desvíe.

**Lo verifica el CI**, no la revisión: `herramientas/comprobar-tono-y-forma.mjs`
busca una lista cerrada de palabras prohibidas en la salida **compilada** de la
aplicación y de la landing. Sobre el código no funcionaría —los comentarios
explican en español lo que la pantalla decía antes, y pondrían el CI en rojo por
documentar la decisión—; sobre `dist/` lo que queda es lo que el cliente lee.

---

## 3. Color

### La paleta es propia, no de la plantilla

La marca es **índigo `#4f46e5`** (`--color-brand-500`). Sustituyó al azul
`#465fff` que veníamos arrastrando de la plantilla en la que nos inspiramos.
No fue cuestión de gusto: el 500 pasó de 4,84:1 a **6,29:1** contra el blanco,
así que el botón primario dejó de aprobar por los pelos y quedó margen para el
estado deshabilitado y para el `hover`.

El 500 es un paso más oscuro de lo que el nombre sugiere, a propósito: es el
tono que va bajo texto blanco en cada botón, y ahí el contraste manda sobre la
simetría de la escala.

### Un tono no siempre sirve para los dos temas

Es la regla que más veces ha aparecido. Cuando un color tiene que leerse sobre
blanco **y** sobre `--color-gray-900` (`#171c2d`), a menudo **no existe ningún
valor que cumpla en ambos**. Comprobado dos veces:

| Uso | Claro | Oscuro | Solución |
|---|---|---|---|
| Texto de error | `#d92525` cumple | no llega | `.dark` redefine `--color-error-500` a `#f87171` |
| Contorno de foco | `brand-500` = 6,29:1 | 2,69:1 | `.dark` usa `brand-400` (5,68:1) |

Cuando aparezca el tercer caso, la salida es la misma: **redefinir el token
bajo `.dark`**, no inventar una clase paralela. Tailwind emite `var(--color-…)`
en lugar de incrustar el hexadecimal, así que redefinir el token funciona.

### Umbrales que se aplican

- **4,5:1** para texto.
- **3:1** para lo que no es texto: contornos de foco, bordes de control, puntos
  de estado.

Un caso concreto para no repetirlo: el texto atenuado de las tablas se puso
primero en `gray-400` —**2,6:1**, ilegible— y se corrigió a `gray-500`
(`#697083`), que da 4,98:1 sobre blanco y 4,78:1 sobre la fila rayada, que es
el otro fondo sobre el que puede caer. **Atenuar es bajar el peso de un dato,
no dejarlo a medio leer.**

---

## 4. El color nunca va solo

Todo estado que se comunica con color lleva además una forma o una palabra:

- Las insignias de estado llevan **punto de color + texto**, nunca solo el
  color.
- La opción vigente de un desplegable lleva **una marca de verificación**, no
  solo el fondo resaltado — que además se confunde con el fondo del puntero
  encima.
- En los indicadores del panel, **la flecha dice el movimiento y el color dice
  si conviene**: son dos cosas distintas. «Por cobrar» bajando lleva flecha
  hacia abajo y color verde, porque significa que los clientes están pagando.
  Cada indicador declara su propia dirección de «mejor».

---

## 5. Forma

**Un radio y una sombra.** Minimalismo es quitar, no decorar poco.

```
--radius-base: 6px    el único radio; `rounded-full` solo en insignias y avatares
--shadow-flotante     la única sombra, y solo para lo que flota sobre otra cosa
```

Antes convivían cuatro radios —174 `rounded-lg`, 58 `rounded-2xl`, 49
`rounded-xl` y los `rounded-md` sueltos— y cinco sombras, con cincuenta y dos
superficies llevando la de apoyo. No eran cuatro decisiones de diseño sino la
ausencia de una: cada pantalla heredó el radio de la maqueta de la que salió, y
dos tarjetas contiguas se veían distintas sin que nadie lo hubiera querido.

**En una superficie no hay sombra.** La separación la hacen el borde de un píxel
y el espacio, que además son los que siguen funcionando en modo oscuro —una
sombra sobre fondo casi negro no se ve—. La sombra se reserva para un
desplegable, un diálogo o un aviso, donde sí comunica algo: que eso se puede
cerrar y que lo de debajo sigue estando.

**Tipografía: Outfit en 400, 500 y 600.** El 700 y superiores no se usan, ni
siquiera en los titulares de la landing. Con menos pesos el conjunto se ve más
sobrio, y un titular en 800 al lado de una pantalla cuyo texto más fuerte es 600
se lee como otra marca.

**El icono solo cuando sustituye a una palabra que no cabe.** En el menú, texto.

**Los tres pesos también los verifica el CI**, junto al radio y la sombra. El
mismo script del §2 mira la hoja compilada: Tailwind
solo emite las utilidades que se usan, así que la ausencia de `.rounded-lg` en
el CSS servido no es una aproximación a «nadie la escribió», es la prueba. Para
que eso fuera cierto hubo que decirle a Tailwind que no mire
`pages/_maquetas/` —descubre el contenido recorriendo el proyecto entero—, o las
pantallas retiradas seguirían metiendo sus radios en la hoja.

---

## 6. Movimiento

Cuatro tokens, y nada fuera de ellos:

```
--ease-propia: cubic-bezier(0.22, 0.85, 0.3, 1)
--duracion-rapida: 150ms   menús, globos
--duracion-normal: 200ms   la mayoría
--duracion-lenta:  250ms   el menú lateral, las barras del gráfico
```

`prefers-reduced-motion` se respeta globalmente. Se excluyen las entradas de
avisos y diálogos, que aparecen de la nada y sin transición no se sabe de dónde
salieron.

Solo se anima la **entrada**. La salida se probó y se descartó: para animarla
hay que retener el nodo hasta que la animación acabe, y si llega otro aviso
antes quedan tarjetas huérfanas en el DOM, invisibles pero interceptando clics.

---

## 7. Botones

`app-boton` es el único sitio donde vive el aspecto de un botón. Diez pantallas
repetían la cadena de clases a mano y cada una había derivado su propia altura.

**Estados.** Bloquea el doble envío. No es una molestia visual: en un sistema
donde crear consume correlativos de comprobante, dos clics rápidos en «Crear»
son un documento duplicado ante SUNAT.

**El ancho no cambia entre estados.** La etiqueta no se sustituye por el icono:
se mantiene en el DOM, pierde la opacidad, y el icono se superpone. Sustituirla
encogería el botón y todo lo que tiene al lado daría un salto, justo en el
instante en que se está mirando ahí. Por eso el resultado se dice con icono y
no con texto —«Guardado», «No se guardó» obligaría a reservar el ancho del
texto más largo—. Lo que hay que leer va en el aviso flotante.

**Con `ruta` se renderiza como enlace.** No es cosmética: un enlace se abre con
la rueda del ratón, con Ctrl, en pestaña nueva, y enseña el destino en la barra
de estado. Un `<button>` que llama a `router.navigate` no hace ninguna de las
tres — y navegar es exactamente lo que hacen los botones de «crear».

**Un icono se pide por nombre** (`icono="mas"`), no como SVG suelto. El mismo
`+` dibujado en diez plantillas acaba con diez tamaños y diez grosores de
trazo.

---

## 8. Paneles flotantes

Todos —menús, desplegables, globos de ayuda— se colocan con
`posicionFlotante` (`shared/components/comunes/panel-flotante/`).

**`fixed`, no `absolute`.** Un panel `absolute` se recorta contra cualquier
ascendiente con `overflow` distinto de `visible`, y las tablas viven dentro de
un contenedor que se desplaza: un menú abierto en la última fila se cortaba por
el borde de la tarjeta. El precio es recalcular al desplazar y al redimensionar,
en fase de captura —el desplazamiento de los contenedores internos no burbujea
hasta `window`—.

**Se recorta contra `documentElement.clientWidth`, nunca contra
`window.innerWidth`.** `innerWidth` incluye el hueco de la barra de
desplazamiento, así que siempre sobrestima; y en el emulador de dispositivo de
Chrome llega a mentir de largo — medido en una pantalla de 440: decía **665**.
Recortando contra ese número el panel se coloca «dentro» de una ventana que no
existe y aparece cortado igual.

> El alto se mide con `clientHeight` por el mismo motivo. Hubo un intento de
> cambiar solo el ancho «por simetría» y dejar el alto en `innerHeight`: con
> `innerHeight` inflado, el panel **nunca** decide abrirse hacia arriba y un
> menú cerca del borde inferior se despliega fuera de la pantalla.

**Para recortar hay que medir el panel**, y eso solo se puede cuando ya está en
el DOM. Quien lo use debe medirlo desde el `ViewChild` y volver a llamar con
`anchoPanel`. Con un temporizador se llegaba unas veces antes del pintado y
otras después; el asignador del `ViewChild` se ejecuta justo cuando el elemento
existe.

---

## 9. Tablas

`tabla-datos` sirve a los diecisiete listados. Dos presentaciones del mismo dato:

**Desde 640 px, tabla.** Con un ancho mínimo de 640: seis columnas apretadas en
375 son ilegibles. Ese mínimo lo absorbe un contenedor con `overflow-x-auto`,
que se desplaza él y no la página.

**Por debajo, tarjetas.** Porque la consecuencia del mínimo era que en un móvil
toda lista aparecía dentro de una caja que se arrastra de lado, con las
primeras columnas fuera de vista: se leía «NOMBRE COMERCIAL» y había que
empujar para descubrir de qué registro se hablaba. En la tarjeta, la columna
marcada como `principal` es el título —no la primera del array, que suele ser
un código—, el estado va como insignia, y el resto como pares de etiqueta y
valor. Las etiquetas hacen falta justamente porque no hay cabecera.

Es la misma lista: mismo filtro, mismo orden, misma paginación, mismas
acciones.

**Otras reglas de la tabla:**

- **Una columna destacada como máximo.** Si todo destaca, nada destaca.
- **Rayado cebra**, sin líneas divisorias: separar con línea y con fondo es
  separarlo dos veces. La regla vive en `@layer components` porque tiene que
  perder contra `hover` y contra la fila seleccionada, que son utilidades.
- **El recuento va arriba**, no al pie: abajo obliga a recorrer la tabla entera
  para encontrar el dato con el que uno se hace la primera idea.
- **La barra de herramientas se pinta aunque la tabla esté vacía o cargando.**
  Si se fuera con las filas, quien busca algo que no existe se queda sin campo
  donde corregir lo que escribió.
- **Los importes a la derecha y con cifras de ancho fijo** (`tabular-nums`): es
  la única columna que se compara de un vistazo entre filas.
- **Más de dos acciones por fila van en un menú**, no sueltas.

---

## 10. Encabezado de vista

`app-encabezado-pagina`, en todas las vistas salvo el panel, que tiene el suyo
con saludo y fecha. Dos filas: **la ruta de
navegación arriba y arrimada a la derecha, el título debajo a la izquierda.**

**La descripción del módulo vive detrás de un botón de información.** Estaba
siempre visible, en un párrafo entre el título y la tabla. Explica qué es el
módulo, y eso se lee **una vez**: quien entra a Establecimientos por décima vez
ya sabe qué son y lo que quiere es la tabla. Cuatro renglones fijos la empujan
hacia abajo todos los días para decir algo que solo hizo falta el primero.

Es un botón y no un globo que salta al pasar el ratón: con el ratón encima se
abre sin querer al mover el cursor, y en una pantalla táctil no hay «pasar por
encima» — la explicación no existiría en un teléfono.

**Lo que no se esconde:** las advertencias con consecuencia. El plazo de 7 días
de la comunicación de baja no explica qué es el módulo, avisa de un plazo
legal, y esconderlo puede costar un comprobante que ya no se puede anular.

---

## 11. Marco de la aplicación

**La barra superior mide `--spacing-barra` (64 px)**, y la banda de la marca
del menú lateral usa el mismo token. Mientras fueron números independientes
—64 y 104— el logotipo quedaba cuarenta píxeles por debajo del resto del
encabezado, y el desajuste solo se nota mirando las dos columnas a la vez.

**El `sticky` va en el host del componente, no en el `<header>`.** Un elemento
pegajoso se desplaza dentro de su bloque contenedor, que es su padre; puesto en
el `<header>`, ese padre es el host, que mide exactamente lo mismo. Cero
holgura, y `sticky` se comporta igual que `static` — en silencio, sin que el
navegador avise de nada.

**Por debajo de 768 px** los controles —contexto de trabajo, tema y cuenta— no
caben junto al logotipo: piden unos 315 px y quedan menos de 200. Bajan a una
segunda fila que se muestra con un botón. Una fila y no un menú con todo
dentro: así cada control sigue siendo el que era, y la fila tiene el ancho
completo.

**El borde solo si se puede pulsar.** Un recuadro alrededor de un texto que no
responde al clic promete una acción que no existe. Por eso el selector de
contexto es texto plano cuando hay una sola empresa y un solo establecimiento,
y por eso los botones de icono de la barra no llevan borde: el borde se reserva
para lo que contiene un dato.

---

## 12. Foco de teclado

Definido una vez, en `@layer base`:

```css
:focus-visible { outline: 2px solid var(--color-brand-500); outline-offset: 2px }
.dark :focus-visible { outline-color: var(--color-brand-400) }
```

Antes no había ninguno: se dependía del contorno por defecto del navegador
—negro, `auto`, 0,8 px— que en nuestros fondos casi no se distingue y cambia
entre Chrome, Firefox y Safari.

`:focus-visible` y no `:focus`: el navegador solo lo marca cuando el foco llegó
de una forma que merece señal, y no al pulsar con el ratón.

**Va en la capa `base`, y eso es lo que lo hace funcionar.** Las utilidades de
Tailwind viven en una capa posterior, así que cualquier componente que ya
resuelva su foco lo apaga con `focus:outline-hidden` sin pelearse por
especificidad. Es lo que hacen los campos de formulario —que dibujan su anillo
también con el ratón, que es lo correcto para un campo— y los paneles
flotantes, que reciben el foco por programa y no deben pintar un aro alrededor
de todo el panel.

---

## 13. Respuestas y errores

`nucleo/errores.ts` interpreta lo que devuelve la API.

**Solo se muestra el `detail` del servidor si viene acompañado de `codigo`.**
Ese campo lo pone nuestro manejador de errores; API Gateway y CloudFront nunca
lo ponen. Sin esa comprobación, un error de infraestructura llegaría al usuario
con el texto que le apeteciera a AWS. Cuando falta, se usa un mensaje genérico
por estado.

**Los errores de campo se pintan en su campo.** `repartirFallo()` coloca cada
entrada de `campos` sobre su control, lo marca como tocado, y lo limpia en el
siguiente cambio de ese control. Solo lo que sobra va al aviso flotante.

**Los mensajes de validación son nuestros**, en
`ValidationMessages.properties`. Los de Hibernate llegaban al usuario diciendo
«el tamaño debe estar entre 0 y 200».

---

## 14. Cómo se verifica todo esto

**78 pruebas** en el frontend, más la comprobación de tono y forma del CI, y las
de interfaz siguen tres reglas aprendidas a base de escribir pruebas que no
medían nada:

1. **Una prueba que no falla al deshacer el cambio no prueba nada.** Cada regla
   de maquetación de aquí se comprobó quitando el cambio y viendo la prueba en
   rojo. Dos veces salvó de dar por buena una prueba inútil.

2. **Las consultas de medios miran la ventana, no el contenedor.** Para probar
   el diseño de teléfono no vale meter el componente en un `div` de 375 px:
   todas las variantes `sm:` siguen activas y se mide el diseño de escritorio
   encogido, que no existe en ninguna pantalla. Se monta dentro de un **iframe**
   estrecho, que tiene ventana propia. La prueba verifica además que dentro del
   iframe manda el móvil, para que no pase en verde midiendo lo que no es.

3. **Los estilos hay que meterlos en el iframe copiando el texto de las hojas**,
   no clonando el `<link>`: un `<link>` carga de forma asíncrona y la prueba
   mediría el DOM sin CSS — el resultado que pasa en verde sin significar nada.
   Se cuentan las reglas copiadas y se falla si son pocas.
