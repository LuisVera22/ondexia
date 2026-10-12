# Preparación de promoción productiva

Base develop `f63d04f0739d65288947f8168fe8d68e65db19e2`.
[Especificación](../especificaciones/2026-10-12-promocion-productiva.md),
[datos verificables](2026-10-12-promocion-productiva.json), procedimiento en README
infra. Se conserva la pila física dev con políticas prod; no se ejecuta AWS.

## Evidencia local

- Terraform 1.15.8: fmt, validate y cuatro escenarios con proveedores simulados.
- Sin la conservación de nombres y SSM, tres aserciones fallan: RDS, pool y rutas
  de parámetros. Con el arreglo pasan las mismas aserciones.
- DNS obligatorio, base privada y MFA personal se verifican por rechazo. Tres
  mutaciones válidas de DNS/MFA/protección de borrado son detectadas y restauradas.
- Cuarenta y nueve pruebas de herramientas, incluidas puertas del workflow real,
  estados vacíos/ajenos, segunda RDS, borrado/reemplazos en ambos órdenes, nueva
  pila, plan incompleto e identidad del pipeline. Dos mutaciones de plan son
  detectadas en copias temporales; el original queda intacto.
- Los mocks no usan credenciales AWS. Se fijan ARN y opciones de validación para
  evaluar el plan; los ajustes iniciales del harness no son fallos de negocio ni
  forman parte de la demostración rojo/verde.
- El lock conserva versiones y hashes previos; init añade hashes Linux de los
  mismos proveedores. No es una actualización de dependencias.

Los logs comprimidos acompañan el JSON. No se ejecutan pruebas backend/frontend
adicionales locales: no cambian código ni contratos de aplicación. El CI completo
se contrasta separadamente por commit; esta evidencia local no acredita su éxito.

## Pendientes explícitos

No están verificados estado AWS, plan real, cambios sin reemplazo en toda la pila,
retiro OIDC dev, cuotas, credenciales, dominios/certificados efectivos, restauración
ni publicación. Conservar nombres no garantiza ausencia de reemplazos: la puerta
rechaza el plan si los hay. IAM del pipeline exige mantenimiento previo; no se
amplían sus permisos para que se cambie a sí mismo.

La contraseña maestra y datos demo requieren revisión antes de clientes. SUNAT
permanece bajo la configuración de cada empresa. No se declara la revisión
completa de seguridad ni una auditoría independiente.
