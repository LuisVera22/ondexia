# Promoción del único entorno AWS

## Decisión y alcance

El propietario aprobó desarrollo local y un único entorno AWS productivo. Se
prepara la promoción de la pila existente, sin crear otra RDS, borrar datos ni
activar emisión real hacia SUNAT. No equivale a autorizar un `apply` sin plan.

`entorno = prod` selecciona políticas, perfiles y permisos productivos;
`entorno_recursos = dev` conserva nombres y parámetros SSM existentes. El estado
productivo debe recibir la titularidad del estado anterior mediante una operación
controlada, con despliegues detenidos y respaldo. No se comparten estados activos.

## Flujo y extensiones

1. Publicar el código validado siguiendo GitFlow; producción se aplica desde main.
2. Un operador autorizado inventaría recursos, datos, estado y configuración real.
3. Respalda estado y datos; verifica el destino vacío y detiene los despliegues.
4. Migra la titularidad a `ondexia/prod/terraform.tfstate`, verifica contenido e
   inutiliza el estado anterior como destino activo. No copiar y dejar dos dueños.
5. Planifica usando prod.tfvars. La puerta local rechaza estado vacío/incompatible,
   destrucciones, reemplazos y creación de una segunda pila de datos/aplicación.
6. Prepara DNS por etapas: zona y solicitudes de certificados, delegación desde
   Namecheap, validación y plan completo. No esperar validación sin delegación.
7. Revisa el plan completo, aprueba y aplica controles, migra esquema y publica.
8. Comprueba efectividad: protección/respaldos, identidad, dominios, aislamiento,
   originales y recuperación; `apply` verde no prueba restauración ni SUNAT.

Estado ausente, identificador diferente, recursos faltantes o plan destructivo:
rechazar sin aplicar ni migrar esquema. Una modificación legítima que requiera
reemplazo necesita su propio plan; no quitar la puerta para continuar.

## Aceptación y límites

- Nombres RDS/S3/Cognito/Lambda y rutas SSM conservados con política prod.
- Respaldos siete días, logs treinta, protección RDS/Cognito y copia final.
- Sin retornos localhost, con MFA personal; separación de roles de lectura/escritura.
- DNS raíz, www, app, api, panel y cdn; certificados con región adecuada.
- Sin despliegue dev por este workflow; aplicación prod solo desde main.
- Cuota de concurrencia y credenciales se comprueban en AWS antes de aplicar.
- Pruebas de Terraform usan proveedores simulados y no acreditan un plan AWS.
- No se presume que los datos existentes sean descartables. Usuarios/datos demo
  requieren inventario y decisión explícita; no borrarlos automáticamente.
- Pendiente externo: estado real, cuota, secretos, respaldos/restauración, revisión
  del plan AWS, aprobación y publicación. No se cambia el modo SUNAT por empresa.
