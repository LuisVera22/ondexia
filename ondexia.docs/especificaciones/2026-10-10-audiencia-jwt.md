# ACT-13: audiencia JWT obligatoria en el perfil AWS

Estado: corregido y verificado localmente; CI/integración y auditoría pendientes. Base: develop,
`0df3f6d57a8b0dcece6f7667ce605c7fa307dc15`.
Fuente: CLAUDE.md (premisas verificables, configuración y pruebas) y
[backlog ACT-13](../investigacion/2026-10-10-requisitos-ux-seguridad/backlog.md).

## Plan y contrato

La configuración no debe permitir omitir la audiencia. Si el cliente Cognito
esperado es nulo, vacío o solo espacios, construir el bean de seguridad debe
fallar antes de aceptar peticiones. Es un error de configuración, no un rechazo
comercial. Mensaje esperado fijado antes de ejecutar:
`COGNITO_CLIENTE_ID debe identificar la aplicación esperada.`

1. Añadir regresión para nulo, vacío, espacio y tabulación/salto de línea, usando
   token sintético firmado por la clave confiable y emitido para otra aplicación.
2. Observar fallo sin arreglo por ausencia del rechazo de configuración.
3. Impedir construir la configuración inválida; retirar el camino permisivo y
   el comentario que se apoya sin evidencia en Terraform.
4. Comprobar la suite JWT: configuración válida y firma/emisor/uso/audiencia;
   ejecutar controles de arquitectura pertinentes y registrar resultado.

Los tipos access/id, RS256, issuer, caducidad y contrato HTTP permanecen como
están definidos. No modificar API/OpenAPI, migraciones ni datos.

## Flujos y aceptación

- Configuración válida: la aplicación construye el decodificador; token legítimo
  aceptado y token de cliente ajeno rechazado según reglas existentes.
- Cliente nulo/vacío/blanco: falla la configuración y no se llega a aceptar el
  token ajeno. Requiere corregir configuración antes de iniciar servicio.
- Omisión completa de propiedad: resolución Spring existente; verificar por
  separado si se evalúa arranque completo, sin darlo por probado por unidad.
- Cambiar JWKS/rotación, sesiones y permisos pertenece a ACT-14/15/18.

Datos: claves RSA generadas en pruebas, emisor ficticio y clientes sintéticos.
Esperados proceden del contrato de fallo cerrado, no de respuestas observadas.
No se demuestra exposición AWS, configuración desplegada ni aceptación SUNAT.
Auditoría independiente y CI de la corrección pendientes.

Resultado local y limitaciones: [evidencia](../evidencias/2026-10-10-audiencia-jwt.md).
