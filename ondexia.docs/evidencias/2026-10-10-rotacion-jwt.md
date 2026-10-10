# ACT-14: evidencia de caracterización del JWKS inmutable

Fecha: 2026-10-10. Base develop `0df3f6d57a8b0dcece6f7667ce605c7fa307dc15`;
rama `feature/caracterizar-rotacion-jwt`. Ver
[contrato y alternativas](../especificaciones/2026-10-10-rotacion-jwt.md) y
[registro reproducible con hashes](2026-10-10-rotacion-jwt.json).

## Resultado y alcance

La instancia configurada con la clave anterior rechaza la nueva y conserva la
anterior. Al construir otra instancia con ambas claves públicas, acepta ambas;
la original sigue rechazando la nueva. La nueva instancia rechaza una firma ajena
incluso cuando su kid coincide con el de la clave nueva. Los pares y reclamaciones
son sintéticos, con resultados fijados en la especificación antes de ejecutar.

| Fase | Casos | Fallos | Errores | Omitidos | Salida |
|---|---|---|---|---|---|
| Caracterización y regresión inicial | 22 | 0 | 0 | 0 | 0 |
| Mutación: conservar solo primera clave del JWKS en producción | 3 | 0 | 1 | 0 | 1 |
| Restauración y regresión | 22 | 0 | 0 | 0 | 0 |

Los 22 casos son tres de RotacionDeClavesIT, diez de FirmaDelTokenIT y nueve de
ArquitecturaTest. Corresponden a esta base: **no incluyen** los cuatro casos de
ACT-13 que están en el PR #33 sin integrar. No reutilizar conteos de otra rama.

La mutación introduce un error de decodificación en la aceptación de la clave
nueva; Surefire lo cuenta como **error**, no como fallo de aserción. Demuestra
sensibilidad de ese escenario, no cobertura de todas las mutaciones de firma,
algoritmo o permisos. Código restaurado byte a byte antes de repetir las 22.

Se retiró la afirmación de que Cognito no rota por su cuenta del comentario de la
API y de api.tf; se enlaza la prueba local y se explicita recuperación no
verificada. La referencia oficial de AWS admite rotación. No se modifica lógica
de autenticación, API/OpenAPI, Angular, migraciones ni datos.

## Reproducción

Desde apps/backend, con Java 21.0.12.1 y la configuración de esta sesión:

```bash
source /workspace/.ondexia-setup/activar-develop.sh
./mvnw -o -B -s /workspace/.ondexia-setup/maven.xml \
  -pl ondexia.api -am -Ddependency-check.skip=true \
  -Dtest=RotacionDeClavesIT,FirmaDelTokenIT,ArquitecturaTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Para la mutación se ejecutó únicamente RotacionDeClavesIT. Logs originales
comprimidos, sin cambios de contenido:
[base](2026-10-10-rotacion-jwt/base.log.gz),
[mutación](2026-10-10-rotacion-jwt/mutacion.log.gz),
[restauración](2026-10-10-rotacion-jwt/restaurado.log.gz).

## Estado y deuda residual

ACT-14 **en curso**, no cerrada. Faltan decisión y ensayo del mecanismo operativo
de renovación, permisos, alarmas, tiempo de recuperación y costo. No se verifica
Cognito real, despliegue, SnapStart, panel interno, revocación ni AWS. El panel
contiene una premisa semejante pendiente de caracterización y reconciliación.
El ensayo local omite explícitamente Dependency-Check; no es SCA ni CI completo.
Auditoría independiente e integración pendientes. El rechazo por clave nueva es
una limitación reproducida del modelo inmutable, no una caída productiva probada
ni una corrección funcional de renovación automática.

## Reconciliación posterior con ACT-13

Después de integrar el cierre de audiencia en develop
`9e262d7fa9a63eacb8ffa3879b61323e97f8fb40`, se repitieron FirmaDelTokenIT,
RotacionDeClavesIT y ArquitecturaTest: 26 casos correctos (14 + 3 + 9).
El log combinado acompaña la evidencia original; no sustituye su ensayo de
mutación. Dependency-Check local se omitió; AWS y auditoría siguen pendientes.
