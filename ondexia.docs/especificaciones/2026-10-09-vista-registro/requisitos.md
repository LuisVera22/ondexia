# Detalle como registro y previsualización de emisión

Decisión del propietario en este chat, 2026-10-09: retirar la descripción del pie de la nota de venta y mostrar el detalle como registro, con botón para previsualizar el comprobante. Sustituye exclusivamente la leyenda aprobada en DEC-02 de ajustes de ventas; conservar importes, título y serie propios.

## Criterios de aceptación

- Al entrar en un detalle NV, boleta, factura o nota de crédito se muestran datos del registro: tipo/número, estado, fecha, cliente, líneas, pagos, observaciones y totales. No se muestran inicialmente controles A4/ticket ni la hoja de emisión.
- «Previsualizar comprobante» abre la hoja en la misma pantalla. Permite elegir ticket de 80 mm o A4, imprimir y volver al registro, sin solicitar otra emisión ni modificar el documento.
- La nota de venta no lleva la leyenda interna en el pie. Conserva NOTA DE VENTA y su número NV; no muestra IGV. Las leyendas fiscales se conservan.
- Ejemplo independiente: 3 cajas a 150.00, total 450.00; NV muestra importe/total 450.00. Factura y boleta conservan base 381.36, IGV 68.64 y total 450.00. Descuentos y afectaciones no se recalculan.
- Acciones de canje, anulación, baja, estado SUNAT y descargas siguen disponibles según las condiciones vigentes.

## Diseño y verificación

Estado local de previsualización, apagado inicialmente; registro con secciones adaptables y tabla desplazable en pantallas pequeñas. Reutilizar la hoja existente. La impresión del navegador oculta registro y marco, y muestra solo la hoja, incluso con impresión directa del navegador desde el registro. Sin cambio de API, modelos, base, migraciones ni aritmética monetaria.

Pruebas: fallo previo de ausencia de registro y leyenda aún presente; paso tras el arreglo. Mutación aislada del resumen fiscal debe detectarse por expectativas 381.36/68.64/450.00. Revisar registro y ambos formatos en navegador con datos sintéticos. Auditoría independiente e impresora física no verificadas; no se afirma validación tributaria externa de la leyenda retirada.
