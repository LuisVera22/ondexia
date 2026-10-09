# Landing — notas de SEO

Lo que la maqueta no puede llevar y hay que escribir al portarla a Astro
(`apps/frontend/ondexia.landing`). Fecha: 2026-08-23.

## 1. El principio: encabezado de dos pisos

El criollismo y el SEO no compiten porque **no ocupan el mismo renglón**. Cada
encabezado lleva dos partes dentro del mismo `<h1>`/`<h2>`:

```html
<h2>
  <span class="rotulo">Precios del software de facturación electrónica</span>
  <span class="cartel">Cuentas claras.</span>
</h2>
```

El buscador lee el encabezado completo; la persona ve el cartel. Nadie pierde.

**Lo que NO hay que hacer:** poner el término en un `<p>` aparte «porque igual
está en la página». Un `<p>` de 12 px pesa una fracción de lo que pesa el
encabezado que lo acompaña.

## 2. Mapa de encabezados de la portada

| Nivel | Rótulo (término buscado) | Cartel (lo que se ve grande) |
|---|---|---|
| `h1` | Facturación electrónica ante SUNAT · Perú | Papelito manda. |
| `h2` | Almacén, compras y ventas en un solo sistema | Toda la chamba, en un solo sitio. |
| `h2` | Nota de crédito y comunicación de baja electrónicas | No te paltees. |
| `h2` | Precios del software de facturación electrónica | Cuentas claras. |
| `h2` | Estado del producto y hoja de ruta | Vamos por partes. |
| `h2` | Prueba gratis de 30 días contra la beta de SUNAT | Entra a la lista corta. |

Un solo `h1` por página. Los `h3` son los nombres de módulo y de plan, que ya
llevan término propio.

### Hueco abierto: los tipos de comprobante

Hubo una cinta bajo la portada con los ocho documentos y su código. Se retiró el
2026-08-23 por decisión de diseño —no convencía visualmente—, y con ella se
fueron seis términos que no aparecen en ningún otro sitio de la página:

| Término | ¿Sigue en la página? |
|---|---|
| Nota de crédito | Sí, en «No te paltees» |
| Comunicación de baja | Sí, en «No te paltees» |
| Guía de remisión | Sí, en Precios y en el bento |
| **Factura electrónica** | **No** |
| **Boleta de venta electrónica** | **No** |
| **Resumen diario** | **No** |
| **Liquidación de compra** | Solo dentro de la tarjeta de Compras |
| **Nota de débito** | **No** |

«Factura» y «boleta» son los dos términos más buscados del rubro, y hoy la
página los dice únicamente en el `<title>`, la meta descripción y el
`featureList` del dato estructurado. Eso es poco.

**No lo resuelvas volviendo a poner una franja.** Lo natural es que la tarjeta de
Ventas del bento los nombre —ahí encajan sin forzar nada, porque son justamente
lo que ese módulo emite— en lugar de las tres etiquetas genéricas que lleva
ahora («Cotizaciones», «Preventa», «Formas de pago»).

## 3. Etiquetas de cabecera

```html
<html lang="es-PE">
<title>Ondexia · Facturación electrónica con almacén para empresas peruanas</title>
<meta name="description" content="Emite facturas y boletas ante SUNAT y controla tu inventario en el mismo sistema. Almacén, compras y ventas. Prueba de 30 días sin tarjeta.">
<link rel="canonical" href="https://ondexia.com/">
```

- El `<title>` es lo que decide el clic desde el buscador: marca primero, término
  después, bajo 60 caracteres visibles.
- La descripción no posiciona, pero sí convierte. Que termine en la oferta.
- Open Graph y `twitter:card` con una imagen del cartel: es el activo que mejor
  se comparte por WhatsApp, que en Perú es el canal real.

## 4. Datos estructurados

Dos esquemas que rinden de inmediato:

- **`SoftwareApplication`** con `applicationCategory: BusinessApplication`,
  `operatingSystem: Web` y un `Offer` por plan (89 / 199 / 390 `PEN`). Es lo que
  permite que el buscador muestre el precio junto al resultado.
- **`FAQPage`** si se agrega la sección de preguntas (ver §6).

`Organization` con el RUC y la dirección cuando existan los datos del pie.

## 5. Lo técnico que ya está decidido en otros documentos

- `www` → 301 al apex, y la landing servida desde S3 + CloudFront
  (doc 01 §2 y §8). Estático y cacheado: los Core Web Vitals salen bien sin
  esfuerzo extra.
- **Outfit y Bricolage autoalojadas**, no desde Google Fonts. La maqueta usa el
  CDN porque el lienzo de diseño no admite otra cosa; en producción va
  `@font-face` local con `font-display: swap`, como ya hace `styles.css`.
- `X-Robots-Tag: noindex, nofollow` en `ver.ondexia.com` (doc 01 §5.1) y
  `noindex` + Basic Auth en todo lo que no sea producción (doc 01 §3).

## 6. Dónde se gana de verdad

Términos como «facturación electrónica Perú» no los gana una portada: los gana
quien responde las preguntas concretas. Ese material **ya está escrito** en
`ondexia.docs` y hoy no lo lee nadie fuera del repo:

| Pregunta que la gente busca | De dónde sale |
|---|---|
| ¿Cuál es el plazo para anular una factura electrónica? | doc 02 §2, riesgo V-1 |
| ¿Cuándo va nota de crédito y cuándo comunicación de baja? | doc 02 §2 |
| ¿Cómo funciona el resumen diario de boletas? | doc 02 §3 |
| ¿Qué código de establecimiento va en la guía de remisión? | doc 04 §1 |
| ¿La guía de remisión usa el mismo canal que la factura? | doc 02 §3 |
| ¿Cuántos años hay que conservar el XML y el CDR? | doc 04 §2.2 |

Publicarlas en `docs.ondexia.com` con enlace hacia la landing hace dos cosas a la
vez: trae búsquedas de intención alta y demuestra que sabes del tema. Es más
barato que competir por el término genérico.

**Pendiente:** validar volúmenes reales con una herramienta con datos de Perú.
Las prioridades de arriba son por lógica de negocio, no por cifras medidas.
