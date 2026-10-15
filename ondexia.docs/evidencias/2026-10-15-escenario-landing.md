# Ventana de producto ligada al scroll · revisión local 2026-10-15

## Alcance y trazabilidad

El propietario pidió capturas de computador de la app, animaciones sincronizadas
con el scroll y evaluar morphing. Se complementa la propuesta existente; no se
modifican reglas, datos, precios, API, autenticación ni emisión fiscal.

- Repositorio: `/workspace/ondexia`, rama `feature/landing-interactiva`.
- Base develop: `69290ea19f6a27279460c57d271c73e3c5d6a5b7`.
- HEAD antes de esta revisión: `6ca86e8bf7e927c3f60f5dc9a60056d142e145e2`.
- [Especificación y fuentes primarias](../landing-propuesta/2026-10-14-interactividad.md#revisión-vigente--2026-10-15--ventana-central-y-capturas-de-computador), fijadas antes del código.
- [Hashes de fuente y compilado](2026-10-15-escenario-landing/compilado.json).

La propuesta se mantiene local, sin publicar commits ni ejecutar CI remoto o CD.
La rama conserva las candidatas anteriores como historia. Este resultado no
implica aceptación visual del propietario ni mejora comercial demostrada.

## Resultado visual

Una ventana central muestra venta, registro y documento dentro del sistema
completo, incluyendo menú lateral y cabecera. El mismo progreso controla fundidos,
escala proporcional del marco y un contorno SVG interpolado; no deforma la interfaz.
El scroll se puede detener y recorrer en reversa. No hay reloj de diapositivas ni
interceptación de rueda, gestos o teclas.

- [Vídeo de escritorio](2026-10-15-escenario-landing/recorrido-escritorio.mp4).
- [Venta](2026-10-15-escenario-landing/venta-escritorio.png), [registro](2026-10-15-escenario-landing/registro-escritorio.png) y [documento](2026-10-15-escenario-landing/documento-escritorio.png).
- [Móvil con la misma captura desktop](2026-10-15-escenario-landing/venta-movil.png).
- [Ampliación ajustada](2026-10-15-escenario-landing/ampliacion-ajustada-movil.png), [tamaño real](2026-10-15-escenario-landing/ampliacion-real-movil.png) y [alternativa estática](2026-10-15-escenario-landing/alternativa-movil.png).

La grabación nativa de Playwright utiliza rueda automatizada; los PNG no se
editaron. Se conserva WebM original y una transcodificación H.264/MP4 para lectura
en móvil, sin cambiar la interfaz ni fabricar fotogramas. No son fotogramas fabricados ni capturas de producción. La alternativa
estática se mantiene sin JavaScript, con movimiento reducido, por elección del
visitante, en ventana baja o si faltan capacidades de animación/unidades CSS.

## Procedencia de las capturas

Los tres originales son viewport **1440 × 1000**, obtenidos del Angular compilado
con `herramientas/capturar-producto-landing.py` sin modificarlo. Los PNG publicados
son copias exactas. El [manifiesto](2026-10-15-escenario-landing/capturas-completas.json)
conserva SHA-256 del helper, fixtures, frontend y originales.

Fixtures y sesión sintéticos; 50 GET interceptados, ninguna escritura o conexión
externa. El caso independiente tiene nota `NV01-00000042`, tres unidades de 150,
dos de 25 y un servicio de 35: total 535, efectivo 550 y vuelto 15. La numeración es
coherente en las tres pantallas. No se autenticó contra Cognito ni se consultó
backend, clientes o SUNAT reales. El encabezado completo se revisó visualmente.

Las seis variantes previas recortadas/móviles se sustituyeron por los tres
originales completos. Esos tres PNG suman 293205 bytes y se reutilizan en todos
los tamaños. Los dos scripts generados suman 8071 bytes sin compresión; no hay
biblioteca o framework nuevo en cliente.

## Fallos, correcciones y sensibilidad

| Contraste | Sin el cambio | Candidata corregida |
|---|---|---|
| Capturas de computador y recorrido central | Build 6ca86e8 usa recortes de 1082 px, variantes móviles y no tiene ventana central/morph | Mismos tres PNG completos 1440×1000 y cinco estados del progreso |
| Geometría del progreso | La entrada antigua de `.explorador` podía desplazar 14 px el inicio cacheado; un punto del 25% dio 0.5643/0.4357 | Se elimina la entrada del contenedor; el marco tiene movimiento propio. Tres cargas frescas vuelven a satisfacer los estados fijados |
| AI-01, foco al reducir movimiento desde «Ver sin animación» | El botón se oculta y el foco cae en body | Transfiere al artículo visible; original/ampliar conservan acción equivalente |
| AI-02, reapertura de captura | Lienzo conserva desplazamiento vertical 141 px | Reset tras abrir: ambos ejes en 0 y ajuste inicial |
| AI-03, ajuste del diálogo | Imagen 1390×965.27 en lienzo 1390×824: parte inferior fuera | Ajuste proporcional completo; tamaño real de 1440 conserva pan interior |
| AI-04, resize 320×250 con control enfocado | Control equivalente termina en 257.14 px: 7.14 px fuera | Termina en 234.14 px, enteramente visible |

Los tres primeros hallazgos de la auditoría se contrastaron con los mismos pasos
en la candidata conservada en 4328 y la corregida 4324. El informe independiente
conserva severidades, datos y hashes que realmente se registraron por ejecución;
no atribuye toda la matriz a la última sonda focalizada. La incidencia geométrica
la reprodujo el comprobador, no el auditor. Los controles de opacidad y morphing
se mutaron por separado en el DOM, se detectaron y se restauraron. El control de
contraste ya correcto también detectó su mutación y volvió a pasar.

[Informe independiente](2026-10-15-escenario-landing/auditoria-independiente.json),
con observaciones comprimidas enlazadas desde ese informe. Ningún hallazgo queda
abierto en los casos auditados; eso no cierra una auditoría global del sistema.

## Comprobaciones ejecutadas

El build oficial pasa Astro check (17 archivos, sin errores/advertencias), Astro
build, sitemap/canonical/OpenGraph y tres comprobaciones de direcciones. Tono y
forma pasa. Las dos pruebas unitarias de CSP pasan. El comprobador de responsividad
pasa 320/390/768/1024/1440, menú/FAQ por teclado y contraste 6.43:1; la mutación 2.25:1
se detecta y se restaura.

El [pase integrado](2026-10-15-escenario-landing/interactividad.json) termina
`correcto/completado`, código de salida 0. Comprueba los cinco progresos y reversa,
pausa estable, mutaciones aisladas/restauradas, rueda y teclas nativas, tres
originales completos, ampliación en móvil/escritorio, diez combinaciones de ancho
y altura (hasta 320×225), foco al redimensionar, reducción dinámica y detención
persistente. También pasan la base sin JavaScript, las capacidades ausentes y
la CSP anterior con scripts bloqueados. Las [tres cargas frescas](2026-10-15-escenario-landing/tres-cargas-frescas.json)
conservan los cinco estados sin la entrada antigua del contenedor.

La CSP efectiva local permite el script propio y rechaza inline, eval, scripts
de otro origen, iframes y conexiones locales/de otro origen. Ninguna conexión
externa o error de aplicación se observó. Son pruebas de la cabecera servida por
el helper, no de su aplicación efectiva en CloudFront.

El [resumen del comprobador](2026-10-15-escenario-landing/resumen-aceptacion.json)
separa fallos del producto de preparación: la primera invocación
sin ruta temporal falló al intentar crear la configuración de Astro fuera del
workspace; se ejecutó después con telemetría desactivada y configuración temporal.
Se corrigieron la firma `arg` de Playwright y las esperas del comprobador sinJS:
los callbacks rAF de página no sirven si JavaScript está deshabilitado. También
se espera el evento `close`, además de que desaparezca `open`, antes de probar
otra preferencia: el manejador aún puede restaurar el foco. Un predicado literal
evita construir código para esa espera bajo la CSP. El clic automático de
Playwright en anclas estáticas se prepara dejándolas visibles bajo la cabecera;
el foco nativo se contrastó por separado y quedó completamente visible. El tamaño
real se mide después de pintar, manteniendo el esperado de 1440; el CSS global de
movimiento reducido introduce una transición de 0.01 ms antes de ese renderizado.
Para grabar se reutilizó FFmpeg instalado mediante caché temporal de Playwright,
sin descargar herramientas ni escribir en el directorio personal.

## Perfil descriptivo

Chromium 151.0.7922.173 headless, 1440×1000, sin limitar CPU. Tres pares con/sin
actualización del path; cada tramo son 240 cuadros solicitados con rAF, ida y
vuelta. Otros navegadores de comprobación estaban cerrados durante este perfil.
La mutación bloquea solo `d`, conserva el resto de la composición y se restaura.

| Ronda | Morphing | Tareas largas | Layout (ms) | Pintura (ms) |
|---|---|---|---|---|
| 1 | Sí | 0 | 71.95 | 121.02 |
| 1 | No | 0 | 71.71 | 119.73 |
| 2 | Sí | 0 | 73.28 | 122.30 |
| 2 | No | 0 | 68.56 | 121.40 |
| 3 | Sí | 0 | 73.85 | 125.73 |
| 3 | No | 0 | 63.93 | 109.76 |

[Mediciones](2026-10-15-escenario-landing/resumen.json), primera pareja de trazas
comprimidas y [guion de reproducción](2026-10-15-escenario-landing/reproducir-perfil.py).
Hay trabajo de layout y pintura: no se presenta todo el efecto como composición
fuera del hilo principal. Estas muestras permiten conservar el contorno en la
candidata para revisión, sin atribuir causalidad a diferencias pequeñas ni
prometer 60 fps. Orden con/sin repetido, cachés y host compartido limitan la comparación.
La mediana de intervalos observada 16.7 ms no equivale a garantía en dispositivos.

## Límites

No verificado: Firefox/WebKit, teléfono físico, lector de pantalla real, WCAG
completa, redes lentas, conversión, recepción de contacto, CI remoto, SCA de esta
revisión, configuración efectiva de AWS o emisión/impresión fiscal. La CSP se
contrastó sirviendo la declaración Terraform; no se inspeccionó AWS. No se aplicó
infraestructura, no se tocaron datos ni se publicaron cambios.
