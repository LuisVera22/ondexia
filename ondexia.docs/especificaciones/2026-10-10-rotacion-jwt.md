# ACT-14: caracterización de rotación de claves JWT

Estado: especificación de ensayo local; actualización automática pendiente de
decisión y ejecución. Base: develop `0df3f6d57a8b0dcece6f7667ce605c7fa307dc15`.
Rama: `feature/caracterizar-rotacion-jwt`. El PR #33 de ACT-13 permanece separado.

## Necesidad y contrato

Mantener autenticación válida cuando el emisor publique claves nuevas sin aceptar
firmas desconocidas para recuperar disponibilidad. AWS advierte que Cognito puede
rotar claves y recomienda renovar su caché periódicamente y ante un kid nuevo:
[fuente oficial consultada el 2026-10-10](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-verifying-a-jwt.html).
No se presume que haya ocurrido una caída real ni se fija un tiempo de recuperación
sin datos y decisión del propietario.

Hoy Terraform obtiene el JWKS al aplicar y lo inyecta en COGNITO_JWKS; la API
construye un selector inmutable sin red. Este ensayo caracteriza ese contrato,
retira la premisa documental de que Cognito no rota por su cuenta y prepara la
decisión de recuperación. No incorpora conectividad ni ejecuta Terraform.

## Flujo y extensiones

Actor: usuario con token; verificador: API del perfil aws. Configuración confiable:
emisor, cliente y claves públicas obtenidas por el proceso de configuración.

1. El proceso construye el decodificador con la clave pública anterior.
2. Llega un token de acceso firmado por la clave anterior y se acepta si sus
   restantes reclamaciones son válidas.
3. El emisor comienza a firmar con otra clave, distinta de la configurada.
4. El mismo decodificador rechaza ese token, también en el segundo intento;
   continúa aceptando la clave anterior. No se omite la firma por indisponibilidad.
5. Un proceso autorizado configura otro decodificador con ambas claves públicas.
6. La nueva instancia acepta ambas firmas; la anterior conserva su estado.
7. Una firma ajena se rechaza incluso con el mismo kid de una clave publicada.

Si no se puede obtener configuración confiable, no aceptar material procedente del
token ni de URL indicadas por él. Renovar claves no sustituye comprobar firma,
algoritmo, emisor, caducidad, uso y audiencia. Fallo del proveedor y rechazo de
autenticación requieren diagnóstico distinto, sin registrar tokens o secretos.

## Oráculos y plan de comprobación

Tres pares RSA generados localmente, sin relación con Cognito ni usuarios reales.
La tercera clave comparte kid con la nueva, pero tiene otro material RSA. Se fijan
antes de ejecutar los resultados: rechazar nueva con conjunto anterior, aceptar
ambas en conjunto actualizado y rechazar firma ajena. El sujeto esperado se fija
en `usuario-controlado`, no se extrae de una respuesta observada.

Ejecutar RotacionDeClavesIT, FirmaDelTokenIT y ArquitecturaTest. Mutar únicamente
el selector de producción para conservar solo la primera clave del conjunto:
debe fallar la aceptación de la firma nueva del conjunto actualizado.
Restaurar byte a byte y repetir. Es sensibilidad de una caracterización, no una
prueba roja/verde de una recuperación automática implementada.

## Alternativas de recuperación a evaluar

| Alternativa | Ventaja | Costo y límite que hay que comprobar |
|---|---|---|
| Actualización controlada del JWKS y nueva versión de Lambda | Reutiliza el diseño sin red de las Lambdas | Tiempo de detección/aplicación y exposición a interrupción; ensayo completo pendiente |
| Proceso externo periódico que distribuye claves confiables | Evita depender de intervención manual en cada cambio | Permisos separados, almacenamiento, frecuencia, alarmas y entrega/lectura todavía no diseñados ni presupuestados |
| Consulta HTTPS desde el verificador con caché y renovación acotada | Recuperación ante kid nuevo en ejecución | Conectividad efectiva, costo de red, límites ante abuso, tiempos, SnapStart y caché de claves; no asumir que un endpoint permite acceder al JWKS |

Recomendación provisional: comparar primero la actualización controlada y la
distribución externa reutilizando recursos existentes. No adoptar NAT ni otro
servicio por defecto. La decisión necesita tolerancia a interrupción, frecuencia,
presupuesto, permisos y ensayo de recuperación; estos datos siguen pendientes.

## Límites y criterio de cierre

El ensayo no demuestra Cognito real, rutas HTTP, API Gateway, panel interno,
revocación, SnapStart, IAM, alarmas ni recuperación desplegada. Tampoco cierra
ACT-14: falta seleccionar y verificar el mecanismo operativo, documentar tiempos
y comprobar controles negativos y costo. Auditoría independiente pendiente.
