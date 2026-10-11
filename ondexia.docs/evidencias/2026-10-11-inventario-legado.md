# F06 — evidencia de preparación de recuperación

Base develop `98d7d6f8c6e168eb48f4bfb76729b7024ef99fb7`.
[Especificación](../especificaciones/2026-10-11-inventario-legado.md) y
[procedimiento](../operacion-recuperacion-originales.md).

11 pruebas nuevas; 17 pruebas Python completas correctas. TDD inicial: siete
fallos de ocho pruebas contra un stub, seguido de implementación. No se presenta
como siete defectos del código anterior ni como recuperación de clientes reales.
Mutación aislada de autenticidad false→true: dos fallos; archivo restaurado y
comparado byte por byte; suite completa posterior correcta.

Oráculo independiente: bytes literales abc y SHA-256 conocido, sin obtener
esperados de los archivos inventariados. Fuentes temporales permanecen iguales;
enlaces y FIFO omitidos, exceso de tamaño sin truncamiento, JSON de credenciales
excluido. Error de lectura produce código fijo sin texto privado del sistema.
CLI con carpeta ausente falla y no emite un informe exitoso.

El CI existente descubre estas pruebas; no se cambia el workflow. CI pendiente
al registrar; no se repiten tests backend porque este corte no cambia backend,
API, web, dinero ni migraciones. Revisión del diff y git diff --check correctos.

Estado actual del entorno comprobado: sin identidad AWS configurada, sin CLI
AWS y sin S3 permitido por red. No se consulta una cuenta ni se habilitan permisos.
El propietario preguntó cuál fuente es adecuada; se recomendó acceso de lectura
al prefijo documentos/ y sus versiones, después respaldos/copias. No se ha
configurado ese acceso. Documentos reales, autenticidad, recuperación, PDF y
auditoría independiente pendientes. Una carpeta vacía no demuestra pérdida.

Registros comprimidos y SHA-256 en el JSON adjunto.
