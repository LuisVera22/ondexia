# F17 · Duplicados y límites del reintento

Base develop `e7f003ed0e56fd24fd0e338032990ff8f2d29134`.

Plan: comprobar las reglas vigentes del documento 14 §3 y la conservación
documental, con fixtures fijos independientes de la salida del agregado. Este
lote no modifica transiciones, contratos, migraciones ni infraestructura.

| Caso o extensión | Resultado exigido |
|---|---|
| ACEPTADO, RECHAZADO o ERROR_ENVIO recibe otro resultado compatible de cualquiera de esos tres estados sin reencolar | Devuelve false; identidad, estado, intento, fechas y todos los datos de la primera respuesta permanecen iguales |
| ACEPTADO o ANULADO solicita reintento | reintento_no_admitido; ningún campo cambia |
| EN_COLA, un nanosegundo antes de diez minutos | emision_en_curso; contador y fecha intactos |
| EN_COLA, exactamente diez minutos o un nanosegundo después | Vuelve a encolar, incrementa una sola vez y fija la fecha del nuevo intento |
| Reencolado al vencer la espera, seguido de otra solicitud un segundo después | emision_en_curso; la espera parte del último encolado |
| RECHAZADO o ERROR_ENVIO reintenta sin esperar diez minutos | Permitido; mismo documento/empresa/serie/número; conserva XML y resumen, limpia fecha de respuesta y CDR vigente |

Los controles ya funcionan según la inspección; ejecutar primero las pruebas sin
cambiar producción. Probar sensibilidad mediante tres mutaciones aisladas:
retirar rechazo de resultados fuera de EN_COLA, cambiar el límite de espera para
rechazar también el instante exacto y permitir reintento de estados finales.
Restaurar bytes exactos entre mutaciones y ejecutar regresión después.

Corregir la afirmación del documento 14: no hay identificación de intento en el
resultado. Volver a EN_COLA permite que se aplique una respuesta antigua
compatible; el bloqueo fuera de EN_COLA no demuestra correlación. No escribir
un test que convierta esa limitación en política aprobada. Quedan pendientes
especificación de correlación/conciliación, recuperación y concurrencia real.

Actualización del backlog y evidencia existente, PR a develop, CI completo del
head y del commit integrado antes de eliminar exclusivamente su rama. No hay
llamadas a SUNAT ni simulación de auditoría independiente.
