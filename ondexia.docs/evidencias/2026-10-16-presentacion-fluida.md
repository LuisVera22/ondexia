# Presentación automática y galería compacta — 2026-10-16

Revisión local solicitada por el propietario sobre `develop`
`76a6da9c0330f6aa66f2f36623d81fc3ad151cd0`, en
`feature/presentacion-fluida`. [Resultados estructurados](2026-10-16-presentacion-fluida.json).

## Cambio y contraste

Antes: la escena seguía en «Vender» después de 6,5 segundos; el DOM contenía
cinco enlaces «Ver captura original», un botón «Ver sin animación» y tres
artículos visibles en móvil. Se observó sobre el build anterior servido localmente.

Ahora: la portada alterna Venta → Registro → Documento → Venta cada seis segundos.
El scroll gobierna el recorrido de escritorio; las pausas por interacción,
preferencia, visor y visibilidad tienen prioridad. El selector desaparece al
entrar al acordeón salvo que conserve foco. En móvil se presenta una galería
compacta; con movimiento reducido se selecciona manualmente. Sin JavaScript
continúan disponibles los tres artículos y sus imágenes enlazadas.

La captura abre un único visor con navegación, ajuste, 1440 px con paneo y
descarga. Se retiran botones de ampliación duplicados y los enlaces de las
tarjetas de herramientas. Siete objetos decorativos acompañan cada escena;
en móvil se conservan cuatro acentos pequeños. El fondo cambia de forma y las
capturas se funden; los píxeles originales del producto no se deforman.

Durante la comprobación se reprodujo y corrigió la pérdida del margen de cabecera
al pasar a móvil. También se corrigió la duración residual de transiciones con
movimiento reducido: producía un ancho intermedio de 1360 px al solicitar 1440 px;
ahora el zoom aplica inmediatamente su tamaño real.

## Verificación realizada

- Astro check: 21 archivos, cero errores, advertencias e indicaciones. Build de
  portada y planes correcto; sitemap, canonical y tres pruebas de direcciones pasan.
- Tono y forma correcto; 69 pruebas del harness Python pasan.
- Playwright/Chromium local con CSP real del repositorio: ciclo completo con reloj
  controlado, pausas, reanudación, estado oculto simulado, prioridad del scroll,
  fundido y morphing intermedios, barras al 50 %, reversa y liberación.
- Teclado y foco, resize, cambio dinámico de movimiento reducido, visor ajustado,
  tamaño real, navegación, reapertura, Escape y ausencia de recursos externos.
- Matriz de interacción: 320×900, 390×844, 768×900, 1024×900, 1440×400 y 320×225;
  1440×1000 con movimiento reducido; sin JavaScript en 1440×1000 y 390×844.
- Responsividad: 320, 390, 768, 1024 y 1440 px sin desbordamiento horizontal;
  menú móvil y preguntas operables con teclado. Contraste del párrafo de cierre
  6,43:1. Mutaciones aisladas de contraste y barra detectadas y restauradas.
- Grabación real y traza local del recorrido: una tarea larga inicial de 64 ms
  con grabación/trazado activos; ninguna adicional observada durante ese recorrido.
  No demuestra una frecuencia estable ni rendimiento en otros dispositivos.

La vista previa y la traza se conservan fuera del repositorio en
`/workspace/ondexia-presentacion-fluida/`. El video muestra el build local, sin
servicios reales. Las capturas de producto mantienen su procedencia de Angular
con fixtures sintéticos y sus hashes se registran en el JSON.

## Reproducción y límites

Usar los comandos del [README de la landing](../../apps/frontend/ondexia.landing/README.md)
y el servidor local con CSP en el puerto 4330. La ejecución verificada usó los
binarios locales de Astro directamente (`astro.mjs check`, `astro.mjs build`),
seguidos de los controles de sitemap y direcciones, y:

```sh
python3 herramientas/comprobar-interactividad-landing.py --url http://127.0.0.1:4330/ --evidencia /tmp/ondexia-fluida-pruebas
URL_LANDING=http://127.0.0.1:4330/ EVIDENCIA_LANDING=/tmp/ondexia-fluida-responsividad python3 herramientas/comprobar-responsividad-landing.py
node herramientas/comprobar-tono-y-forma.mjs
python3 -m unittest discover -s herramientas/pruebas -q
```

No se ejecutó Deploy/CD, Terraform, S3 ni invalidaciones. La revisión no acredita
producción, Firefox/Safari, teléfonos físicos, auditoría independiente, backend,
recepción de correo, SUNAT ni conformidad WCAG completa. No modifica condiciones
comerciales, políticas legales ni la corrección de MIME publicada en rc.5.
