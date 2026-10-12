# Landing · evidencia local del rediseño

Base develop `5d1bfe34bfbb303e287d0248fe110edb5d372ecb`.
[Necesidad, referencias y aceptación](../landing-propuesta/2026-10-12-rediseno.md).

## Corrección de contraste

Se sirvieron por separado el build anterior, sin el arreglo, y el build del
rediseño. El mismo comprobador lee color y fondo efectivos del párrafo de contacto
y exige 4,5:1 por WCAG 2.2 SC 1.4.3, sin derivar el umbral del resultado observado.

- Sin arreglo: falla con contraste 2,25:1.
- Con arreglo: pasa con contraste 6,43:1.
- Mutación aislada de color/fondo en el DOM: detectada por la misma comprobación.
- Recarga, sin modificaciones de archivos: contraste correcto de nuevo.

La mutación no forma parte del código. Es una corrección concreta de legibilidad,
no una prueba de preferencia estética ni una auditoría completa de accesibilidad.

Una repetición inicial leyó estilos antes de que los colores mutados estuvieran
aplicados y no detectó la mutación. El comprobador ahora espera esos colores
explícitos antes de medir; la repetición final detecta 2,25:1 y pasa tras recargar.
No se rebajó el umbral ni se cambió el oráculo.

La imagen social referida por el build anterior respondía 404 en el servidor
local. La misma ruta responde 200 en el build con el PNG incorporado.

## Verificación ejecutada

- Script de build del paquete, mediante `npm run build`: Astro check sin errores,
  compilación estática, sitemap/canonical/Open Graph y tres controles existentes
  de direcciones correctos. Dependencias fijadas por pnpm-lock.yaml, sin cambios.
- `node herramientas/comprobar-tono-y-forma.mjs`: correcto.
- Chromium local: 320, 390, 768, 1024 y 1440 px sin desbordamiento horizontal;
  menú móvil y preguntas con Enter; anclas válidas; un h1; sin scripts ejecutables.
- Capturas tomadas con movimiento reducido y revisadas visualmente.
- `git diff --check`: correcto al registrar el resultado.
- Revisión local adicional sin historial de implementación: sin desbordamiento
  también en 568 × 320; foco visible y movimiento reducido correctos. La revisión
  señaló la omisión de «en ensayo», corregida conforme a doc 14 antes del cierre.

El entorno requirió reconstruir dependencias locales incompletas. Se usó
`ASTRO_TELEMETRY_DISABLED=1 XDG_CONFIG_HOME=/tmp/ondexia-landing-config` para el
build. La compilación invoca exactamente los scripts del paquete; usar npm para
ejecutarlos no reemplaza el gestor ni modifica el lockfile.

Capturas conservadas: [escritorio](2026-10-12-rediseno-landing/escritorio.png),
[móvil](2026-10-12-rediseno-landing/movil.png) y
[medidas](2026-10-12-rediseno-landing/verificacion.json).

El comprobador manual está en `herramientas/comprobar-responsividad-landing.py`.
Requiere Playwright para Python y Chromium, disponibles en esta ejecución; no
se asumen instalados en otra máquina. Con ambos builds servidos, ejecutar desde
raíz `python herramientas/comprobar-responsividad-landing.py`; variables opcionales
`URL_LANDING`, `URL_LANDING_ANTERIOR` y `EVIDENCIA_LANDING`. La versión anterior es
el build del commit base citado, servido en 4322; la actual, en 4321. La carpeta
externa de trabajo `/workspace/ondexia-vistas/landing/` no se da por transferida.

## No verificado y pendientes

No se ejecutaron publicación, CD ni despliegue AWS. CI remoto no ejecutado para
esta propuesta. Safari/iOS, Android real, zoom con tecnologías de asistencia y
auditoría WCAG completa no verificados. Conversión y rentabilidad no medidas.
Oferta, CTA final, canal comercial y documentación legal pendientes; no se
presenta la página como lista para publicar sin resolverlos.
