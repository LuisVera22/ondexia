> Aplicar las [reglas aprobadas del harness](../README.md). Plantilla reutilizable; rellenarla no demuestra ejecución ni aprobación.

# Ficha de cambio verificable

Estado: propuesta / acordado / verificado / pendiente (seleccionar con evidencia).

- ID, objetivo y responsable:
- Repositorio, commit base y commit evaluado:
- Rama/base y destino según GitFlow; instrucción de sesión aplicable:
- Mensaje propuesto o confirmado según Conventional Commits, en español:
- Fuente de negocio, versión/fecha y decisión vigente:
- Decisiones pendientes y trabajo que depende de ellas:
- Alcance, exclusiones, datos y riesgo:
- Método elegido (SDD/BDD/TDD u otro) y razón proporcional al cambio:

## Especificación

Contrato/ADR existente, invariantes, NFR, amenazas y controles. Registrar contradicciones y quién tiene autoridad para resolverlas. Un enlace a una propuesta no demuestra aprobación.

## Comportamientos y trazabilidad

| Requisito / amenaza y fuente | Dado / cuando / entonces | Control y prueba / archivo | Resultado, commit y evidencia | Pendiente / responsable |
|---|---|---|---|---|
| Completar con el caso real | Incluir límite o caso negativo pertinente | No inventar pruebas ejecutadas | Comando, target, exit code y artefacto actual | Dependencia concreta |

## Ejecución y revisión

- Si se usó TDD: fallo inicial por la propiedad esperada, verde y regresión tras refactorizar. Si no, razón y verificación pertinente.
- Pruebas ejecutadas/fallidas/omitidas/no ejecutadas y cantidad de casos:
- Verificaciones de configuración/proveedor externo y limitaciones:
- Revisión independiente: sesión/alcance, commit, hallazgos y respuesta basada en evidencia:
- Entrega/rollback y autorización operativa cuando corresponda:
- Resultado de negocio observado y seguimiento de mejora:

Durante onboarding esta ficha documenta trabajo y límites; no autoriza modificar archivos protegidos, desplegar, gastar ni emitir documentos fiscales.

## Criterios vigentes adicionales

- Caso de uso: validaciones con autoridad, aceptar/rechazar/indeterminado y extensiones por paso; estados, efectos y recuperación pertinentes.
- UX/UI: tareas y estados afectados, móvil/escritorio, teclado/foco y criterios WCAG aplicables; separar capturas de pruebas de uso reales.
- Seguridad: activo/frontera/amenaza, requisito versionado, configuración, prueba efectiva y pendientes; costo y riesgo residual.
- Correcciones: fallo sin arreglo y éxito con él. Control existente: mutación aislada detectada y restaurada. Esperados independientes.
- Evidencia: comando/entorno/commit/resultado; declarar no verificado lo que no se ejecutó.

Usar también los [formatos de caso de uso, pruebas, UX y seguridad](../../investigacion/2026-10-10-requisitos-ux-seguridad/plantillas.md).
