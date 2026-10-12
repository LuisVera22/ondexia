# F17 · Frontera de publicación de emisión

Base develop `824aeebb8425d750f43938ccd0ed1fc966b1b023`.
[Especificación previa](../especificaciones/2026-10-11-confirmacion-emision.md).
F17 sigue parcial; no se cierra recuperación ni correlación de intentos.

El código publicaba directamente al faltar sincronización. También admitía
registrar un callback sin transacción real. Ahora exige ambos contextos y falla
explícitamente sin entregar una orden si falta cualquiera.

`PublicacionTrasConfirmarIT`: PostgreSQL real, tabla temporal en conexión
exclusiva, rol de aplicación y bus simulado. Comprueba tres contextos inválidos,
publicación única después de commit, rollback por excepción o explícito, commit
rechazado por restricción diferida y propagación del fallo del bus con la fila
ya confirmada conservada. No modifica tablas de negocio ni invoca SUNAT.

| Ejecución | Resultado |
|---|---|
| Código anterior con las nuevas pruebas | 8 pruebas, 3 fallos de aserción, 0 errores |
| Con arreglo | 8 pruebas correctas, ninguna omitida |
| Mutación aislada: publicar en beforeCommit | 8 pruebas, 2 fallos: commit rechazado y fallo del bus |
| Restauración exacta y regresiones | 43 pruebas correctas, 0 errores, ninguna omitida |

Las pruebas fallaron antes de la corrección y pasaron después. Los otros cinco
comportamientos ya funcionaban; la mutación acredita sensibilidad de la frontera
beforeCommit/afterCommit, no otro defecto pendiente. Restauración comprobada con cmp.
El oráculo procede del contrato y de la especificación previa: cero entregas sin
commit y conservación de la fila cuando el bus falla después de commit.

Ejecución local, desde apps/backend, con el activador y settings del harness:

```sh
./mvnw -o -B -s /workspace/.ondexia-setup/maven.xml \
  -pl ondexia.api -am -Ddependency-check.skip=true \
  -Dtest=PublicacionTrasConfirmarIT -Dsurefire.failIfNoSpecifiedTests=false test
```

La regresión añade EmisionElectronicaIT, PuntoDeVentaIT,
FronteraResultadosEmisionTest, ArquitecturaTest y ExportarContratoIT al selector.
No hay diff del contrato. Los registros comprimidos son extractos de resultados
y aserciones, sin cuerpos HTTP ni credenciales; hashes en el JSON adjunto.

Límites: bus simulado, sin AWS/S3/SUNAT ni auditoría independiente. La tabla
temporal prueba persistencia JDBC ante fallo poscommit; no se provocó ese fallo
en la venta HTTP completa. No acredita entrega exactamente una vez, recuperación
automática, resultados tardíos ni correlación de intentos. SCA se omitió
explícitamente en local. CI del PR y de develop pendientes de comprobar y registrar
con sus commits exactos en el PR; un build con skip no acredita SCA.
