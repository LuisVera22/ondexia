# Evidencia ACT-15 · F17/F18 — identidad del resultado de emisión

Base develop `99ddbabd2aa9eea4bc0f4baea035df5626aa6a26`.
[Especificación](../especificaciones/2026-10-11-identidad-resultados-emision.md),
[registro y hashes](2026-10-11-identidad-resultados-emision.json).

## Rojo y verde

Sobre el agregado original: 21 casos, 18 fallos y cero errores. Empresa ajena o
nula, operación VERIFICAR_CREDENCIALES/ENVIAR_BAJA/CONSULTAR_TICKET/nula, cruzadas
con ACEPTADO/RECHAZADO/ERROR_ENVIO, no producen el rechazo técnico esperado.
Los tres resultados propios mantienen transiciones válidas. No son 18 riesgos
independientes: faltaban dos verificaciones de identidad.

Sobre las entradas originales de aplicación: cuatro casos, cuatro fallos,
consulta/directa y empresa/operación ajenas. El agregado puede aceptar y el caso
de uso continúa hacia el documento/persistencia. Dobles de puertos, no base real.
El primer intento de Mockito fue bloqueado al adjuntar el agente en el sandbox;
se repitió con permiso local. Ese bloqueo no cuenta como defecto de producto.

Tras validar empresa y operación antes de la transición: 29 casos de dominio
focales y cuatro de aplicación verdes. El agregado conserva los campos anteriores
ante un resultado incompatible; la aplicación no guarda el comprobante ni accede
al documento. No cambian contratos API ni reglas tributarias/reintentos.

## Sensibilidad y regresión

Se añadió el caso de ID distinto dentro de la misma empresa: distingue el
control de ID del nuevo control de empresa. Mutación aislada al omitir el control
de ID: un fallo. Otra mutación al permitir aplicar después del estado final:
un fallo en `aceptadoEsFinal`. Agregado restaurado byte por byte en ambos casos.

Resultado final: dominio completo 152, focales de dominio 30, aplicación cuatro y
arquitectura nueve, sin fallos/errores. El focal incluye las ocho pruebas anteriores
y 22 de identidad. Maven offline, SCA omitido localmente; CI del mismo head
pendiente al registrar y obligatorio antes de integrar.

## Límites y siguiente caracterización

Se corrigió el comentario que decía ignorar cualquier respuesta de intento
anterior: solo se ignora cuando el estado ya no está EN_COLA. No se implementa una
política temporal por inferencia. F17 sigue pendiente de rollback, poscommit,
duplicados, correlación por intento y respuestas tardías. ACT-15 sigue parcial.
Sin pruebas RLS, transacciones reales, AWS/SUNAT, autenticidad del productor,
firma/esquema/vinculación CDR ni propiedad de claves de archivos en este corte.
Las otras operaciones del bus requieren revisión propia. Esta evidencia de
implementación no es auditoría independiente ni explotación demostrada.
