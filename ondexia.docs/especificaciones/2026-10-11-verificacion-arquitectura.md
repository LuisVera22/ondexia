# Detección de dependencias en excepciones capturadas

Lote de ACT-23/F20: adaptar la propuesta Dependabot #13 a develop. Base:
`df050e87da8e4573c1caf87552ef1c8ab400798e`.

## Problema y criterio de aceptación

Una dependencia tecnológica puede aparecer exclusivamente en un bloque `catch`.
El comprobador arquitectónico debe incluirla entre las dependencias del bytecode;
de lo contrario, una regla de frontera puede pasar pese a esa dependencia.
ArchUnit 1.5.1 anuncia la corrección en sus notas oficiales:
<https://github.com/TNG/ArchUnit/releases/tag/v1.5.1>.

Crear una clase sintética que capture `java.sql.SQLException` al invocar una
operación cuyo contrato declara solo `Exception`. Sin campos, firmas ni
construcciones que referencien SQLException: el único origen es el `catch`.
El oráculo exige además que el tipo capturado esté completamente importado al
habilitar exclusivamente la resolución por accesos a tipos, con configuración
local al hilo. Es el defecto acotado del cambio oficial #1732. La referencia
directa ya existe en 1.5.0: no se atribuye al parche su creación.
Como controles, se exige la dependencia SQLException y que una regla que prohíba
`java.sql..` rechace esa clase. Una clase de control que captura solo Exception
debe ser aceptada por la misma regla. No se ejecuta SQL ni se arranca Spring.

Ejecutar antes con 1.5.0 y después con 1.5.1, mantener las reglas existentes y
ejecutar regresión backend. Actualizar únicamente la propiedad de ArchUnit y
añadir prueba/evidencia. API, contratos, datos y migraciones quedan fuera del lote.

## Límites y cierre

No prueba que exista esa violación en el código productivo, ni audita toda la
arquitectura. El CI completo del nuevo PR y del merge a develop debe pasar antes
de terminar su rama. La propuesta #13 podrá cerrarse como sustituida solo tras
verificar la actualización equivalente integrada y que su head no haya cambiado.
El aviso independiente de braces sigue pendiente: npm respondió 404 a 3.0.4
el 2026-10-11; esa ausencia no acredita ausencia de riesgo ni aceptación del aviso.
