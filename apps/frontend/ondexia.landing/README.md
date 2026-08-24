# ondexia.landing

La página pública de `ondexia.com`. Astro, estática, sin JavaScript de cliente.

Es el único desplegable que **no habla con nadie**: ni API, ni base de datos, ni
Cognito. Esa frontera está en [doc 03 §3](../../../ondexia.docs/03-estructura-repositorio.md)
y conviene no cruzarla — el día que la landing necesite el backend deja de poder
desplegarse sola, que es justo lo que la hace barata y rápida.

## Arrancar

```bash
pnpm install && pnpm dev
```

Queda en `http://localhost:4321`. Para ver lo que se va a publicar de verdad:

```bash
pnpm build && pnpm preview
```

`pnpm build` corre `astro check` antes de compilar, así que valida los tipos de
las plantillas. Es lo mismo que ejecuta el CI.

## Cómo está armada

```
src/
├── layouts/Base.astro       <head> entero: título, meta, Open Graph, JSON-LD
├── pages/index.astro        La única página. Ensambla las secciones
├── components/
│   ├── Barra.astro          Barra superior. El menú de móvil es un <details>
│   ├── Cartel.astro         La portada
│   ├── VistaProducto.astro  La maqueta de la app que asoma bajo la portada
│   ├── Modulos.astro        El bento de Ventas, Almacén y Compras
│   ├── Anular.astro         «No te paltees» — nota de crédito y baja
│   ├── Precios.astro        Los tres planes
│   ├── Estado.astro         Los cortes C1 a C5
│   ├── Cierre.astro         Llamada a la acción
│   ├── Pie.astro            Pie
│   └── Logo.astro           El logotipo en línea, claro y oscuro
└── styles/global.css        Tokens y las cuatro piezas con estado
```

### Los tokens son los mismos que los de la app

`global.css` repite los nombres de `ondexia.web/src/styles.css` a propósito:
`--color-brand-500` vale lo mismo en los dos sitios. Hoy son dos archivos porque
los frontends no comparten paquete —la decisión sigue abierta en el doc 03 §3—,
así que **un cambio de marca hay que hacerlo en los dos**.

Lo exclusivo de aquí son tres: `--font-cartel`, `--color-fluor` y
`--color-fucsia`. No van en la app porque en una pantalla de trabajo no pintan
nada.

### De dónde sale el diseño

Del lenguaje del cartel chicha: una palabra gigante, tinta plana, cero párrafos.
Se roba la jerarquía, no el disfraz. Los bocetos y las notas están en
[`ondexia.docs/landing-propuesta/`](../../../ondexia.docs/landing-propuesta/).

Tres reglas que conviene no romper al editar:

- **El fluorescente va poco y siempre sobre fondo oscuro.** Sobre blanco no
  llega al mínimo legible.
- **El fucsia, una vez por pantalla.** Es el banderín rotado. A la tercera deja
  de señalar y se vuelve ruido.
- **Nada de degradados de fondo.** La serigrafía imprime tintas planas, y un
  degradado delata la imitación. Para dar textura está `.trama`.

### El movimiento

Todo es CSS. No hay observadores, ni escuchas de `scroll`, ni una línea de
script — la promesa de cero JavaScript sigue en pie.

**Al pasar por encima y al pulsar.** El lenguaje es la sombra dura, sin
difuminar: el registro desplazado de la serigrafía, que es el mismo accidente
que en el cómic acabó siendo lenguaje. Las tarjetas suben y su sombra crece
(`.pop`); los botones bajan hasta meterse dentro de su propia sombra al pulsar.
La curva es `--ease-resorte`, que pasa de largo y vuelve.

Tres reglas que sostienen esto y no son negociables:

- **`@media (hover: hover)` en todo efecto de puntero.** Sin eso, una pantalla
  táctil deja el `:hover` pegado después de tocar y la tarjeta se queda
  levantada hasta que toques otra cosa. Parece un fallo, y lo es.
- **`:focus-visible` recibe lo mismo que `:hover`.** Un efecto solo para ratón
  le dice a quien tabula que ahí no hay nada.
- **La curva de rebote solo responde a gestos.** En una aparición por
  desplazamiento marea, porque nadie pidió que eso se moviera.

**Al desplazar.** `animation-timeline: view()`: el progreso lo marca la posición
del elemento en la ventana, no un reloj. El escalonado del bento va por
posición y no por tiempo —con una línea de tiempo de desplazamiento
`animation-delay` no significa nada—, así que cada ficha lleva un
`--retardo: N` que corre su rango un poco más abajo.

> **Lo importante: nada se esconde con CSS estático.** `.revelar` no tiene
> `opacity: 0` de base; la opacidad cero vive únicamente dentro del fotograma
> inicial de la animación. Si el navegador no soporta líneas de tiempo de
> desplazamiento, o si la línea queda inactiva por cualquier motivo, el
> resultado es **contenido visible sin animación** — nunca una página en
> blanco. Esa es la razón del `@supports`, y la razón de no invertir la lógica
> por comodidad.
>
> `prefers-reduced-motion` se comprueba aparte y explícitamente. No basta con la
> regla global que acorta duraciones: con una línea de tiempo de desplazamiento
> la duración se ignora, así que aquella regla no haría nada.

### El SEO no es un añadido

Cada encabezado lleva dos pisos dentro del mismo `<h1>`/`<h2>`: el rótulo
pequeño con el término que se busca, y el cartel que se ve. El buscador lee los
dos. Si separas el rótulo a un `<p>` aparte, deja de pesar.

El plan completo —title, meta, datos estructurados y qué contenido rinde más—
está en [`ondexia.docs/landing-propuesta/SEO.md`](../../../ondexia.docs/landing-propuesta/SEO.md).

**Los precios están en dos sitios**: las tarjetas de `Precios.astro` y el
`SoftwareApplication` de `Base.astro`. Cambiar uno y no el otro hace que el
buscador muestre una cifra distinta a la de la página, que es peor que no
mostrar ninguna.

## Desplegar

No se despliega desde aquí. Lo hace `.github/workflows/deploy.yml`, que compila,
sincroniza `dist/` contra el bucket `landing` e invalida la distribución de
CloudFront. La infraestructura ya existe en `ondexia.infra/estatico.tf`
(`sitio["landing"]`, con `es_spa = false`: aquí un 404 es un 404).

El HTML se publica sin caché y todo lo de `_astro/` y `fonts/` con un año, porque
lleva hash en el nombre.

El workflow trae la casilla **«Solo la landing»**, que acota el plan de Terraform
al sitio estático y se salta el backend, las migraciones, la SPA y el panel. Es
la forma de tener esta página en línea sin levantar RDS, que es el 90 % de la
factura. El orden exacto del primer despliegue —incluido el paso que hay que
hacer desde tu equipo porque el rol de GitHub lo crea Terraform— está en
[`ondexia.infra/README.md`](../../../ondexia.infra/README.md), sección «Solo la
landing».

Con `gestionar_dns = false` no hace falta el dominio: CloudFront sirve por el
suyo, y sirve para comprobar la cadena entera antes de gastar en `ondexia.com`.

> **Si algún día hay más de una página**, hay que añadir una función de
> CloudFront que reescriba `/precios` a `/precios/index.html`. Con OAC, S3 no
> resuelve el índice de directorio solo. Hoy no hace falta: solo existe `/`, y de
> eso se encarga `default_root_object`.

## Pendientes

| Qué | Por qué importa | Dónde |
|---|---|---|
| **Datos del titular** | `[RAZÓN SOCIAL]`, `[TU RUC]` y `[DIRECCIÓN FISCAL]` están literales en el pie. No se puede publicar así | `src/components/Pie.astro` |
| **Bricolage Grotesque** | Falta el `.woff2`. Mientras tanto los titulares caen en Outfit 800: se ve digno, pero pierde el carácter del cartel | `src/styles/global.css`, bloque comentado |
| **Imagen social** | `og:image` apunta a `/og-ondexia.png`, que no existe. Es lo único que se ve al compartir por WhatsApp | `public/og-ondexia.png`, 1200×630 |
| **Destino del formulario** | Sin `PUBLIC_ENDPOINT_LISTA` se muestra el enlace de correo en vez del formulario, a propósito: un formulario que no envía a ninguna parte pierde correos en silencio | `.env`, ver `.env.example` |
| **Enlaces del pie** | `ver`, `docs` y `status` salen como «pronto» porque esos subdominios aún no existen (doc 01 §2) | `src/components/Pie.astro` |
| **Validar los precios** | Las cifras son la hipótesis del doc 04 §2.3, no un estudio. El método para validarlas está en §2.5 | `src/components/Precios.astro` |
| **Cambiar la llamada a la acción** | Dice «Quiero mi acceso» porque C1 sigue en obra. Cuando salga a producción pasa a «Empieza tu prueba de 30 días», y la sección de estado se retira | Toda la página |

## Lo que no lleva y es deliberado

- **Ni un byte de JavaScript.** El menú de móvil es un `<details>`; el navegador
  ya sabe abrirlo, lo anuncia como plegable y funciona aunque falle todo lo
  demás.
- **Ninguna fuente desde un CDN.** Outfit se sirve desde `public/fonts/`. Un
  tercero en la ruta crítica de la portada es latencia que no controlamos, y
  además cuenta en la métrica con la que Google posiciona.
- **Sin analítica de terceros.** Cuando haga falta medir, conviene decidirlo a
  propósito y no heredarlo de una plantilla.
- **Sin testimonios ni logos de clientes.** No hay clientes todavía, y
  fabricarlos en un producto tributario es la peor primera impresión posible.
