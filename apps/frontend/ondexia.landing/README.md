## Recorrido continuo — revisión del 15 de octubre de 2026

La portada y la demostración forman una sola secuencia: **Vender / Consultar /
Imprimir** seleccionan capturas y objetos ilustrativos. El scroll retira los
objetos, mueve la captura a la derecha y presenta el acordeón con tres barras.
La sección se libera al completar el último capítulo. Después aparecen las
herramientas, el acceso a planes, privacidad/seguridad, alcance, preguntas y cierre.

- Capturas completas existentes de computador, con datos sintéticos y origen
  conservado en la evidencia anterior; no son datos de clientes ni nuevas pruebas de backend.
- Escritorio mejorado: ancho >= 1024 px, alto >= 700 px, movimiento permitido.
  Sin esas condiciones, sin scripts o al detener: tres artículos en flujo normal.
- Teclado, hover y clic; sin timers de reproducción ni secuestro de scroll.
- Carrusel nativo con botones y desplazamiento táctil; originales disponibles.
- `/planes.html` conserva importes, aviso sin IGV y propuesta pendiente de validación.
  Se genera como archivo para S3 sin cambios en CloudFront; SEO comprueba ambas rutas.
- Contacto de privacidad/seguridad por correo; la política sigue pendiente. No se
  inventan certificaciones, garantías, prueba gratuita ni condiciones comerciales.
- `Solicitar acceso` mantiene su destino vigente; `Ingresar` abre la aplicación.

Ver [especificación del recorrido](../../../ondexia.docs/landing-propuesta/2026-10-14-interactividad.md#revisión-del-recorrido-continuo--2026-10-15)
y [evidencia](../../../ondexia.docs/evidencias/2026-10-15-presentacion-continua.md).

Validar con el build del paquete y el servidor local con CSP:

```sh
pnpm --dir apps/frontend/ondexia.landing build
python3 herramientas/servir-landing-local.py --puerto 4330
python3 herramientas/comprobar-interactividad-landing.py --url http://127.0.0.1:4330/ --evidencia /tmp/ondexia-presentacion
URL_LANDING=http://127.0.0.1:4330/ python3 herramientas/comprobar-responsividad-landing.py
```

El oráculo del recorrido anterior se conserva mediante `--escenario-historico`,
para reproducir aquella versión, y no corresponde a la nueva composición.
Las pruebas de navegador requieren Playwright Python y Chromium locales.

---

## Referencia histórica de la versión anterior

# ondexia.landing

Página pública de Ondexia: Astro estático con JavaScript local mínimo para
recorrido por escenas, ampliación de capturas, entradas y cierre del menú móvil.
El código ejecutable se sirve en archivos externos del mismo sitio; no utiliza API,
backend, autenticación, analítica ni servicios de terceros. La frontera se
conserva conforme a doc 03 §3.

La revisión del **2026-10-15** incorpora una ventana central y capturas completas
de computador también en móvil. La [evidencia local](../../../ondexia.docs/evidencias/2026-10-15-escenario-landing.md)
registra los casos ejecutados y sus límites; las candidatas anteriores son historia.

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
| `components/Explorador.astro` | Demostración, capítulos, alternativa estática y ampliación |
| `components/Modulos.astro` | Ventas, caja, catálogo y existencias |
| `components/Proceso.astro` | Preparación, cobro y consulta de una venta |
| `components/Precios.astro` | Propuesta comercial pendiente de validación |
| `components/Estado.astro` | Alcance actual, ensayo tributario y evolución |
| `components/Preguntas.astro` | Preguntas mediante details nativo |
| `components/Cierre.astro` | Contacto con destino configurado o alternativa de correo |
| `components/Pie.astro` | Identidad, navegación y pendientes legales |
| `datos/planes.ts` | Cifras compartidas con sección y datos estructurados |
| `scripts/explorador.ts` | Progreso, fundidos y contorno SVG; alternativa estática y diálogo |
| `scripts/movimiento.ts` | Entradas con movimiento reducido y cierre del menú móvil |
| `styles/global.css` | Tokens de marca, foco, botones y composición compartida |
| `public/capturas/` | Capturas locales de venta, registro y documento con datos sintéticos |

## Diseño vigente de la propuesta

La [revisión del 2026-10-15](../../../ondexia.docs/landing-propuesta/2026-10-14-interactividad.md#revisión-vigente--2026-10-15--ventana-central-y-capturas-de-computador)
complementa el [rediseño del 2026-10-12](../../../ondexia.docs/landing-propuesta/2026-10-12-rediseno.md)
y sustituye la presentación anterior de tres figuras con parallax de ±12 píxeles.
Conserva índigo, grises azulados, Outfit
400/500/600, radio 6 px y fuentes locales. La separación se consigue con espacio
y bordes; no hay sombras en superficies. La portada presenta una tarea y una
demostración central. La escritura sigue tarea → demostración → utilidad → acción,
conforme al complemento del doc 10 §16.

Las escenas muestran el frontend Angular real: preparar la venta,
revisar su registro y previsualizar la nota de venta. Identificar cerca de ellas
«Capturas del sistema · datos de demostración». No son capturas de clientes ni
acreditan una emisión ante SUNAT. No incorporar testimonios, certificaciones ni
cifras de clientes sin evidencia.

El contrato nuevo presenta una ventana central grande con tres capturas completas
de computador, ajustadas proporcionalmente incluso en móvil. Sus fundidos siguen
el desplazamiento continuo en ambos sentidos; tres enlaces de capítulos indican
el actual con `aria-current="step"`. Desplazar no mueve el foco ni anuncia cada
fotograma. Los controles compartidos permanecen estables y no desaparecen al
cambiar de escena.

La propuesta usa animaciones WAAPI pausadas: `currentTime` sigue el progreso, sin
temporizadores ni bloqueo de rueda, teclas o gestos. El marco o fondo SVG puede
cambiar de forma con comandos compatibles; los píxeles de la captura no se
deforman. Esta excepción se limita al marketing público conforme al doc 10 §§6/16,
no a la aplicación de trabajo. La investigación y los offsets están en la
especificación; no implican rendimiento ni compatibilidad medidos.

La mejora se habilita solo con altura de al menos 700 píxeles CSS y espacio
efectivo para ventana, explicación y controles. «Ver sin animación», movimiento
reducido, JavaScript ausente o bloqueado, ventana baja o falta de espacio deben
dejar los tres artículos en flujo normal, sin fijación ni animación de la
demostración. La alternativa mantiene anclas, originales y «3 escenas»; menú y
preguntas usan HTML nativo. Debe atender cambios de preferencia y tamaño con la
página abierta.

La ampliación debe abrir ajustada y ofrecer «Tamaño real» a 1440 píxeles CSS con
paneo dentro del diálogo. Reabrir desde el ajuste y origen cero, conservar Escape,
Cerrar y retorno del foco, y no introducir desplazamiento horizontal de toda la
página. La evidencia del 2026-10-15 registra su comprobación en Chromium local. La
[evidencia del recorrido anterior](../../../ondexia.docs/evidencias/2026-10-14-recorrido-landing.md)
y la [primera versión interactiva](../../../ondexia.docs/evidencias/2026-10-14-landing-interactiva.md)
se conservan como historia; no verifican la propuesta del 2026-10-15 ni demuestran
conformidad WCAG completa o conversión.

## Procedencia de las capturas y reproducción local

La revisión usa `venta-completa.png`, `registro-completo.png` y
`documento-completo.png`: capturas completas del frontend de computador a
1440 × 1000, obtenidas del build de `ondexia.web` con fixtures sintéticos. Los
mismos PNG deben utilizarse en todos los anchos; la variante móvil anterior queda
sustituida para esta demostración. Conservar procedencia, hashes y control del
mismo recorrido de la nota de venta. La captura no debe consultar clientes reales,
iniciar una sesión real ni ejecutar el backend.

El ajuste proporcional en móvil muestra el encuadre completo de computador; la
ampliación permite leerlo a tamaño real. La evidencia conserva los hashes del
frontend y de los tres PNG copiados sin edición. Capturar con Chromium local no
implica comprobar teléfonos físicos.

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

`--compilado` es opcional y acredita hashes del build utilizado. El comando
entrega, entre sus salidas, `venta-1440.png`, `registro-1440.png` y
`comprobante-logo-1440.png`: los tres PNG completos de 1440 × 1000. Revisar
capturas, procedencia, fixtures y control del recorrido antes de sustituir las
imágenes de `public/capturas/`; la herramienta rechaza un destino en el repositorio.

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
  --evidencia /tmp/ondexia-landing-ventana-central
```

El comprobador verifica: mismos PNG completos
a cualquier ancho; estados 0/25/50/75/100 % y reversa; animaciones pausadas estables;
morphing intermedio real si se conserva; representación accesible única y foco
estable; alternativas estáticas; modal ajustado/tamaño real; CSP y ausencia de
recursos externos. Registrar un perfil y tareas largas antes de afirmar fluidez;
no se declara «60 fps». El informe de esta revisión distingue los resultados
locales de CI, producción y navegadores aún no comprobados.

Para reproducir el bloqueo anterior, iniciar el mismo build en otra terminal con
la política conservada en la evidencia, sin `script-src 'self'`:

```bash
python3 herramientas/servir-landing-local.py \
  --puerto 4325 \
  --politica-csp ondexia.docs/evidencias/2026-10-14-landing-interactiva/csp-anterior.txt
```

Añadir `--politica-anterior http://127.0.0.1:4325/` al comprobador. Para el contraste
de evolución, conservar fuera del repositorio el build del recorrido anterior
y servirlo en 4327 con la política que permite scripts locales. La opción
`--recorrido-anterior http://127.0.0.1:4327/` contrasta el build anterior con los
mismos oráculos de capturas de computador y progreso, que deben fallar allí y
pasar en la candidata nueva. Registrar
preparación, resultado y límites; no suponer que ese build está disponible en
otra máquina.
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
