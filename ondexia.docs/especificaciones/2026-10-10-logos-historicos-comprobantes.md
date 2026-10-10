# Cabecera y versiones históricas de logos

Decisión del propietario, 2026-10-10: los documentos sin referencia guardada no muestran logo. Cada nueva emisión conserva las versiones vigentes del logo principal y del ticket.

## Criterios de aceptación

- Dado un documento sin referencia histórica, consultar o imprimir después de cargar un logo no añade ese logo.
- Dadas las versiones A principal y A ticket, emitir guarda ambas referencias. Reemplazarlas por B o quitarlas no modifica la impresión del documento A.
- Quitar retira la referencia para futuras emisiones, conservando los archivos históricos.
- Cada nueva nota de venta, boleta, factura, nota de crédito y documento por canje captura las versiones de su propia emisión, no las del documento origen.
- A4 usa el logo principal con los datos del emisor a la izquierda y el recuadro RUC/tipo/número a la derecha. Ticket usa su logo en gris y cabecera centrada.
- Configuración y Ventas comparten la cabecera. Configuración usa versiones actuales y ejemplos identificados como previsualización; Ventas usa exclusivamente versiones guardadas.
- Consultar un documento entrega sus logos bajo los permisos de ventas, sin exigir acceso a Configuración. Se conserva el aislamiento por empresa.
- No cambian importes, cálculos fiscales, XML, estados ni leyendas aprobadas. No se agregan logos retroactivamente.

## Implementación y evidencia prevista

Migración nueva V27: referencias inmutables y captura transaccional en la base para todos los documentos. La clave única de cada subida identifica su versión; no se copia el archivo en la base. Contrato OpenAPI y modelo Angular se actualizan juntos. Pruebas de regresión fallan antes del arreglo; mutación aislada comprueba los controles que ya funcionan. Se contrastan A4/ticket, impresión y anchos móviles. S3 real, impresora física y auditoría independiente se declaran pendientes mientras no exista evidencia.
