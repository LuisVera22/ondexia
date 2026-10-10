# F05: cliente actual y línea histórica

Base develop: `392bd6884a2d9d4fdb43551ae50e41a13f100453`.
[Especificación y literales](../especificaciones/2026-10-10-historia-comprobantes.md).

## Resultado local

`PuntoDeVentaIT.caracterizaClienteActualYLineaHistorica`: una prueba correcta.
Después de editar cliente, GET de la nota devuelve **Cliente después de emitir** y
**Dirección posterior**. La emisión devolvió **Cliente al emitir** y **Dirección al emitir**.
La línea sigue **Instalacion a domicilio**, precio **80.000000**, cantidad **1**,
total **80.00**, aunque el producto ahora indique **Servicio posterior** / **90**.
Se confirma dependencia de datos actuales del cliente; no se aprueba como política.

Mutación aislada de reconstrucción: sustituir descripción de detalle por
**Servicio posterior**. Una prueba falló (esperado **Instalacion a domicilio**).
Detectó la alteración en la comprobación inicial de emisión. Restaurado byte por
byte el adaptador, SHA-256 `61180fdfd2209a8041688137391aa38b0d671a5ba33fb68ec8e3752cfa5f54ff`.
La suite PuntoDeVentaIT pasó después de restaurar; recuento exacto en JSON.
Este lote caracteriza; no contiene arreglo y no finge un ciclo rojo/verde de F06.

Ejecución: Java 21, PostgreSQL 17 efímero de Testcontainers, perfil local.
`./mvnw -o -B -s /workspace/.ondexia-setup/maven.xml -pl ondexia.api -am
-Ddependency-check.skip=true -Dtest=PuntoDeVentaIT -Dsurefire.failIfNoSpecifiedTests=false test`.
Se quitaron variables de base externa. SCA se omitió explícitamente en local.
Dos intentos iniciales sin acceso al socket Docker fallaron por infraestructura;
no cuentan como detección funcional. El primer intento de edición falló por ruta
relativa incorrecta; se corrigió usando ruta absoluta antes de ejecutar la prueba.

## Emisor y límites

Inspección estática: DetalleDocumentoComponent.cargar consulta configuración de
empresa/establecimientos actuales; su plantilla los pasa a CabeceraComprobante.
RespuestaDocumento no contiene esos campos históricos. EmisionElectronica.construirOrden
crea datos de emisor y adquirente en la orden: eso no acredita retención o reimpresión
ni reconstrucción de XML después de editar datos maestros.

No verificados: impresión ejecutada, XML/CDR, otros tipos de documento, legado,
configuración AWS, suite completa del sistema y auditoría independiente. CI remoto
pendiente al registrar esta evidencia. F05 sigue parcial; F06 pendiente de decisión
sobre campos y tratamiento del legado. Sin cambios en API, migraciones o producción.

Logs comprimidos en `2026-10-10-historia-comprobantes/`; resultados en el JSON adjunto.
