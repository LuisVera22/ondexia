# Corrección de avisos críticos de herramientas Angular

[Especificación](../especificaciones/2026-10-11-herramientas-angular.md),
[resultados y huellas](2026-10-11-herramientas-angular.json),
[fuentes oficiales](2026-10-11-herramientas-angular-fuentes.json).
Base develop `3f97241acc26f6da237869a0ef2247e8a0d10508`.

## Arreglo y comprobación previa/posterior

En web y panel solo cambian los dos rangos directos de herramientas:
@angular/build y @angular/cli pasan a ^21.2.26. Los lockfiles cambian su árbol
transitivo: build incorpora piscina 5.3.2/undici 7.29.1 y CLI incorpora MCP SDK
1.31.0; la resolución compatible obtiene proxy-addr 2.0.8. No hay overrides,
Angular 22, cambio de core, TypeScript, Zone ni paquete runtime directo.
Las notas 21.2.24/25/26 confirman las actualizaciones undici/piscina/MCP;
las salidas JSON npm conservan requisitos e integridades declaradas por el proveedor.
No se afirma verificación independiente de toda la cadena de suministro.

El oráculo procede de los avisos GHSA externos y la puerta crítica cero de la
especificación previa, no de los valores obtenidos al compilar. Antes, auditoría
crítica devuelve 1 con exactamente los dos avisos críticos fijados. Después,
devuelve 0 y cero críticos en ambos árboles. La auditoría completa posterior
devuelve 1 por avisos residuales; --prod --audit-level=high devuelve 0.

| Aplicación | Críticos antes/después | Altos antes/después | Moderados antes/después | Pruebas |
| --- | --- | --- | --- | --- |
| Web | 2 → 0 | 17 → 4 | 23 → 3 | 144 correctas |
| Panel | 2 → 0 | 16 → 4 | 19 → 3 | 5 correctas |

Instalación --frozen-lockfile, build y suites correctos en ambos proyectos.
La comparación de importers confirma que ninguna otra versión directa cambió.
No se ejecutó exploit ni se creó un test que copiara números de versión;
se ejecutaron el análisis independiente previo/posterior y regresiones existentes.

## Incidencias del entorno

Primer intento de instalación sin CI abortó por falta de TTY; se repitió con
CI=true correctamente. Primer intento de build/pruebas en sandbox no pudo abrir
SQLite del almacén pnpm ni consultar registro; no se considera regresión funcional.
Se repitieron con acceso autorizado al almacén, sin cambiar código o controles.
Chromium usa el wrapper local sin sandbox SUID de esta nube, ya documentado en
setup; no equivale a validación de aislamiento del navegador en producción.

## Pendientes y siguiente corte

Quedan cuatro avisos altos y tres moderados en cada aplicación, todos en rutas
dev de Karma/cobertura: braces, brace-expansion, engine.io y qs. Están identificados
con GHSA, versiones y fuentes en la auditoría completa; las aplicaciones comparten
avisos, no sumar como incidentes diferentes. El siguiente corte revisará rangos
de sus padres y parches compatibles antes de considerar overrides o sustituir Karma.
La landing conserva sus avisos registrados y no cambia aquí.

No se cierra #27: su salto aislado a CLI 22 sigue pospuesto y no es el arreglo aplicado.
No se modifica main, API, modelos, dinero, migraciones, datos ni controles del CI.
CI completo de este head y del merge pendientes al redactar; se contrastarán en el PR.
Sin auditoría independiente, alcanzabilidad, exposición AWS o garantía ante avisos
futuros. ACT-19/23 y F20/F23/F24 siguen parciales; no se cierra toda deuda de seguridad.
