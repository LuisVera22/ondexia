# Sensibilidad de la puerta del análisis de dependencias

ACT-19/F20/F23/F24. Base develop `9681a1e0cfd19366603a784423c50edbe9098898`.
La puerta de producción existe en deploy.yml; no se duplica ni se cambia la
política vigente que permite omitir el análisis en desarrollo con aviso.

Plan: ejecutar localmente el cuerpo real de los pasos Backend y Verificar el
backend, extraídos de los workflows. Resolver únicamente inputs.entorno para
dev/prod; ejecutar Bash con salida ante error y pipefail, como el runner. Maven
se sustituye por un ejecutable temporal sin red que registra argumentos y
devuelve un código prefijado. Entorno mínimo y credencial exclusivamente ficticia.
Las pruebas no invocan Maven real, NVD, Terraform ni AWS.

Reglas y oráculos independientes del resultado observado:

| Condición | Resultado exigido |
|---|---|
| CD prod sin clave | Error visible, código no cero, Maven no invocado |
| CD dev sin clave | Aviso de omisión, verify con skip explícito; no declarar análisis completo |
| CI sin clave | Aviso de omisión, verify con skip explícito |
| CI de rama con clave y análisis aplazado | Aviso de aplazamiento, verify con skip |
| CI con análisis requerido o CD con clave | verify con clave ficticia, sin skip |
| Maven/escáner falla | Paso devuelve el fallo y no permite continuar; nunca convierte fallo en éxito |
| Camino válido con clave | Paso correcto, sin aviso de omisión/aplazamiento |

Mutaciones: retirar el exit de producción sin clave; ocultar el código de fallo
de Maven en CI y en CD. Ejecutarlas por separado, restaurar bytes exactos y
comprobar pruebas correctas después. No son defectos pendientes del código real.

Los comentarios que describen estos controles enlazan la nueva prueba.
Actualizar backlog existente y evidencia, publicar PR separado y comprobar
CI del mismo head y del commit integrado antes de cerrar su rama.

Límites: no acredita vigencia/cobertura de datos NVD, detección de una CVE por
el escáner real, configuración efectiva del runner o despliegue AWS. El CI real
se contrasta por jobs/logs y Analysis Complete; un build verde con skip no
acredita SCA. Sin cambios de API, importes, migraciones, interfaz o reglas comerciales.
