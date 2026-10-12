# F17 · Publicación después de confirmar

Base develop: `824aeebb8425d750f43938ccd0ed1fc966b1b023`.

Problema: publicarTrasConfirmar publica inmediatamente si no existe sincronización.
La sincronización por sí sola tampoco acredita una transacción real. El contrato
vigente (documento 14 §2.1) exige confirmar antes de entregar la orden al bus.

Plan acotado: exigir transacción real y sincronización activa; rechazar con
IllegalStateException y mensaje fijo en español si falta cualquiera. Conservar
afterCommit. No cambiar estados, importes, API, migraciones ni política de reintento.

| Precondición o flujo | Resultado exigido, definido antes de ejecutar |
|---|---|
| Sin transacción ni sincronización | Excepción; ninguna publicación |
| Solo sincronización | Excepción; ningún callback registrado ni publicación |
| Solo transacción declarada activa | Excepción; ninguna publicación |
| Transacción JDBC válida | Dentro de la transacción ninguna publicación; tras commit una sola y fila conservada |
| Excepción antes de confirmar | Rollback, ninguna fila conservada ni publicación |
| Rollback solicitado explícitamente | Ninguna fila conservada ni publicación |
| Commit rechazado por restricción diferida | Ninguna publicación ni fila conservada |
| Bus falla después del commit | Error propagado; fila ya confirmada permanece; no prometer rollback ni recuperación automática |

Pruebas con PostgreSQL real y tabla temporal por conexión, sin alterar tablas ni
datos existentes. Bus simulado: este ensayo no llama al Emisor ni a SUNAT.
Los casos negativos sin contexto deben fallar antes del arreglo y pasar después.
Mutación aislada: mover publicación a beforeCommit; debe detectar entrega anticipada
y restaurarse byte a byte. Después ejecutar regresiones de emisión, venta y arquitectura.

Actualizar documento 14 y backlog existente con evidencia y límites. Abrir PR
hacia develop y comprobar CI completo del head y del commit integrado antes de
eliminar exclusivamente su rama. F17 permanece parcial: outbox, recuperación
automática, resultado tardío y correlación de intentos requieren bloques propios.
