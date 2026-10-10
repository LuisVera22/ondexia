# F04 · precisión del mostrador: corrección y límites

Base develop `99a9a3dc8595cbd3534fe0472131fdaa0aebe8ee`.
[Especificación](../especificaciones/2026-10-10-decimales-mostrador.md).
[Resultados y hashes](2026-10-10-decimales-mostrador.json).

## Resultado comprobado

Las cuatro regresiones de total del componente fallaron sin el arreglo (19 casos,
cuatro fallos). La prueba HTTP del precio grande falló porque el JSON era numérico
(un caso, un fallo). Cantidad cero contra componente, plantilla, prueba y modelo
originales produjo un fallo entre 133 casos: sustituía cero por uno. La misma
prueba pasa tras restaurar la corrección. No se convierte un fallo de preparación
(pnpm sin TTY o rutas de trabajo incorrectas) en evidencia funcional.

Con el arreglo: 139 pruebas web en Chromium; 29 seleccionadas de backend;
525 pruebas completas de backend (130 dominio, 286 API, 41 administración,
41 consultas y 27 facturación), sin fallos ni omisiones. Compilación Angular de
producción correcta. Los siete oráculos F03, sin cambiar esperados, coinciden.

Para el comportamiento ya funcional de recepción de texto decimal, una mutación
truncó el monto del pago en el adaptador. La prueba de 39.90 con pago mixto falló:
esperaba 201 y recibió 400. Se restauró el archivo byte por byte y la ejecución
verde comprobó venta 39.90 y vuelto 20.10. La prueba conserva además rechazo de
cantidad con siete decimales; las pruebas anteriores siguen enviando números,
comprobando compatibilidad de entrada. La mutación no forma parte del cambio.

Se regeneró OpenAPI desde springdoc y almacen.modelos.ts desde ese contrato.
Una prueba exige salida string para precio y variantes escalares string/number
para cantidades, descuentos y pagos, evitando representar BigDecimal como objeto.
La verificación adicional de nulabilidad falla sobre OpenApiConfig del primer
commit: esperaba true para la variante textual opcional y recibía false (un caso,
un fallo). Se restaura la corrección byte por byte y pasan las dos pruebas del
contrato. La suite completa de 525 casos se repitió con la declaración corregida.
Solo una variante de oneOf admite null, con type string explícito como exige
OpenAPI 3.0; el CI debe confirmar que la regeneración no deja diferencias.

## Reproducción

Con Java 21, Docker local y dependencias disponibles, desde apps/backend:
`./mvnw -pl ondexia.api -am -Ddependency-check.skip=true -Dtest=ProductosIT,PuntoDeVentaIT,ExportarContratoIT,PrecisionMonetariaTest -Dsurefire.failIfNoSpecifiedTests=false test`.
Para la regresión completa se ejecutó `./mvnw test -Ddependency-check.skip=true`.
En esta nube se añadió `-o -B -s /workspace/.ondexia-setup/maven.xml` y se quitaron
ONDEXIA_PRUEBAS_BD_URL/USUARIO/CONTRASENA del proceso: solo bases efímeras.
Dependency-Check local omitido explícitamente; el CI tiene su puerta separada.

Desde apps/frontend/ondexia.web: `pnpm run test:ci` y `pnpm run build`.
Aquí se invocó el CLI instalado directamente con Node y Chromium mediante un
wrapper local `--no-sandbox`, necesario en el contenedor; no cambia producción.
El diagnóstico se ejecuta desde la raíz con
`node herramientas/caracterizar-precision-monetaria.mjs`.

`herramientas/comprobar-responsividad-ventas.py` con el servidor Angular de
desarrollo en 9100 y EVIDENCIA_MOVIL hacia un directorio de resultados comprobó
21 pantallas y ocho impresiones, sin desbordes, a 320/390/768/1440 píxeles.
Las API y sesión son sintéticas; no se permite registrar ni emitir realmente.
Se verifican campos textuales con inputmode decimal, coma decimal, importe y foco.
El [JSON visual](2026-10-10-decimales-mostrador/movil.json) conserva las medidas;
las capturas del mostrador de 320, 390 y 1440 píxeles están en el mismo directorio.

## Compatibilidad y pendientes

Precio y existencia de /almacen/productos/disponibles cambian de number a string
(existencia también null). Es un cambio incompatible de salida, documentado como
tal: API y frontend deben actualizarse coordinadamente. Entrada admite texto y
números anteriores; código, OpenAPI y modelos se confirman juntos.

CI del PR y CI posterior de develop pendientes al registrar esta evidencia.
Este registro local no se presenta como CI ni auditoría independiente.
Quedan recepción de totales de documento/consulta/reimpresión, cajas, notas de
crédito, otras pantallas y límites acumulados extremos: F03/F04 parciales.
Teclados físicos, SUNAT, AWS y auditoría independiente no verificados. No se
modificaron migraciones ni datos existentes, ni se afirma cerrada toda la deuda.
