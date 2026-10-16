# Presentación continua de la landing — 2026-10-15

## Alcance y procedencia

Rama `feature/recorrido-presentacion-producto`, desde develop
`cdbed6b2c21438fca6753180dd46af94c9ac9a5e`. Implementa la referencia visual y el
recorrido descritos por el propietario: verbos, complementos, captura centrada,
traslado lateral, acordeón por scroll, herramientas, planes, confianza y cierre.
La especificación complementa [el documento existente](../landing-propuesta/2026-10-14-interactividad.md#revisión-del-recorrido-continuo--2026-10-15).

Se reutilizan los tres PNG de 1440 × 1000 del frontend Angular real con fixtures
sintéticos. Su origen y reproducción permanecen en la evidencia del escenario
anterior; no se generaron interfaces ficticias ni capturas de clientes. Los
objetos SVG alrededor son ilustrativos y están fuera del árbol accesible.

No se modificaron API, reglas, importes, datos, autenticación, infraestructura,
políticas CSP ni SUNAT. No se ejecutaron Deploy/CD, Terraform, S3 ni invalidaciones.

## Verificaciones realizadas

- Oráculo fijado antes de implementar: el build base no contiene los tres verbos
  interactivos en el h1; la comprobación falla con 0 frente a 3 esperados.
  El fallo de arranque anterior por permisos de socket no se utiliza como prueba roja.
- Build Astro con check: 20 archivos, cero errores, advertencias o hints. Salida
  estática de portada y `planes.html`; pruebas de sitemap, canonical, Open Graph
  y las tres comprobaciones de direcciones correctas.
- Se ejecutaron los comandos del script build con el Astro ya instalado y
  `ASTRO_TELEMETRY_DISABLED=1`. El envoltorio pnpm del entorno intentaba instalar
  y fallaba abriendo su base SQLite; no se cambiaron dependencias para resolverlo.
- Tono y forma correcto; dos pruebas unitarias de frontera CSP correctas.
- Harness de navegador: hover, foco y Enter seleccionan imagen, elementos y
  enlace original. Salida de complementos al 16 %, traslado intermedio real,
  tres capítulos con barra al 50 %, reversa y ausencia de avance autónomo.
- Navegación de acordeón por teclado, foco conservado incluso al retroceder,
  ampliación del PNG actual, tamaño real, Escape, retorno de foco y reapertura
  ajustada. «Ver sin animación», reducción dinámica y cambio de tamaño conservan
  la escena equivalente. El escenario se libera después del último capítulo.
- Carrusel con avance, retorno y extremos; página de planes abre como archivo
  estático. Importes 89/199/390 conservados; ofertas JSON-LD únicamente en planes.
- Alternativas: 320×900, 390×844, 768×900, 1024×900, 1440×400, 320×225,
  escritorio con movimiento reducido, y escritorio/móvil sin JavaScript.
  Las anclas funcionan y no hay desbordamiento de la página.
- Harness de responsividad existente: 320/390/768/1024/1440; menú y preguntas
  operables por teclado. Contraste del párrafo de acceso 6,43:1. Mutación aislada
  del fondo/texto detectada a 2,25:1 y restaurada. La mutación de la barra también
  se detecta y restaura antes de continuar; ninguna afecta fuentes o producción.
- CSP servida desde Terraform: scripts propios operativos; inline, eval, scripts
  externos, marcos y conexiones bloqueados. Sin errores JS ni conexiones externas
  durante los recorridos de aceptación.

El [resultado estructurado](2026-10-15-presentacion-continua.json) conserva la
prueba roja, el recorrido final, la responsividad y el perfil local. Una grabación
programada de 16 segundos de scroll no registró tareas mayores de 50 ms; esta
medición descriptiva no acredita 60 fps ni rendimiento en equipos físicos.

Los artefactos de revisión de esta sesión se entregan en
`/workspace/ondexia-presentacion/`: video `recorrido.mp4`, `portada.png` y
`recorrido.png`. No son recursos publicados en la landing. Los comandos de
reproducción están en el README del paquete. El harness histórico anterior se
conserva con `--escenario-historico`; su composición y sus offsets no son los
oráculos de esta revisión.

## Límites y pendientes

Revisión propia, sin auditoría independiente nueva. Chromium local únicamente;
pendientes otros navegadores, teléfonos físicos y evaluación WCAG completa.
No se atribuye CI verde ni comportamiento AWS a pruebas locales.

La política de privacidad y la información legal siguen pendientes de aprobación y
publicación; los botones de privacidad y seguridad permiten consultar por correo.
No se anuncian certificaciones ni garantías. El acceso conserva la solicitud
vigente y el ingreso a la aplicación; no se crea una prueba gratuita. El estado
de emisión ante SUNAT permanece en ensayo. Las condiciones de planes siguen
siendo propuesta comercial sin IGV, pendiente de validación.
