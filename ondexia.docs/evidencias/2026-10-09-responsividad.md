# Ventas en móvil: registro, emisión y alta contextual

Base local: `develop`, `bab4c20`. Especificación previa:
`../especificaciones/2026-10-09-responsividad-ventas.md`.

Las líneas se adaptan a tarjetas en móvil y mantienen la tabla en escritorio.
Las acciones se apilan o distribuyen según espacio; los campos de cobro encogen
sin ensanchar la página. Los campos móviles usan 16 px y al menos 44 px de
altura. Los textos largos se parten sin ocultarse. La previsualización del
ticket cabe en pantalla, conservando 80 mm en impresión. No cambia la API,
los cálculos ni las reglas de emisión, cliente o SUNAT.

## Resultado reproducible

`herramientas/comprobar-responsividad-ventas.py` verifica 21 escenarios de
pantalla a 320, 390, 768 y 1440 px; incluye el alta a 390 × 400 px y el acceso
a «Registrar y usar». Usa todas las API simuladas y una sesión sintética;
la preparación de las líneas usa Angular en modo desarrollo. Importes de la
nota fijados previamente: tres cajas a S/ 150, total S/ 450. Los valores
fraccionarios de la respuesta sintética usan `Decimal`, serializados como
números JSON exactos. No se crean clientes ni comprobantes reales.

- Sin los cinco archivos de interfaz corregidos, sobre el contenido original
  de `bab4c20`: fallan los 21 escenarios (salida 1).
- Con el arreglo restaurado: pasan los 21 escenarios y ocho comprobaciones
  de impresión ticket/A4 (salida 0). No hay controles fuera del ancho, los
  campos tienen las dimensiones previstas y el ticket impreso conserva 80 mm.
- Mutación aislada: cambiar `.no-imprimir` a `display:block` en impresión
  hace fallar la aserción de impresión directa desde el registro (salida 1).
  Mutación retirada; ejecución final verde.
- Regresión web: 119 pruebas correctas. Compilación de producción correcta.

Registros: `2026-10-09-responsividad/rojo.log`, `verde.log`, `mutacion.log`,
`pruebas-web.log` y `compilacion.log`. Las mediciones completas están en
`rojo/resultados.json` y `verde/resultados.json`; se conservan capturas
representativas del antes y del después. Los registros eliminan códigos de
color y espacios al final de línea; conservan el contenido y los resultados.
El JSON hermano resume el alcance.

## Límites

La emulación Chromium no certifica dispositivos físicos, Safari/iOS, Android,
teclado real ni impresora física. La altura de 400 px simula espacio reducido;
no representa una prueba de teclado. No se verificaron en esta corrección la
API real, la consulta real de RUC, el CI remoto ni una auditoría independiente.
Los formularios específicos comparten el componente de emisión comprobado;
sus rutas y restricciones se cubren en la regresión web existente.
