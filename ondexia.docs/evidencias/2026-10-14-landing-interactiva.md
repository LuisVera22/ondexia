# Landing interactiva · evidencia local

## Alcance y procedencia

Se implementa la [especificación](../landing-propuesta/2026-10-14-interactividad.md)
desde develop `69290ea19f6a27279460c57d271c73e3c5d6a5b7`, en
`feature/landing-interactiva`. Cambian la presentación pública, su escritura,
capturas e interacciones; no cambian el backend, contratos, autenticación,
migraciones, cálculo monetario ni emisión fiscal.

Las seis imágenes de `apps/frontend/ondexia.landing/public/capturas/` son
capturas nativas de componentes del frontend Angular compilado. Hay variantes
de escritorio y móvil para venta, registro y documento. El navegador usa una
sesión sintética y respuestas GET de una lista cerrada; no ejecuta escrituras,
conexiones AWS, Cognito, SUNAT ni consultas a una base de datos. Datos de
demostración: Comercial Demo S.A.C., RUC 20000000000, Cliente de demostración,
DNI 00000000. No son identidades de clientes acreditadas.

El oráculo del recorrido se fija antes de la captura: nota NV01-00000042,
3 × S/150 + 2 × S/25 + 1 × S/35 = S/535; entregado S/550, vuelto S/15.
Las fixtures conservan importes como cadenas/Decimal. Se usan búsquedas,
selecciones e inputs nativos; no se reconstruye ni modifica el HTML del producto
para simular una pantalla.

[Procedencia](2026-10-14-landing-interactiva/procedencia.json) conserva hashes del
script, fuentes, build, capturas y recortes nativos. Los seis PNG fuente, copias
públicas y assets servidos coinciden por SHA-256. Hay 50 GET de fixtures, ninguna
petición bloqueada inesperada y ningún error JavaScript. El helper admite solo
orígenes locales y destino externo, y bloquea redirecciones y WebSockets.
[Fixtures](2026-10-14-landing-interactiva/fixtures.json) y
[control del recorrido](2026-10-14-landing-interactiva/control-recorrido.json).

## Fallos demostrados y arreglos

| Corrección | Sin arreglo | Con arreglo | Evidencia |
|---|---|---|---|
| CSP para interacción local | La misma versión bajo la cabecera anterior no inicia pestañas: cero tabs, tres escenas estáticas disponibles | `script-src 'self'` permite archivos locales; scripts inline y conexiones siguen rechazados | [Cabecera anterior](2026-10-14-landing-interactiva/csp-anterior.txt), [ejecución](2026-10-14-landing-interactiva/interactividad-verde.log) |
| Inicio de la ampliación | Tras desplazar venta y ampliar registro, el control «cada apertura comienza arriba» falla | Venta, registro y documento reabren en scrollTop 0 | [Rojo](2026-10-14-landing-interactiva/reapertura-sin-arreglo.log), [verde](2026-10-14-landing-interactiva/interactividad-verde.log) |
| Coherencia de imágenes | Preparación muestra 43 y registro/documento 42; control DOM falla en ambos anchos | Recaptura desde Angular con 42 en las tres escenas; no edición del PNG | [Antes/después y hashes](2026-10-14-landing-interactiva/correccion-correlativo-capturas.json) |

Los fallos de CSP y reapertura se ejecutaron antes de sus cambios. El defecto de
correlativo se reprodujo con las fixtures anteriores antes de recapturar.
Un intento de build desde una carpeta sin package.json fue un error de
preparación, no evidencia funcional roja. La diferencia temporal entre una
recaptura final y el asset del build se resolvió reconstruyendo y contrastando
sus bytes; no se alteró la numeración mediante edición de imágenes.

## Sensibilidad de controles conservados

- Estado de pestaña: cambiar aislada y temporalmente `aria-selected` de la primera
  escena a false se detecta; recargar restaura el estado correcto.
- Contraste: mutar en DOM el texto y fondo del contacto baja a 2,25:1; el oráculo
  WCAG de texto normal exige 4,5:1 y falla. Tras restaurar, 6,43:1.
- Conexiones: introducir aisladamente `connect-src 'self'` en la política provoca
  fallo del control de denegación. Se restaura el archivo idéntico byte por byte
  en finally y las dos pruebas CSP pasan.

[Mutación CSP](2026-10-14-landing-interactiva/mutacion-conexiones.log),
[restauración](2026-10-14-landing-interactiva/restaurado-csp.log),
[hash de restauración](2026-10-14-landing-interactiva/mutacion-csp.json) y
[contraste](2026-10-14-landing-interactiva/responsividad.log).

## Comprobaciones ejecutadas

- Build oficial Astro: 17 archivos revisados; cero errores, advertencias o hints.
  Compilación estática, sitemap, canonical/Open Graph y tres controles de
  direcciones pasan. [Log](2026-10-14-landing-interactiva/compilacion.log.gz).
- Suite de herramientas: 63 pruebas pasan, incluidas las dos de CSP de landing.
  [Log](2026-10-14-landing-interactiva/pruebas-herramientas.log).
- Tono y forma: control existente pasa. `git diff --check` pasa.
- Chromium local: 320/390/768/1024/1440 px sin desbordamiento horizontal de
  página; variantes móviles reales, menú y preguntas con teclado. Pestañas con
  flechas/Inicio/Fin, diálogo, Escape, retorno del foco y reapertura desde arriba.
- Sin JavaScript: tres escenas visibles y enlaces móviles originales; controles
  dependientes de script ocultos. CSP anterior conserva esa alternativa.
- La política servida permite solo scripts ejecutables externos locales y
  rechaza inline y fetch incluso del propio origen. Cero solicitudes externas
  o errores de página durante la aceptación.

[Resultados de interacción](2026-10-14-landing-interactiva/interactividad.json) y
[responsividad](2026-10-14-landing-interactiva/responsividad.json).
La [reproducción](../../apps/frontend/ondexia.landing/README.md) utiliza el
harness existente; no requiere añadir servicios a la landing.

## Revisión independiente y material visual

La revisión se inició con contexto propio y sin la narrativa de implementación;
leyó el checkout compartido y ejercitó el sitio local. Encontró los dos defectos
de reapertura y correlativo anteriores. Después repitió esos casos y recapturó
personalmente Angular con el helper; ambos quedaron corregidos en su alcance.
Confirmó además transición de pestañas de 200 ms con el token existente,
ausencia de animación bajo movimiento reducido y ayuda de desplazamiento móvil.

En la inspección inicial comprobó contraste de 119 textos DOM (mínimo 6,2875:1),
teclado/foco, altura reducida/orientación horizontal, originales sin JavaScript
y sondas locales que rechazaron inline, eval, scripts de otro origen, fetch e
iframe. Estas sondas no probaron servicios externos. La revalidación no repitió
todas esas sondas ni constituye una auditoría de AWS o de todo el sistema.

Capturas de evaluación:
[portada](2026-10-14-landing-interactiva/portada-escritorio.png),
[galería](2026-10-14-landing-interactiva/galeria-escritorio.png),
[documento](2026-10-14-landing-interactiva/galeria-documento-escritorio.png),
[móvil](2026-10-14-landing-interactiva/galeria-movil.png).
[Vídeo del recorrido](2026-10-14-landing-interactiva/recorrido-interactivo.webm):
21,68 segundos, portada → explorar → pestañas → ampliar → cerrar → continuar.
Imágenes y vídeo del navegador sin edición.
[Manifiesto](2026-10-14-landing-interactiva/presentacion.json).

## Límites y publicación

No se ejecutaron CI remoto ni SCA nuevo, Terraform fmt/validate (CLI no disponible),
backend/SUNAT real, autenticación real, pruebas en dispositivos físicos, métricas
de conversión, rendimiento de campo ni una evaluación WCAG completa. Tampoco se
observaron las animaciones exactas de ChatGPT: la referencia comprobada es su
estructura y escritura; las interacciones de Ondexia son una propuesta propia.

No se ha modificado AWS ni publicado esta versión. La próxima publicación debe
aplicar también la política CSP revisada de la landing: sincronizar solo los
assets y conservar la cabecera anterior bloquearía el código interactivo. No se
autoriza un apply ni se altera main por esta evidencia. CTA/precios, contenido
legal, canal de contacto y estado del ensayo SUNAT conservan sus pendientes.
