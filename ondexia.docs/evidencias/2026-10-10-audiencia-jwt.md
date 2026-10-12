# ACT-13: evidencia de configuración JWT con fallo cerrado

Base develop: `0df3f6d57a8b0dcece6f7667ce605c7fa307dc15`.
Rama: `feature/cerrar-audiencia-jwt`.
[Especificación y esperados previos](../especificaciones/2026-10-10-audiencia-jwt.md).
[Resultado estructurado](2026-10-10-audiencia-jwt.json).

## Propiedad y cambio

Sin cliente Cognito esperado, el perfil AWS no puede validar audiencia y debe
fallar al construir su configuración. Cuatro casos: nulo, vacío, espacio y
tabulación/salto de línea. Token firmado por la clave confiable, cliente ajeno.
La regresión exige fallo de configuración; sin arreglo, el token se acepta.
Se valida en el constructor y se retira la omisión permisiva del validador.

| Etapa | Casos | Fallos | Errores | Omitidos | Salida |
|---|---|---|---|---|---|
| Regresión sin arreglo | 14 | 4 | 0 | 0 | 1 |
| Arreglo + JWT + arquitectura | 23 | 0 | 0 | 0 | 0 |
| Mutación: desactivar rechazo de audiencia | 14 | 2 | 0 | 0 | 1 |
| Código restaurado + JWT + arquitectura | 23 | 0 | 0 | 0 | 0 |

Los cuatro fallos iniciales son «no se lanzó excepción», no fallos de entorno.
La mutación la detectan los casos de token ajeno access/id; no se conserva en el
código de entrega. La restauración se contrastó byte a byte con el código que pasó en verde
y se repitieron las 23 pruebas antes de confirmar.

Comandos, entorno, hashes de código/artefactos y límites están en el JSON.
Logs conservados: [rojo](2026-10-10-audiencia-jwt/rojo.log.gz),
[verde](2026-10-10-audiencia-jwt/verde.log.gz),
[mutación](2026-10-10-audiencia-jwt/mutacion.log.gz),
[restauración](2026-10-10-audiencia-jwt/restaurado.log.gz).

## Alcance y límites

Solo suite JWT de API y nueve reglas ArchUnit, sin base ni AWS. No se afirma
suite completa del backend, conformidad ASVS, explotación ni auditoría independiente.
Dependency-Check fue omitido explícitamente en estos comandos focales; no hay
resultado local de SCA. OpenAPI, modelos Angular y migraciones no cambian.

Se prueba construcción del componente de seguridad y decodificación, no arranque
completo con base de datos ni ausencia total de propiedad en Spring. F01 sigue
en curso; ACT-14/15 y la revisión del resto del sistema permanecen pendientes.
CI, integración y revisión independiente de ACT-13 pendientes al registrar esta evidencia.
