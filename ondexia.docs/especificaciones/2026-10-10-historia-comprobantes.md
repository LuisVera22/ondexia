# F05: primer corte de conservación histórica

Base: develop `392bd6884a2d9d4fdb43551ae50e41a13f100453`.
Alcance: nota de venta consultada por HTTP después de editar al cliente y cambiar
el producto en PostgreSQL efímero. Caracterización autorizada, sin corrección F06.

## Fuente, ejemplos y flujo

Documento 13 §4 declara el documento inmutable salvo estado. Las filas están
protegidas, pero una referencia a datos maestros puede cambiar su representación.
F05 exige identificar campos y decisiones antes de F06; no se inventa la política
para documentos anteriores. La obligación de conservar logos ya está aprobada.

Datos fijados antes de ejecutar:

| Campo | Al emitir | Maestro después | Resultado a contrastar |
|---|---|---|---|
| Cliente, nombre | Cliente al emitir | Cliente después de emitir | Determinar si consulta maestro o conserva instantánea |
| Cliente, dirección | Dirección al emitir | Dirección posterior | Determinar si consulta maestro o conserva instantánea |
| Producto, descripción | Instalacion a domicilio | Servicio posterior | Mantener Instalacion a domicilio en la línea |
| Precio con IGV | 80.000000 | 90.000000 | Mantener 80.000000 en la línea |
| Cantidad / total | 1 / 80.00 | — | Mantener 1 / 80.00 |

1. Registrar cliente propio con DNI 90112233 y datos literales.
2. Abrir caja nueva y emitir una nota con una unidad del servicio de la semilla.
3. Comprobar datos iniciales por HTTP.
4. Editar nombre/dirección por API; cambiar nombre/precio del producto dentro de
   transacción con contexto de empresa, únicamente en la base efímera.
5. Consultar el mismo ID. Comparar con literales, sin obtener esperados de la respuesta.
6. Restituir producto de la semilla en `finally`; Testcontainers retira la base.
7. Sustituir aisladamente la descripción reconstruida por un literal posterior:
   la prueba debe detectar la alteración. Restaurar el archivo byte por byte.
8. Ejecutar suite pertinente y registrar límites, sin declarar cerrados F05/F06.

La aserción del cliente posterior caracteriza la dependencia actual, no aprueba
ese comportamiento como regla. Al adoptar instantáneas deberá reemplazarse por
una regresión de conservación basada en la decisión del propietario.

## Fronteras y decisiones

La API devuelve cliente reconstruido desde su repositorio actual. El contrato no
contiene instantánea del emisor/local. La interfaz pide configuración actual para
la cabecera; eso es inspección estática, no ensayo de impresión.

Pendiente del propietario: campos históricos de cliente/emisor/local, alcance por
tipo, y presentación del legado sin instantáneas. No reconstruir valores desconocidos
como si hubieran sido fijados al emitir. La investigación de XML/S3 y reintentos
queda pendiente. No cambiar migraciones, API, reglas fiscales ni AWS en este lote.
