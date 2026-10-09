# Adaptación de ventas a móviles

Solicitud: mejorar la adaptación de los ajustes de ventas también para móviles.
Alcance: mostrador general y emisión por módulo, alta contextual de cliente,
registro del documento y previsualización ticket/A4. No cambia reglas de negocio,
API, persistencia ni importes.

## Criterios de aceptación

- A 320 y 390 px, la página y sus controles caben sin desplazamiento horizontal.
- Las líneas permiten consultar descripción, unidad, cantidad, precio y total;
  en emisión, también editar cantidad/descuento y quitar la línea sin arrastrar
  una tabla horizontal. En escritorio se conserva la tabla.
- Las acciones del registro y de previsualización permanecen accesibles.
- Los campos móviles tienen al menos 44 px de altura y texto de 16 px, para
  facilitar la pulsación y evitar el aumento automático al enfocar en iOS.
- El alta contextual permite consultar, revisar y llegar a «Registrar y usar»
  también con 400 px de altura disponible. Conserva la venta abierta.
- El ticket se adapta al espacio de pantalla; al imprimir conserva los 80 mm.
  A4 conserva la representación impresa. El registro no aparece en el papel.
- A 768 y 1440 px no aparecen desbordamientos en las mismas vistas.

## Ejemplos y comprobación

Datos sintéticos independientes: tres cajas a S/ 150, total S/ 450, con nombres,
códigos y observaciones largos sin espacios. Las dimensiones esperadas provienen
del tamaño de ventana y de los criterios anteriores, no de resultados observados.

`herramientas/comprobar-responsividad-ventas.py` ejecuta Chromium mediante CDP y
simula todas las API. No registra clientes ni emite ventas reales. Mide límites,
campos y tablas y captura las vistas. La preparación de líneas usa la instancia
Angular en modo desarrollo para aislar el diseño del catálogo y del servidor.
Requiere el frontend local en `localhost:9100`, Chromium y el paquete Python
`websockets`. `EVIDENCIA_MOVIL` permite separar el resultado sin arreglo del
resultado corregido. El archivo de evidencia registra los resultados y límites.

La emulación no certifica teclado, impresora ni dispositivos físicos: quedan
explícitamente pendientes de verificación. La prueba de impresión debe comprobar
por separado la hoja y el ocultamiento del registro.
