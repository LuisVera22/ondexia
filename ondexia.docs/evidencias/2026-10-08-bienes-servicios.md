# Bienes y servicios: implementación y evidencia

Base: `develop`, commit `538dffd8eb1f7e84a1bf8b206f3b9ecbc30d122b`. Reglas aprobadas por el propietario en esta tarea.

## Comportamiento

- `Producto.tipo` es obligatorio: `BIEN` o `SERVICIO`. `controlaStock` queda como proyección para los consumidores existentes del mostrador y se comprueba su coherencia en PostgreSQL.
- La V26 rellena desde `controla_stock`, conserva unidades e importes anteriores y no deja un valor por omisión para `tipo`. El relleno alcanza a las empresas sin contexto activo; RLS y FORCE RLS quedan habilitados.
- Servicios: solo ZZ, HUR y DAY; ZZ preseleccionada. Bienes: ZZ excluida. La validación también existe en el dominio: modificar una petición fuera del formulario no permite saltársela.
- SERVICIO → BIEN permitido sin existencias iniciales. BIEN → SERVICIO permitido solamente sin filas de stock ni movimientos. Se rechaza con `bien_con_historia_de_stock`, asociado a `tipo`. Un saldo cero con movimientos también se rechaza.
- Conversión y movimientos comparten el bloqueo del producto. Los disparadores protegen además escrituras directas de stock y movimientos; una carrera no puede dejar un servicio con almacén.
- Dos entradas, listas y formularios: `/almacen/bienes` y `/almacen/servicios`. Comparten componentes de presentación con configuración de tipo. El servicio no expone ajustes, almacenes ni la pestaña de existencias, y tampoco los consulta. El bien conserva el conteo en su ficha después del alta.
- El mostrador conserva catálogo mixto y las instantáneas históricas de descarga de sus líneas. Vender un servicio no crea movimientos ni existencias.
- Alta y edición reciben `tipo`, en lugar de un booleano editable. Los importes de la ficha y la disponibilidad viajan como texto decimal exacto y se procesan como `BigDecimal`/`NUMERIC(18,6)`. El formulario no convierte esos importes a `number`.
- `ExportarContratoIT` regenera OpenAPI y los modelos del catálogo usados por Angular. Las peticiones tienen nombres propios para evitar la colisión de `PeticionEdicion` con otros controladores. CI rechaza diferencias entre contrato/modelos regenerados y los confirmados. La generación incorporada abarca los modelos del catálogo; el transporte HTTP conserva el servicio Angular vigente.

## Pruebas con fallo observado

| Premisa | Evidencia sin corrección o con mutación | Evidencia con corrección |
| --- | --- | --- |
| Tipo explícito y lista separada | Las cinco pruebas iniciales de `TiposDeProductoIT` fallaron sobre el código base; faltaba `$.tipo` y aparecía el bien en la lista de servicios | Tipo persistido, lista filtrada y mostrador mixto |
| Conversión con movimientos | La petición que debía rechazarse devolvió 200 en el código base | Devuelve 400 y `bien_con_historia_de_stock`; incluye saldo cero, fila de stock sin movimientos y movimiento sin proyección |
| Formulario de servicio | Tres pruebas nuevas fallaron sobre la web base: NIU preseleccionada, cinco opciones en lugar de tres y consultas/campos de almacén | ZZ preseleccionada, ZZ/HUR/DAY, sin campos ni consultas de existencias |
| Unidad válida también en servidor | Mutación que omite la validación del dominio: falla `unidadesDeServicioAcotadasSinForzarZZ` al aceptar una unidad incompatible | Peticiones incompatibles rechazadas; horas y días aceptados |
| Venta de servicio sin descarga | Comportamiento preexistente. Mutación que incluye todas las líneas en la descarga: `notaDeVentaCompleta` espera 201 y obtiene 400 | Venta mixta por 145, bien de 10 a 8, servicio con cero filas de existencias y movimientos |
| Relleno de datos anteriores bajo FORCE RLS | Mutación que omite `NO FORCE`: el SQL real de V26 falla con valores nulos en `tipo` sobre el catálogo anterior con datos | Relleno de dos empresas, unidades/importes conservados y aislamiento restablecido |
| Conversión concurrente | Mutación que omite la comprobación de stock en PostgreSQL: falla `stockConfirmadoImpideConvertirAunqueNoTengaMovimientos` | Dos conexiones comprueban bloqueo y rechazo en ambos órdenes; también stock cero |
| Validación de aplicación con código propio | Mutación que salta la consulta del historial: fallan los casos de conversión, aunque el disparador conserva la integridad | Rechazo de negocio con el código esperado |
| Dos entradas del catálogo | Mutación que restaura la entrada única Productos: falla la prueba del menú | Bienes y Servicios con rutas y permiso explícitos |
| Importe exacto en formulario | Mutación que convierte a `Number`: falla el importe literal enviado por el formulario | Se envía `999999999999.123456` sin pérdida y se muestran sus seis decimales |

Las expectativas son literales e independientes de los resultados observados. Las mutaciones se ejecutaron una por una y se restauraron comprobando sus bytes originales. Un fallo de mutación no se presenta como defecto preexistente.

La prueba de relleno reconstruye la forma V25 del catálogo dentro de una transacción, aplica el recurso SQL real de V26 y revierte todo al terminar. El arranque desde cero, incluida V900, se comprueba por separado con Flyway durante las pruebas y en la API local. No se simula PostgreSQL con H2.

## Validación final y reproducción

- Backend completo: 501 casos, sin fallos, errores ni omitidos; dominio 123, API 269, panel 41, consultas 41, emisión 27.
- Web: 88 pruebas en Chromium; compilación de producción y comprobación de tono y forma correctas.
- API local: arranque con V26 y V900 sobre una base sintética recreada. Navegador real: dos listas y fichas de bien, servicio existente y servicio nuevo comprobadas contra esa API.
- V1–V25 conservadas byte por byte respecto a la base. OpenAPI y modelos del catálogo se confirman junto a la implementación.

Desde `apps/backend`, ejecutar `./mvnw verify` con Docker disponible. Para una base externa usar exclusivamente un servidor de pruebas desechable y las variables `ONDEXIA_PRUEBAS_BD_*` documentadas en `ServidorDePruebas`: la suite elimina sus bases y roles. Desde `apps/frontend/ondexia.web`, ejecutar `pnpm run test:ci` y `pnpm run build`.

El [registro de resultados y huellas](2026-10-08-bienes-servicios.json) conserva los fallos observados, mutaciones, conteos y huellas de migraciones y registros.

Los registros de esta ejecución y los programas de mutación quedan en `/workspace/.ondexia-setup/evidencia/bienes-servicios/`; `mutaciones.json` registra qué prueba detectó cada cambio y la huella de su fuente original.

**Base local de ejemplo:** se editó V900 por autorización expresa. Quien conserve la base local anterior debe ejecutar `docker compose down -v` y volver a levantarla. Esto elimina los datos de esa base local. V1–V25 no se modificaron.

**No verificado:** análisis NVD/Dependency-Check (la ejecución local usó `-Ddependency-check.skip=true`), ejecución del flujo completo en GitHub Actions, despliegue AWS, identidad Cognito real y emisión contra SUNAT real/beta. Las pruebas de emisión usan respuestas y credenciales de prueba; no acreditan emisión fiscal externa. La revisión de esta implementación no es una auditoría independiente.
