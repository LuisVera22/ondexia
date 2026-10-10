# F04 · precisión decimal del mostrador, primer corte

Base: develop `99a9a3dc8595cbd3534fe0472131fdaa0aebe8ee`. Corrección técnica
priorizada en el backlog existente. No cambia política fiscal, precios ni redondeo.

## Problema, objetivo y límites

F03 confirmó cuatro totales incorrectos por representación binaria en la web.
El precio también podía perder cifras durante JSON.parse, antes de calcular.
Este corte conserva precio, existencia, cantidad, descuento y cobro como texto
hasta el servidor; calcula con coeficientes BigInt y escala entera. El servidor
continúa usando BigDecimal y las columnas NUMERIC existentes. No hay migración.

El dominio sigue determinando el precio real; la petición nunca lo envía. No se
acepta como autoridad el total calculado en la web. Se conserva HALF_UP, cantidad
y precio con hasta seis decimales, descuento y pagos en céntimos, conforme a
Documento 13 §4.3 y las restricciones actuales de la API. No se infiere una
política nueva de redondeo de la facturación.

## Caso de uso: preparar y cobrar una venta

Actor: cajero con permiso de emisión. Precondiciones: empresa y establecimiento
activos, caja seleccionada, catálogo disponible y series cargadas.

1. Busca un bien o servicio. La API devuelve precio y existencia como texto
   decimal sin notación exponencial; existencia puede ser null.
2. Agrega el producto. La web conserva el precio recibido y comienza con cantidad
   1 y descuento 0; repetir el producto suma una unidad exacta.
3. Ingresa cantidad mayor que cero, con hasta seis decimales y doce cifras
   enteras. Ingresa descuento no negativo, con hasta dos decimales.
4. La web multiplica cantidad por precio, resta el descuento y redondea el total
   de cada línea a céntimos con HALF_UP. Acumula las líneas sin aritmética binaria.
5. Indica pagos. En orden, el efectivo aplica hasta el importe pendiente y calcula
   vuelto; las otras formas aplican su importe y no permiten sobrepago.
6. Con «Resto», completa exactamente el importe pendiente. Al emitir, envía
   cantidades, descuentos y pagos como texto decimal; solo envía pagos positivos,
   conserva entregado cuando hay vuelto y no incluye precio ni total.
7. El servidor valida permisos, escala, límites y reglas del documento, calcula
   desde su catálogo y persiste en una transacción. Las reglas existentes de
   boleta, factura, stock y numeración siguen siendo del servidor.

Alternativas y errores:

- Coma decimal del teclado: se normaliza un separador a punto, sin pasar por Number.
  No se admite agrupación de miles ni separadores múltiples.
- Cantidad cero, negativa, vacía, exponencial o con más de seis decimales: se conserva
  la entrada y se bloquea emisión; no se cambia silenciosamente a 1.
- Descuento negativo, ambiguo o con más de dos decimales: se bloquea emisión.
  Vaciar descuento conserva la convención previa de descuento cero.
- Pago vacío: no aporta cobro. Pago inválido o con más de dos decimales: se bloquea.
- Falta cobrar, sobrepago no efectivo, descuento con total negativo o stock
  insuficiente: se explica el impedimento existente; no se emite.
- Fallo HTTP: se conserva la venta según el comportamiento existente. No se añade
  reintento automático ni se afirma resuelta la idempotencia.

Postcondición del cliente: petición decimal exacta preparada o emisión bloqueada
con explicación. Postcondición de servidor: venta válida persistida o error sin
venta parcial, conforme a las pruebas existentes. El mensaje y la consulta del
documento aún reciben totales numéricos del contrato anterior: quedan fuera del
corte corregido, y F04 no se cierra globalmente.

## Contrato y compatibilidad

Cambio incompatible de salida: GET /api/v1/almacen/productos/disponibles entrega
precio y existencia como string (existencia también null), antes number. Requiere
actualizar API y cliente coordinadamente; no se despliega desde este lote.
OpenAPI y almacen.modelos.ts se regeneran desde la aplicación real.

Entrada: cantidad, descuento, monto y entregado documentan oneOf string/number.
Los clientes nuevos envían texto; el servidor conserva aceptación de peticiones
numéricas anteriores. Los DTO de venta manuales reflejan ambas variantes. Los campos opcionales
descuento y entregado admiten null en una sola variante de oneOf, con type explícito
para cumplir OpenAPI 3.0; cantidad y monto siguen siendo obligatorios no nulos.
La variante textual documenta el formato canónico que produce la web; no pretende
inventariar todas las coerciones adicionales del deserializador de BigDecimal.

## Aceptación y trazabilidad

Los siete oráculos de [F03](2026-10-10-precision-monetaria.md) son literales fijados
antes del arreglo; no se derivan del resultado observado. Casos adicionales:
80 × 0.500000 − 0.10 = 39.90; tarjeta 10.00 y efectivo entregado 50.00 aplican
29.90 en efectivo y devuelven 20.10. Se verifica también rechazo de 0.5000001.

- Rojo: cuatro regresiones del componente y salida de precio grande de API;
  además cantidad cero contra el código original.
- Verde: componente real en Chromium, regla decimal, integración HTTP y base
  efímera, generación real de OpenAPI y modelos, y regresión de backend.
- Sensibilidad: truncar el monto de pago en el adaptador hace fallar la prueba
  HTTP ya funcional. Restauración byte por byte y nueva ejecución verde.
- UX: entradas textuales, coma decimal, foco y no desborde en 320/390/768/1440 px.
  API sintéticas; teclados de dispositivos físicos no verificados.

El diagnóstico AST se actualiza para cargar el auxiliar decimal real: sigue
usando los mismos siete esperados independientes. No sustituye pruebas de Angular.

[La evidencia](../evidencias/2026-10-10-decimales-mostrador.md) diferencia controles
locales, CI y limitaciones. Quedan otras pantallas, consulta/reimpresión, cajas,
notas de crédito, límites acumulados extremos y auditoría independiente. No se
prueba SUNAT, AWS ni datos de terceros; no se declara resuelta toda F03/F04.
