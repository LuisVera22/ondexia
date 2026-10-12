# ondexia.landing

Página pública de Ondexia: Astro estático, sin JavaScript de cliente ni acceso a
la API, la base de datos o Cognito. La frontera se conserva conforme a doc 03 §3.

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
| `components/VistaProducto.astro` | Ilustración identificada con datos de ejemplo |
| `components/Modulos.astro` | Ventas, caja, catálogo y existencias |
| `components/Proceso.astro` | Preparación, cobro y consulta de una venta |
| `components/Precios.astro` | Propuesta comercial pendiente de validación |
| `components/Estado.astro` | Alcance actual, ensayo tributario y evolución |
| `components/Preguntas.astro` | Preguntas mediante details nativo |
| `components/Cierre.astro` | Contacto con destino configurado o alternativa de correo |
| `components/Pie.astro` | Identidad, navegación y pendientes legales |
| `datos/planes.ts` | Cifras compartidas con sección y datos estructurados |
| `styles/global.css` | Tokens de marca, foco, botones y composición compartida |

## Diseño vigente de la propuesta

El [rediseño del 2026-10-12](../../../ondexia.docs/landing-propuesta/2026-10-12-rediseno.md)
complementa el trabajo anterior. Conserva índigo, grises azulados, Outfit
400/500/600, radio 6 px y fuentes locales. La separación se consigue con espacio
y bordes; no hay sombras en superficies. La portada es clara, el recorrido de
venta oscuro y las secciones evitan repetir tarjetas con iconos decorativos.

La vista del producto es una ilustración, no una captura de producción. Importes
como cadenas, sin cálculos monetarios en coma flotante. No hay testimonios,
certificaciones, cifras de clientes ni demanda de planes inventados.

Menú y preguntas funcionan con HTML nativo y teclado. El foco permanece visible
y se respeta movimiento reducido. La [evidencia local](../../../ondexia.docs/evidencias/2026-10-12-rediseno-landing.md)
distingue las comprobaciones ejecutadas de conformidad WCAG o conversión no medidas.

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
infraestructura. Este rediseño local no ejecuta CD ni autoriza apply, sincronizar
S3 o invalidar CloudFront. Revisar las decisiones pendientes antes de publicar.
