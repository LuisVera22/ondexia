# Ajustes de ventas: evidencia de implementación

2026-10-08, hora de Lima. Rama `develop`, base `6cd2f226ece51a871a14261c9a598c38546ba0af`.
Especificación: [requisitos v1.0](../especificaciones/2026-10-08-ajustes-ventas/requisitos.md), [diseño](../especificaciones/2026-10-08-ajustes-ventas/diseno.md), [tareas](../especificaciones/2026-10-08-ajustes-ventas/tareas.md).
Luis confirmó DEC-01/02/03 y autorizó implementar todo lo definido. No se utilizó el ZIP enviado por error.

## Resultado

- Nueva emisión desde notas/boletas/facturas conserva el tipo del módulo, sin selector alternativo; el mostrador general mantiene el selector. Rutas directas y navegación entre ellas están probadas; factura deshabilitada se bloquea sin convertirse en nota.
- Nota de venta muestra «Importe de venta» y «Total», con el total persistido; no desglosa IGV ni altera cálculos/canje. La leyenda acordada está al pie de ticket y A4. Boletas y facturas conservan desglose fiscal.
- «Nuevo cliente» consulta RUC, permite revisar datos y exige «Registrar y usar» antes de registrar/seleccionar. Conserva venta preparada. Maneja duplicado concurrente, cliente inactivo, ausencia de permiso, revocación, doble pulsación, cancelación y respuestas tardías. La ficha existente comparte la preparación de alta/atestación.
- Se reprodujo y corrigió que el marco de navegación apareciera al imprimir. Los estilos globales ocultan menú, barra y avisos, y eliminan el margen lateral del contenido en modo impresión. No cambia la visualización normal.

## Fallo antes del arreglo y resultado posterior

Registros originales en `/workspace/.ondexia-setup/evidencia/ajustes-ventas/`; sus hashes están en [el JSON](2026-10-08-ajustes-ventas.json). Paquete de registros y comprobación de navegador: `/workspace/evidencia-ajustes-ventas.tar.gz`, con checksum contiguo.

| Comportamiento | Evidencia roja válida | Evidencia verde |
|---|---|---|
| Tipo fijo y factura deshabilitada | `01-tipo-rojo-corregido.log`: 4 fallos con componente original; se permitía cambiar el tipo o comenzaba en NV | `03-nota-rojo.log` ya tiene estos casos correctos; `11-final-verde.log` confirma regresión |
| Resumen/leyenda de NV | `03-nota-rojo.log`: 3 fallos, ticket/A4/mixtos aún mostraban base e IGV | `04-cliente-rojo.log` confirma estos casos correctos; final verde |
| Entrada de alta contextual | `04-cliente-rojo.log`: falta el botón Nuevo cliente | `05-integracion-verde.log`, casos posteriores de alta y final verde |
| Cambio de empresa | `07-contexto-rojo.log`: permanece la venta/alta anterior | `08-contexto-verde.log` y final verde |
| Rutas directas del módulo | `09-rutas-rojo.log`: rutas originales no resuelven nueva nota | Final verde con RouterTestingHarness y rutas reales |
| Marco de navegación al imprimir | `12-impresion-rojo.log`: menú/barra visibles en emulación print | `13-impresion-verde.log`: comprobación de CSS y contenido ticket/A4 correcta |

Los primeros intentos de preparar pruebas tuvieron un error de proveedor TestBed y un fixture incompleto; esos fallos se descartaron y **no** se cuentan como evidencia roja del comportamiento. Se conservaron las ejecuciones corregidas. Las pruebas de rutas/hoja y el botón fallaron sin la corrección; las pruebas nuevas de alta se complementaron con mutaciones y casos adversos, sin simular que todas existían antes de la capacidad.

Oráculos independientes: ejemplo del socio 3×150.00=450.00; fiscal 450/1.18 redondeado=381.36 y diferencia=68.64; fixture mixto 100+18+50+32=200 con descuento ya aplicado. No se obtuvieron los esperados de la pantalla observada.

## Regresión y sensibilidad

- Web: **115 pruebas correctas**, sin fallos. Incluye las 88 previas y 27 añadidas.
- Backend: **501 casos correctos**, sin errores ni omitidos: dominio 123, API 269, panel 41, consultas 41 y facturación 27. El script repite dominio en dos reactores; se cuentan una sola vez por módulo, no como 624 pruebas diferentes.
- Compilación web y control de tono/forma correctos.
- Cuatro mutaciones aisladas detectadas y restauradas: firma de otro RUC, resumen interno aplicado a documento fiscal, permiso de registro ignorado y respuesta de empresa anterior aceptada. Cada ejecución falló por aserciones, no por compilación. Resultado final verde después de restaurar.
- Backend, OpenAPI y modelos/servicios de API Angular comparados byte a byte con la base: sin cambios. No se editaron migraciones, incluida la semilla. No se emitieron documentos hacia SUNAT.

## Revisión en navegador

Identidad sintética y contexto de API local; caja abierta, consulta RUC, búsqueda/alta de cliente y nota respondidas mediante interceptación con fixtures. Las llamadas de alta no llegaron al servidor y no se escribieron clientes/ventas reales. No prueba disponibilidad ni respuesta de proveedor real.

Se revisaron entradas directas de los tres módulos, selector del mostrador general, consulta→revisión→registro simulado→selección, hoja ticket/A4, leyenda y ausencia del marco al imprimir. Capturas de la hoja en emulación print, no fotografías de papel:

- [Revisión del cliente](2026-10-08-ajustes-ventas/alta-revision.png).
- [Hoja ticket](2026-10-08-ajustes-ventas/nota-ticket.png).
- [Hoja A4](2026-10-08-ajustes-ventas/nota-a4.png).

La misma plantilla se aplica al reimprimir notas históricas; sus datos persistidos no cambian. La simulación utiliza una nota emitida para verificar esa representación.

## Límites explícitos

La auditoría en sesión independiente está pendiente: este informe es del implementador y no debe precargarse como narrativa de la auditoría. Entregar especificación, decisiones, commit/diff y evidencias para contraste.

No se verificaron proveedor externo real, nueva normativa externa, impresora física/paginación completa, producción, rentabilidad ni adopción. No se afirma cumplimiento decimal general del frontend heredado: mantiene contratos numéricos y aritmética previa con `number`; este cambio no añade aritmética monetaria ni modifica cálculo de dominio BigDecimal/NUMERIC(18,6). La representación nueva consume el total autoritativo, sin calcular un nuevo importe.

Sin despliegue ni publicación remota. La implementación y las verificaciones locales quedan disponibles para revisión.
