# F03: primer corte de precisión monetaria

Base: develop `f004f2a44f8d1fb22c71dd330ad8469132c14c15`.
Alcance: cálculo del total de línea en el mostrador y en el dominio. Es una
caracterización; no implementa F04 ni declara terminada F03.

## Regla y plan

CLAUDE.md exige precisión decimal en todas las capas. Documento 13 §4.3 define
cantidad × precio con IGV − descuento, con total en céntimos.
LineaDeVenta.calcular aplica HALF_UP; se caracteriza esta regla vigente, sin
introducir una regla fiscal nueva ni cambiar las políticas comerciales.

1. Fijar antes de ejecutar siete entradas decimales y resultados explícitos.
2. Contrastar los oráculos por separado con Decimal, sin usar resultados observados.
3. Ejecutar las funciones reales redondear/totalDeLinea de TypeScript y el cálculo
   real de dominio con las mismas entradas. Separar pérdida al representar la
   entrada y divergencia del total.
4. Verificar sensibilidad del dominio cambiando exclusivamente HALF_UP del total
   a HALF_DOWN. Restaurar el archivo byte por byte y repetir las pruebas.
5. Conservar resultados y límites en el backlog existente. La corrección integral
   requerirá contrato decimal y pruebas de recepción, formulario, pagos y envío.

## Ejemplos independientes

| Caso | Cantidad | Precio con IGV | Descuento | Total esperado |
|---|---:|---:|---:|---:|
| Dos bolsas | 2 | 32.50 | 0 | 65.00 |
| Décima | 3 | 0.10 | 0 | 0.30 |
| Medio céntimo | 1 | 10.075000 | 0 | 10.08 |
| Fracción | 0.5 | 20.150000 | 0 | 10.08 |
| Descuento | 1 | 20.075000 | 10 | 10.08 |
| Seis decimales | 5000 | 0.000001 | 0 | 0.01 |
| Importe grande | 1 | 123456789012.344999 | 0 | 123456789012.34 |

El importe grande cabe en NUMERIC(18,6). Eso no demuestra que todos los demás
límites del flujo de venta permitan emitirlo: este corte no ejecuta ese flujo.

## Aceptación y límites

El dominio conserva precio a seis decimales y total a dos, coincidiendo con los
esperados. Una mutación del redondeo debe producir fallos y la restauración pasar.
La interfaz debe coincidir con los mismos oráculos: cualquier divergencia es
deuda confirmada, no un resultado válido que incorporar como esperado.

El diagnóstico extrae por AST las dos funciones declaradas en el componente
y transpila su código real. Si dejan de existir, falla para exigir revisión.
No ejecuta constructor, señales, plantilla, navegador, HTTP ni almacenamiento.
Su salida 1 documenta divergencias; no se instala como puerta de CI mientras el
defecto continúe abierto. El CI verde de este lote no acredita precisión web.

Pendientes: importes/cantidades desde JSON y formularios, máximos admitidos por
API, pagos mixtos/vuelto, totales acumulados, IGV estimado y otras pantallas.
F04 debe mantener API/OpenAPI/modelos generados sincronizados y decidir cómo
representar decimales en interfaz; no cambiar redondeo comercial implícitamente.
No se afirma explotación, emisión fiscal incorrecta ni auditoría independiente.
