# Acciones coordinadas y contrato de artefactos

Adaptación de #14/#16/#19/#21 desde develop
`94cf525552c867ce4b925baa4873060a9026f7b6`.
[Fuentes exactas](2026-10-11-cierre-acciones-fuentes.json) y
[especificación del cierre](../especificaciones/2026-10-11-cierre-dependabot.md).

Se verifica en el repositorio del proveedor el action.yml de cada SHA fijado:
upload 7.0.1, download 8.0.1, setup-java 6.0.1 y cache 6.1.0. Todos ejecutan
Node 24. El runner observado del CI es 2.337.0, superior al mínimo 2.327.1
documentado por setup-java; el Node de las aplicaciones y Java 21 se conservan.

Restore y save se actualizan juntos en CI/CD, con claves, prefijos, limpieza y
condiciones de caché completos conservados. No se cambia la base del escáner,
su versión, supresiones, umbral, ni precondiciones de publicación. El CI debe
probar restauración y seis análisis reales antes de integrar.

Upload declara archive=true para preservar nombre/rutas, tanto en informes como
en artefactos.tgz. Download declara digest-mismatch=error. Son valores del
contrato publicado por el proveedor; no se debilita la validación de huellas.

Dos jobs de CI preparan y transportan entre runners fixtures sintéticas: un
paquete tar y archivos de dos rutas con igual nombre. El consumidor exige las
rutas y bytes literales establecidos en herramientas/comprobar_artefactos.py,
sin derivarlos del contenido descargado. El tar se inspecciona sin extraer
archivos a disco; se rechazan entradas no regulares y duplicadas en la fixture.
Los permisos del consumidor son solo lectura de contenido y acciones; no OIDC.

35 pruebas locales de herramientas correctas: cinco nuevas cubren transporte
simulado válido, informe alterado, ruta adicional, tar alterado y entrada no
regular. No se presenta una prueba roja por cambiar versiones ni se atribuye
el transporte remoto a una copia local. El ensayo efectivo entre runners está
pendiente del nuevo CI. El comentario del build integral se ajusta al número
real de jobs; no se separan los módulos de producto ni se filtra por rutas.

El digest incorrecto del servidor GitHub no se provocó: no se afirma cobertura
de esa respuesta negativa remota. Los casos negativos locales comprueban el
oráculo propio, con bytes independientes. No son documentos reales ni prueban
originales SUNAT, despliegue, IAM, AWS o auditoría independiente. deploy.yml se
mantiene sincronizado, pero no se ejecuta CD. CI e integración pendientes al
registrar; cerrar las cuatro propuestas solo después del verde posterior.
