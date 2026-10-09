# Plan de implementación y evidencia

Estado: implementación local terminada por autorización explícita de Luis; auditoría independiente pendiente. [Requisitos](requisitos.md) y [diseño](diseno.md), versión 1.0.

Orquestación propuesta: una secuencia coordinada con tareas separadas por requisito. «Orquestar» no acredita instalación de agentes ni autoriza delegación automática. La auditoría se realiza en sesión independiente.

| ID | Requisitos | Trabajo y dependencia | Evidencia exigida | Estado |
|---|---|---|---|---|
| TAR-01 | Todos | Registrar respuestas DEC-01/02/03 y actualizar versión/alcance | Fuente, fecha, aceptación; no aprobar por inferencia | Completada: tres respuestas explícitas del 2026-10-08; registradas en requisitos v0.2 |
| TAR-02 | REQ-06/07/08 | Corregir rutas y alta desde listados; tipo fijo, series, permisos y régimen; conservar general según DEC-03 | ESC-09/10 y regresión de navegación; rojo antes de fix, verde después | Completada con evidencia local; consultar el informe |
| TAR-03 | REQ-04/05 | Resumen/leyenda NV en hoja y preparación; actualizar docs 12/13 tras DEC-02 | ESC-06/07; DOM y revisión ticket/A4, incluyendo nota histórica; fallo previo relevante y resultado corregido | Completada con evidencia local; consultar el informe |
| TAR-04 | REQ-01/02/03 | Extraer/reutilizar formulario sin navegación forzada; alta, selección, cancelación, permisos y duplicado tras DEC-01 | ESC-01–05; pruebas de interfaz y frontera real cuando aplique; fallo sin capacidad y correcto con ella | Completada con evidencia local; consultar el informe |
| TAR-05 | Invariantes | Verificar emisión fiscal, canje, totales, cobros, stock y consulta/atestación | ESC-08/11 y pruebas actuales pertinentes; mutaciones aisladas que las vuelvan rojas y restauración | Completada con evidencia local; consultar el informe |
| TAR-06 | Contratos/calidad | Build web, pruebas pertinentes, contratos y documentación; backend según diff real | Registros con comando, entorno, base y diff; OpenAPI/modelos juntos si API cambia | Completada con evidencia local; consultar el informe |
| TAR-07 | Todos | Preparar paquete del commit/diff para sesión de auditoría independiente | Requisitos, decisiones, evidencia y pendientes; informe independiente contrastado | Pendiente |

## Evidencia de regresión y sensibilidad

Por cada corrección, escribir escenario con resultado esperado independiente, ejecutar sobre base sin arreglo y registrar fallo por ese comportamiento (no dependencia rota); aplicar arreglo y registrar paso correcto. Para alta nueva, demostrar ausencia de capacidad en base y prueba de comportamiento tras implementación. No fijar etiquetas, cifras o reglas a partir del DOM observado.

Mutaciones propuestas para propiedades vigentes, siempre aisladas y restauradas: aceptar atestación de otro RUC; omitir permiso efectivo de registro; cambiar tipo/endpoint del formulario fijo; mostrar resumen NV en factura; duplicar cobro/stock al canjear. Elegir solo las pertinentes al cambio real y demostrar que la prueba relevante detecta cada mutación. No mutar datos existentes ni relajar controles en el entorno persistente.

Cifras del ejemplo fiscal independientes: 3×150.00=450.00; 450/1.18 a dos decimales =381.36; diferencia=68.64. Usar Decimal/BigDecimal al definir fixtures. Añadir mixtos, descuento y respuesta tardía antes de ejecutar; no escribir pruebas que solo comparen texto copiado de la implementación.

Comandos disponibles para planificar verificación: `pnpm test:ci` y `pnpm build` desde `apps/frontend/ondexia.web`; backend mediante scripts existentes de `.ondexia-setup` tras confirmar el target. Inspeccionar versión/configuración al ejecutar. Se ejecutaron las pruebas y verificaciones descritas en el [informe](../../evidencias/2026-10-08-ajustes-ventas.md). No se confunde esa ejecución con la auditoría independiente ni con servicios externos reales.

## Entrega y Git

Trabajar en `develop`, respetar cambios previos y Conventional Commits, descripción/ámbito en español y sin marcas de herramientas. Separar commits por capacidad verificable cuando el diff lo permita; títulos posibles `fix(ventas): conserva el tipo del módulo al emitir`, `fix(ventas): ajusta la presentación de las notas de venta`, `feat(clientes): permite registrar clientes desde la venta`. No confirmar OpenAPI o modelos sin su API correspondiente.

No desplegar ni publicar desde este plan. Entrega local incluye evidencias, documentos actualizados y verificaciones no realizadas. Ante una regresión, revertir cambios de aplicación documentados sin editar migraciones aplicadas ni borrar datos. Una reversión escrita no es una recuperación ensayada.
