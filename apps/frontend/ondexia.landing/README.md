# ondexia.landing

Página pública de Ondexia: Astro estático con JavaScript local mínimo para
recorrido por escenas, ampliación de capturas, entradas y cierre del menú móvil.
El código ejecutable se sirve en archivos externos del mismo sitio; no utiliza API,
backend, autenticación, analítica ni servicios de terceros. La frontera se
conserva conforme a doc 03 §3.

## Desarrollo y revisión

```bash
pnpm install --frozen-lockfile
pnpm dev
pnpm build
pnpm preview
```

El desarrollo utiliza `http://localhost:4321`. El build ejecuta Astro check,
compilación estática y controles de sitemap y direcciones. Desde la raíz del
repositorio, con web y landing compiladas:

```bash
node herramientas/comprobar-tono-y-forma.mjs
```

## Composición

| Archivo | Responsabilidad |
|---|---|
| `layouts/Base.astro` | Metadatos, canonical, Open Graph y JSON-LD |
| `pages/index.astro` | Ensamblaje de secciones |
| `components/Barra.astro` | Navegación y separación de ingreso/solicitud comercial |
| `components/Cartel.astro` | Portada y propuesta del producto |
| `components/Explorador.astro` | Tres artículos del frontend real, capítulos, variantes móviles y ampliación |
| `components/Modulos.astro` | Ventas, caja, catálogo y existencias |
| `components/Proceso.astro` | Preparación, cobro y consulta de una venta |
| `components/Precios.astro` | Propuesta comercial pendiente de validación |
| `components/Estado.astro` | Alcance actual, ensayo tributario y evolución |
| `components/Preguntas.astro` | Preguntas mediante details nativo |
| `components/Cierre.astro` | Contacto con destino configurado o alternativa de correo |
| `components/Pie.astro` | Identidad, navegación y pendientes legales |
| `datos/planes.ts` | Cifras compartidas con sección y datos estructurados |
| `scripts/explorador.ts` | Progreso del recorrido, avance manual, movimiento acotado y diálogo |
| `scripts/movimiento.ts` | Entradas con movimiento reducido y cierre del menú móvil |
| `styles/global.css` | Tokens de marca, foco, botones y composición compartida |
| `public/capturas/` | Capturas locales de venta, registro y documento con datos sintéticos |

## Diseño vigente de la propuesta

La [versión interactiva del 2026-10-14](../../../ondexia.docs/landing-propuesta/2026-10-14-interactividad.md)
complementa el [rediseño del 2026-10-12](../../../ondexia.docs/landing-propuesta/2026-10-12-rediseno.md).
Conserva índigo, grises azulados, Outfit
400/500/600, radio 6 px y fuentes locales. La separación se consigue con espacio
y bordes; no hay sombras en superficies. La portada presenta una tarea y una
demostración central. La escritura sigue tarea → demostración → utilidad → acción,
conforme al complemento del doc 10 §16.

Las tres escenas muestran el frontend Angular real: preparar la venta, revisar
su registro y previsualizar la nota de venta. Los datos son sintéticos y están
identificados junto a las imágenes como «Capturas del sistema · datos de
demostración». No son capturas de clientes ni acreditan una emisión ante SUNAT.
No se incorporan testimonios, certificaciones ni cifras de clientes sin evidencia.

La evolución vigente presenta un recorrido al bajar por los tres artículos, que
permanecen visibles y en orden. La navegación por capítulos indica el alcanzado
con `aria-current="step"` y «Escena n de 3». Desplazar la página no cambia el foco
ni anuncia cada capítulo; los enlaces y Anterior/Siguiente permiten avanzar
manualmente y colocar el foco en la escena con su título visible. No hay avance
por temporizador ni bloqueo del desplazamiento nativo.

El marco de la captura responde al desplazamiento con un parallax acotado a
±12 píxeles. En escritorio la figura puede fijarse dentro del artículo solo si
cabe bajo la cabecera y la navegación; en móvil y ventanas bajas las figuras
permanecen en el flujo normal. Movimiento reducido elimina el parallax y la
fijación de las figuras, también si la preferencia cambia con la página abierta.
Esta excepción de movimiento continuo se limita a la demostración pública,
conforme al doc 10 §16; no cambia las convenciones del producto de trabajo.

Diálogo y menú conservan sus controles de teclado y foco. Sin JavaScript, las
tres escenas y enlaces ancla siguen disponibles, el progreso dice «3 escenas» y
los botones de avance y ampliación permanecen ocultos. Los originales tienen
enlaces, y menú y preguntas conservan HTML nativo. La
[evidencia del recorrido](../../../ondexia.docs/evidencias/2026-10-14-recorrido-landing.md)
registra las comprobaciones ejecutadas y sus límites; no demuestra conformidad
WCAG completa ni conversión. La
[evidencia de la primera versión interactiva](../../../ondexia.docs/evidencias/2026-10-14-landing-interactiva.md)
se conserva como historia de la candidata anterior.

## Procedencia de las capturas y reproducción local

Los archivos `venta`, `registro` y `documento`, cada uno con sufijos
`-escritorio.png` y `-movil.png`, proceden del build de `ondexia.web`. Se capturan
los componentes de punto de venta y detalle del documento con respuestas
sintéticas del navegador. El recorrido utiliza una nota de venta de demostración;
no consulta datos de clientes reales, inicia una sesión real ni ejecuta el backend.

Las variantes móviles se toman renderizando la interfaz a 390 × 844 píxeles CSS;
las de escritorio, a 1440 × 1000. No son imágenes de escritorio encogidas. Son
capturas en Chromium local, sin implicar comprobación en dispositivos físicos.

Las herramientas pertenecen al harness existente. Playwright para Python y
Chromium son dependencias opcionales de esta revisión local, no del sitio servido
ni del build Astro. Deben prepararse en la máquina que ejecuta la revisión; no se
asumen disponibles por instalar las dependencias pnpm. La herramienta de captura
busca Chromium del sistema y admite el instalado por Playwright como alternativa;
el comprobador de interactividad utiliza `/usr/bin/chromium`.

Desde la raíz del repositorio, compilar el frontend con su script oficial:

```bash
pnpm --dir apps/frontend/ondexia.web build
```

Servir `apps/frontend/ondexia.web/dist/ondexia-web/browser` solo en localhost,
con retorno a `index.html` para las rutas de la aplicación, por ejemplo en 8030.
El capturador no compila ni inicia ese servidor. Generar los artefactos fuera del
repositorio:

```bash
python3 herramientas/capturar-producto-landing.py \
  --origen http://127.0.0.1:8030 \
  --destino /tmp/ondexia-capturas \
  --compilado apps/frontend/ondexia.web/dist/ondexia-web/browser
```

`--compilado` es opcional y acredita hashes del build utilizado. Revisar capturas,
procedencia, fixtures y control del recorrido antes de sustituir las imágenes de
`public/capturas/`; la herramienta rechaza un destino dentro del repositorio.

Después de revisar los assets, ejecutar el build oficial de la landing y los
controles de tono y forma con ambos frontends compilados:

```bash
pnpm --dir apps/frontend/ondexia.landing build
node herramientas/comprobar-tono-y-forma.mjs
```

Para comprobar la política, iniciar el servidor local en una terminal:

```bash
python3 herramientas/servir-landing-local.py
```

Sirve `apps/frontend/ondexia.landing/dist` en `127.0.0.1:4324` y lee al arrancar
la cabecera `Content-Security-Policy` de `csp_landing` en
`ondexia.infra/estatico.tf`. También puede usarse un proxy local que sirva las
mismas cabeceras. `pnpm preview` permite revisar la página, pero no acredita esa
política. Con el servidor activo, ejecutar en otra terminal:

```bash
python3 herramientas/comprobar-interactividad-landing.py \
  --url http://127.0.0.1:4324/ \
  --evidencia /tmp/ondexia-landing-recorrido
```

El comprobador verifica artículos visibles, capítulos y avance manual coherentes,
foco y títulos visibles, parallax dentro de ±12 píxeles y su desactivación con
movimiento reducido, ampliación, Escape y retorno del foco, reapertura, variantes
móviles, altura reducida, navegación sin JavaScript y rechazo de scripts inline
y conexiones. Para reproducir el bloqueo anterior, iniciar el mismo build en
otra terminal con la política conservada en la evidencia, sin `script-src 'self'`:

```bash
python3 herramientas/servir-landing-local.py \
  --puerto 4325 \
  --politica-csp ondexia.docs/evidencias/2026-10-14-landing-interactiva/csp-anterior.txt
```

Añadir `--politica-anterior http://127.0.0.1:4325/` al comprobador. Opcionalmente,
servir el build de la candidata anterior `4350294`, conservado fuera del
repositorio, en otro origen local con su política que permite scripts locales y
añadir `--recorrido-anterior http://127.0.0.1:4327/` para contrastar la evolución.
La preparación y el resultado de esa comparación se registran en la evidencia
del recorrido; no se supone que ese build esté disponible en otra máquina.
Las herramientas admiten únicamente orígenes locales. Ejecutarlas no verifica
AWS, autenticación, backend ni SUNAT.

## Configuración y decisiones pendientes

`.env.example` documenta las variables públicas del build. No deben contener
secretos. Sin `PUBLIC_ENDPOINT_LISTA` se utiliza el enlace de correo existente.
Configurar un endpoint requiere revisar la CSP: `form-action 'none'` impediría
el envío. Una variable configurada no demuestra funcionamiento del canal.

Los datos del titular proceden de variables públicas; si faltan, no se inventan.
Política de privacidad, términos y Libro de Reclamaciones siguen pendientes de
contenido real. Tampoco se supone que `hola@ondexia.com` esté habilitado solo por
haber configurado el dominio: su recepción necesita verificarse por el operador.

El propietario debe decidir CTA final y publicación de precios. Los importes
previos S/ 89, S/ 199 y desde S/ 390 se conservan en la propuesta; doc 04 §2.3
los considera hipótesis. No se aprueban nuevas condiciones comerciales por diseño.
La emisión electrónica continúa «en ensayo» hasta obtener evidencia tributaria real.

`public/og-ondexia.png` contiene la imagen social de 1200 × 630. Los metadatos
y el sitemap derivan de `site` en astro.config.mjs.

## Publicación

La publicación sigue el workflow del repositorio y el procedimiento de
infraestructura. La política propuesta conserva `default-src 'none'` y añade
`script-src 'self'` para los archivos locales generados por Astro; no habilita
scripts ejecutables inline, eval, conexiones ni marcos. Su aplicación efectiva
queda para la próxima publicación revisada.

Este trabajo local no ejecuta CD, apply, sincronización S3 ni invalidación de
CloudFront. AWS no se ha modificado ni verificado en esta revisión. Revisar las
decisiones comerciales, el canal de contacto y los pendientes legales antes de
publicar; la interactividad no los resuelve.
