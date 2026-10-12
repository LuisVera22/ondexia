# F03: divergencias monetarias reproducibles

Base `f004f2a44f8d1fb22c71dd330ad8469132c14c15`. Primer corte de F03;
[especificación y oráculos](../especificaciones/2026-10-10-precision-monetaria.md).

## Resultado

Se fijaron siete resultados decimales literales, contrastados por separado con
Python Decimal y HALF_UP, sin derivarlos de los observados. El dominio cumple
los siete; DocumentoVentaTest mantiene sus seis casos correctos.

La ejecución de las funciones reales de interfaz encuentra cuatro divergencias:

| Caso | Esperado | Interfaz |
|---|---:|---:|
| 1 × 10.075000 | 10.08 | 10.07 |
| 0.5 × 20.150000 | 10.08 | 10.07 |
| 20.075000 − 10 | 10.08 | 10.07 |
| 1 × 123456789012.344999 | 123456789012.34 | 123456789012.35 |

No se corrigió el cálculo de producción. Number.EPSILON no garantiza redondeo
decimal ni conserva las entradas grandes a seis decimales. El servidor recalcula
precios/totales; eso no elimina la divergencia de lo anticipado al cajero.
No se ejecutó una venta HTTP con esos casos: no se afirma su resultado completo.

## Reproducción

Con las dependencias web instaladas y Node disponible, desde la raíz:
`node herramientas/caracterizar-precision-monetaria.mjs`.
Sale 1 por cuatro divergencias; JSON con entradas, esperados y observados.
No calcula un esperado a partir de la salida actual de Angular.

Desde apps/backend, con Java 21 y Maven configurados:
`./mvnw -pl ondexia.domain -am -Ddependency-check.skip=true -Dtest=PrecisionMonetariaTest,DocumentoVentaTest -Dsurefire.failIfNoSpecifiedTests=false test`.
Aquí se usó además modo offline, batch y settings de /workspace/.ondexia-setup/maven.xml.
Dependency-Check local omitido explícitamente; no hubo conexión a base ni AWS.

Los logs originales y el JSON del diagnóstico acompañan esta evidencia. La
mutación cambia solo el redondeo del total a HALF_DOWN, en el worktree aislado.
No se integra la mutación. Las cuentas verificadas y hashes se registran en JSON.

## Pendiente

F03 parcial, F04 abierto: recepción y envío decimales, pagos mixtos, vuelto,
acumulación, límites API/BD y compatibilidad contractual. Sin conformidad fiscal,
revisión independiente, pruebas UX/navegador ni análisis SCA local demostrado.
El control local de tono/forma no pudo evaluar los sitios: faltan los directorios
dist de web y landing en este worktree. Su mensaje «no cumple» no se registra
como validación correcta; el CI ejecutará construcción y control completos.
CI del PR e integración de este lote pendientes al registrar la evidencia.

## Continuidad de F04

Las instrucciones y resultados anteriores corresponden al primer corte histórico
de F03. El [primer corte F04](2026-10-10-decimales-mostrador.md) corrige el mostrador
y actualiza el diagnóstico para cargar el auxiliar decimal real: en esa revisión
los siete oráculos coinciden y la salida es 0. Para reproducir las divergencias
originales, consultar la revisión del PR #37, no el script posterior corregido.
Los archivos originales de evidencia se conservan.
