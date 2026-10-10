# Logos históricos y cabecera de comprobantes

Base: `9b5d90ecc6416a080c924a8fade56bd46aa7d779`, rama `develop`.
Especificación: [logos históricos](../especificaciones/2026-10-10-logos-historicos-comprobantes.md).

## Fallo previo y corrección

Antes del arreglo, `PuntoDeVentaIT.versionesDeLogoEnLaEmision` falla porque la emisión no devuelve el logo de la versión cargada. `IdentidadVisualIT.quitarConservaHistoria` falla porque quitar elimina el archivo. Las dos pruebas fallan por aserción, sin errores de entorno. Las pruebas web de A4 y ticket fallan porque la hoja no muestra el logo guardado.

V27 captura las claves en la inserción de todo documento y mantiene NULL en las filas existentes. El disparador de inmutabilidad protege ambas referencias. La persistencia convierte las claves en URLs al reconstruir el documento; no consulta la identidad actual. Quitar conserva el archivo. Ventas y Configuración comparten `CabeceraComprobanteComponent` y el contrato OpenAPI incorpora los campos junto al modelo Angular.

## Verificación

- 502 pruebas backend correctas, sin omisiones; 123 web correctas; compilación de producción correcta.
- La prueba de historial emite sin logo, con versión A, después con B y después de retirar los logos. Las consultas conservan las versiones originales.
- El vendedor recibe el logo del documento aunque Identidad visual responda 403. Consultar ese documento desde otra empresa responde 404.
- `PuntoDeVentaIT.inmutable` rechaza cambios directos en las dos referencias; permite cambiar estado.
- Mutación aislada: añadir un logo de respaldo a documentos sin versión produce dos fallos web. Archivo restaurado y contrastado byte a byte.
- Navegador Chromium: 21 escenarios de pantalla y ocho de impresión correctos a 320/390/768/1440 px. Además, ocho comprobaciones de cabeceras de Configuración sin desbordes. Ventas usa URLs sintéticas A y Configuración B; se comprueba la selección, carga de imagen, disposición A4 y filtro monocromo de ticket.
- API, sesión, logos y datos del navegador son sintéticos. No se emiten documentos reales. Los importes esperados proceden de ejemplos fijos, no de la respuesta observada.

Las primeras ejecuciones backend no accedían a PostgreSQL por el aislamiento del ejecutor y después porque su contenedor estaba detenido. No se consideran evidencia de fallo funcional. Se arrancó exclusivamente el contenedor etiquetado como pruebas sintéticas; se repitieron las pruebas y se registraron los fallos funcionales antes de implementar. La base de desarrollo existente no se borró ni se reinició.

## Límites

S3/CloudFront reales, dispositivos físicos, Safari, impresoras físicas, migración sobre copia de producción, auditoría independiente, CI remoto y despliegue: no verificados. El versionado aquí conserva la clave única creada por cada subida confirmada; no almacena los bytes en PostgreSQL. Capturas con el icono de la aplicación como logo sintético, sin representar una subida real.

[Resultados estructurados](2026-10-10-logos-historicos.json). Los logs resumidos y capturas están en `2026-10-10-logos-historicos/`.
