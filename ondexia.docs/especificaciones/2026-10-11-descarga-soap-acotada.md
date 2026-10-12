# ACT-17 · F24 — recepción HTTP acotada

Base develop `fb90213416fc1bfc7d9ab5192fe23b990b8f4d03`.
Complementa la lectura CDR existente; no cambia reglas de SUNAT ni contratos API.

## Plan y criterios

1. Reproducir con servidor HTTP local la descarga sin límite y el cuerpo que
   permanece abierto después de las cabeceras. No consultar servicios externos.
2. Contar bytes recibidos antes de acumularlos: máximo 16 MiB, el mismo del
   lector SOAP. Aceptar el valor exacto; cancelar al recibir un byte adicional,
   incluso con transferencia por bloques y sin `Content-Length`.
3. Aplicar `tiempoDeEspera` hasta completar la recepción, además del timeout de
   la petición. Cancelar la operación pendiente ante vencimiento/interrupción.
4. Exceso: `SIN_RESPUESTA / RESPUESTA_DEMASIADO_GRANDE`, descripción fija, sin
   archivos originales parciales, CDR ni aceptación/rechazo tributario.
   Error de transporte/tiempo: `SIN_CONEXION`, descripción fija sin detalle
   procedente de excepciones. Interrupción: conservar `INTERRUMPIDO` y el flag.
5. Verificar ticket y SOAP original exactos, ambas operaciones restantes,
   redirección sin seguimiento y timeout. Mutar un control existente de
   redirección, restaurar byte por byte y ejecutar regresión del módulo y CI.

Oráculos: tamaños literales especificados, ticket sintético y bytes enviados por
el servidor, contador independiente del segundo destino y ventanas temporales
con margen. El servidor que excede el límite deja el cuerpo abierto: esperar su
fin no cumple el criterio. No se provoca agotamiento de memoria.

## Límites

No hay presupuesto global de CPU/memoria ni control de concurrencia; cabeceras,
solicitud saliente y DOM tienen costos propios. Los bytes ya entregados por el
JDK pueden existir antes de la cancelación. No se afirma límite del heap de
16 MiB. El tiempo acota la recepción, no la interpretación posterior del XML.
Pendientes: firma/vinculación/esquema de CDR, XML interno y auditoría
independiente. AWS/SUNAT y originales reales no se ensayan; según decisión del
propietario, la validación histórica real espera un caso en producción.
