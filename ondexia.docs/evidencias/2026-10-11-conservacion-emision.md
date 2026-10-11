# F06: primer bloque de datos originales de emisión

Decisión del propietario: copiar los datos exactos al emitir y conservar archivos
originales enviados/recibidos. [Especificación](../especificaciones/2026-10-11-conservacion-emision.md).
Base develop `408a68f19151564854acd152bdd97f4bc9d2639d`.

## Evidencia ejecutada

- Regresión API sin arreglo: 1 fallo, esperaba Cliente al emitir y recibió Cliente
  después de emitir. Literales fijados antes de ejecutar; no obtenidos del resultado.
- Cabecera sin arreglo: 2 fallos, ticket y A4 sin Emisor al emitir / Dirección del
  local al emitir. Con arreglo: 142 pruebas web correctas, incluido estado de legado.
- Backend completo: 527 pruebas correctas (130 dominio, 288 API, 41 panel,
  41 consultas, 27 facturación). Captura de cliente y emisor/local tras cambiar
  maestros, precio/descripción conservados y rechazo de cambio del JSON por SQL.
- Mutación aislada: desactivar documento_venta_inmutable en base efímera produce
  1 fallo en inmutable, pues un UPDATE prohibido ya no lanza excepción. Archivo
  restaurado byte por byte. La ejecución posterior consta en sql-restaurado.log.gz.
- API sintética con navegador: 21 escenarios de pantalla y 8 de impresión sin
  desbordes; 320, 390, 768, 1440 px. Capturas adjuntas de ticket/A4/registro.

Java 21/PostgreSQL 17 de Testcontainers; ningún dato existente borrado. Maven
offline con configuración del entorno. SCA omitido explícitamente en local.
OpenAPI regenerado y modelos sincronizados en el mismo cambio. Sin cambiar
NUMERIC(18,6), BigDecimal, cálculos monetarios o migraciones V1–V27.

## Alcance y límites

V28 fija cliente, emisor y local mediante inserción; V27 protege el registro entero.
La API reconstruye el cliente histórico y devuelve los datos de cabecera. Angular
usa esos datos, sin consultar maestros para la hoja. Las nuevas órdenes de emisión
usan datos históricos y autenticación de transporte vigente. Un documento sin copia
no genera una nueva orden con valores actuales: respuesta de regla de negocio.

Los documentos anteriores mantienen datos_historicos NULL. No se recuperaron datos
antiguos desde XML ni se atribuyeron valores actuales como históricos. Recuperación
verificable, acceso a archivos originales, garantía S3 y segundo bloque de archivos
pendientes. No se afirma ensayo AWS, auditoría independiente ni cierre total F06.
No se genera ni se declara almacenado un PDF original en este bloque.

CI pendiente al registrar; el PR documenta CI final, merge y ciclo de rama.
