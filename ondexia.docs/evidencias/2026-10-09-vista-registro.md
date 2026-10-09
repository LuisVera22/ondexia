# Evidencia: registro y previsualización de documentos

Base `20ac39e`, rama `develop`. Decisión del propietario en este chat: retirar el pie interno NV y abrir el detalle como registro; previsualización bajo demanda.

## Resultado

Registro inicial con datos, estado, cliente, líneas, pagos y totales. Botón «Previsualizar comprobante», formatos ticket/A4, impresión y regreso al registro. NV sin leyenda interna; títulos/series y leyendas fiscales conservados. Sin cambios de API, migraciones ni aritmética monetaria.

## Verificación

- Antes del arreglo: 3 fallos funcionales y 3 pasos, registro ausente y leyenda aún presente. Archivo `rojo.log`.
- Después: 119 pruebas web correctas, compilación correcta y comprobación de tono/forma. Archivo `verde-completo.log`.
- Sensibilidad: mutación aislada que aplica el resumen NV a documentos fiscales; 9 fallos de expectativas, restaurada antes del paso final. Archivo `mutacion-resumen-fiscal.log`.
- Chromium: registro inicial, previsualización, elección de formato, regreso y estilos de impresión directa desde registro. Contexto real local, nota y caja simuladas, sin escrituras reales. Capturas junto a este informe.
- Valores esperados del ejemplo aprobado: NV 450.00; fiscal base 381.36, IGV 68.64 y total 450.00. No se derivan de resultados observados.

Los registros completos viven en `/workspace/.ondexia-setup/evidencia/vista-registro/`; sus SHA-256 se conservan en el JSON adjunto. Una primera ejecución de la suite desde una carpeta incorrecta se descartó. La inspección inicial detectó hoja oculta al imprimir desde el registro; se corrigió con estilo de pantalla y regla print explícita. La comprobación posterior pasó. Una lectura durante recompilación se repitió al terminar.

## No verificado

Auditoría independiente, normativa externa sobre la leyenda retirada, impresora física y paginación multipágina. No se repitió backend: no cambió. `number` monetario preexistente en frontend sigue pendiente; no se introducen cálculos nuevos. Las capturas anteriores de ajustes de ventas quedan como evidencia histórica.
