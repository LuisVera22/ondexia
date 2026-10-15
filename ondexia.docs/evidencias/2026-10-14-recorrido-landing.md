# Recorrido visual de la landing · evidencia

Se complementa la [especificación existente](../landing-propuesta/2026-10-14-interactividad.md)
tras la aclaración «contenido interactivo, casi como diapositivas». Base local:
`435029495b17612010bcbc3db9da110d45cd190e`, misma rama
`feature/landing-interactiva`, originalmente desde develop. No se publica ni se
modifican reglas de venta, contratos, importes, migraciones o datos.

## Cambio y aceptación

Tres artículos permanecen visibles. El desplazamiento señala el capítulo actual
sin mover el foco; enlaces y Anterior/Siguiente permiten ir directamente a una
escena. Son capítulos con `aria-current="step"`, no pestañas sobre paneles
simultáneamente visibles. El foco llega al artículo cuando se solicita el salto.

En escritorio una figura puede fijarse dentro de su escena solo si cabe. El
marco de la captura tiene parallax real ligado al desplazamiento, acotado a
±12 px. Movimiento reducido elimina parallax y fijación de figuras, también
cuando cambia durante la visita. Menú, ampliación, originales y alternativa sin
JavaScript se conservan; sin código se indica «3 escenas», sin progreso ficticio.

Las seis capturas originales son las de la candidata 4350294, idénticas por
SHA-256. Su [procedencia y límites](2026-10-14-landing-interactiva.md) siguen
vigentes: frontend real, API/sesión sintéticas y ninguna emisión real.

## Contraste y correcciones

| Caso | Sin cambio/arreglo | Resultado final | Evidencia |
|---|---|---|---|
| Recorrido por capítulos | Build anterior deja solo un artículo visible; falla el nuevo oráculo de tres capítulos | Tres visibles, indicadores y avance manual coherentes | [Antes](2026-10-14-recorrido-landing/sin-recorrido.log.gz), [final](2026-10-14-recorrido-landing/aceptacion.log.gz) |
| Ventana baja | Título recortado en 320×320, 360×250, 390×240; el primer ajuste aún falla en 320×225 | Navegación en una fila, controles de 44 px, títulos y foco visibles con y sin código | [Rojo](2026-10-14-recorrido-landing/altura-sin-arreglo.log.gz), [reflujo rojo](2026-10-14-recorrido-landing/zoom-sin-arreglo.log.gz), [final](2026-10-14-recorrido-landing/aceptacion.log.gz) |
| Etiqueta incluida en nombre | Se muestra 01/02/03 pero el nombre accesible omite el número | Número y título forman el nombre accesible | [Antes](2026-10-14-recorrido-landing/nombres-sin-arreglo.json), [después](2026-10-14-recorrido-landing/nombres-con-arreglo.json) |

Los esperados se fijaron en la especificación antes de observar: orden 1/2/3,
nombre que incluye etiqueta, título bajo los controles y dentro del viewport,
transformación limitada y nula con movimiento reducido. El fallo del build
anterior expresa una diferencia respecto del nuevo recorrido solicitado; no
declara que el selector anterior fuera un defecto de negocio.

Se ejecutó la aceptación en 320/390/768/1024/1440 px y alturas
1000/900/844/400/320/250/240/225 según su matriz, incluyendo 320×225 como
reflujo equivalente de 1280×900 al 400%. Es reflujo simulado, no una prueba con
teléfono físico ni todos los mecanismos de ampliación de cada navegador.

## Sensibilidad, seguridad y construcción

La transformación observada cambia de 0,79 a −1,61 px al desplazar 180 px la
escena, dentro del límite especificado. Mutar aisladamente `transform: none
!important` en DOM hace fallar el oráculo de movimiento; retirar esa propiedad
restaura el resultado. Se conserva la mutación de contraste: 2,25:1 falla frente
al mínimo 4,5:1; restaurado 6,43:1.

[Aceptación](2026-10-14-recorrido-landing/aceptacion.log.gz),
[resultados](2026-10-14-recorrido-landing/interactividad.json),
[contraste](2026-10-14-recorrido-landing/responsividad.log.gz).
La CSP vigente de la candidata permite script local y rechaza inline y fetch,
sin ampliar permisos. Con la cabecera anterior quedan los capítulos y enlaces
pero no se habilitan botones de script. No hay solicitudes externas ni errores
de página en la aceptación final.

Build oficial: 17 archivos, cero errores, advertencias o hints; sitemap,
canonical/Open Graph y tres pruebas de direcciones pasan.
[Log sin alterar, comprimido](2026-10-14-recorrido-landing/compilacion.log.gz).
Tono y forma y `git diff --check` pasan. Un servidor apagado y un diagnóstico de
nulabilidad DOM detuvieron intentos de preparación; se resolvieron antes de la
aceptación final y no se usan como evidencia de fallos fiscales.

## Revisión independiente y vista

La revisión comenzó con contexto propio sin historia de implementación. Encontró
el recorte por altura y el nombre accesible; ambos fueron reproducidos y
corregidos. Revalidó 12 contextos con/sin código, incluidos 320×225 y límites
599/600, controles de 44 px, teclado, foco, nombres y movimiento reducido dinámico.
No confirmó nuevos defectos en ese alcance.

[Inspección](2026-10-14-recorrido-landing/auditoria-resultados.json.gz),
[límites](2026-10-14-recorrido-landing/auditoria-bordes.json),
[revalidación](2026-10-14-recorrido-landing/auditoria-revalidacion.json.gz),
[cierre](2026-10-14-recorrido-landing/auditoria-cierre.json.gz).

[Vídeo continuo de 25,68 s](2026-10-14-recorrido-landing/recorrido-escenas.webm),
[escritorio](2026-10-14-recorrido-landing/escena-escritorio.png),
[móvil](2026-10-14-recorrido-landing/escena-movil.png),
[320×225](2026-10-14-recorrido-landing/escena-movil-horizontal.png).
[Manifiesto](2026-10-14-recorrido-landing/presentacion.json): HTML y ambos scripts
servidos coinciden con dist final; sin edición de imágenes ni vídeo.
HTML SHA-256 `087791156962213c705ff2df3f3ce85865fc57820951d73243752c72633df059`.

## No verificado

CI remoto, SCA nuevo, AWS/CSP efectiva desplegada, SUNAT/backend real, dispositivos
físicos, lector de pantalla, conversión y conformidad WCAG completa. No se han
observado las animaciones exactas de ChatGPT. Se conserva el requisito de publicar
también la CSP de la candidata; no se ejecuta CD ni se modifica main.
