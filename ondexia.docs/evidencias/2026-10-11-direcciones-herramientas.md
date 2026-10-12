# Correcciones de fast-uri en las herramientas de la landing

ACT-19/23, base develop `117712f5fcd379d0abb60f15155ae00c9e717766`.
[Especificación](../especificaciones/2026-10-11-direcciones-herramientas.md),
[resultados y huellas](2026-10-11-direcciones-herramientas.json),
[fuentes oficiales](2026-10-11-direcciones-herramientas-fuentes.json).

## Ejecución y oráculo

Los tres tests del comprobador resuelven fast-uri desde la cadena instalada de
@astrojs/check, pasando por su servicio YAML y Ajv. No usan un mock ni una copia
de código. Los casos proceden de los tests y notas oficiales de 3.1.7 y 3.1.8,
consultados antes de cambiar la versión; los esperados son literales independientes.

Con 3.1.6, salida 1 y tres fallos de aserción: no lanza ante puerto que inyecta
autoridad; no devuelve error de host ante corchete abierto; conserva la mayúscula
escapada al normalizar. Con 3.1.8, salida 0 y tres casos correctos. La instalación
congelada y el build con astro check, compilación, sitemap/canonical/Open Graph
y comprobador de direcciones devuelven 0. No se hacen conexiones con las URLs
de prueba; los dominios example/invalid son solo entradas locales del analizador.

El build incorpora los tres tests a su control permanente. Si la herramienta
cambia su cadena de dependencias habrá que reevaluar el comprobador, no omitir
su error de carga ni sustituirlo por un mock para obtener verde.

## Resolución y auditorías

pnpm update fast-uri --depth Infinity --lockfile-only seleccionó 3.1.8 dentro
de ^3.0.1 declarado por Ajv 8.20.0. El delta del lock es cuatro líneas reemplazadas:
versión, integridad, dependencia de Ajv y snapshot. No hay actualización de
manifiestos de dependencias, overrides ni salto de Astro/TypeScript/Node.

| Comprobación | Altos | Moderados | Críticos | Salida |
| --- | ---: | ---: | ---: | ---: |
| Auditoría completa antes | 2 | 2 | 0 | 1 |
| Auditoría completa después | 0 | 1 | 0 | 1 |
| Producción después, umbral high de CI | 0 | 1 | 0 | 0 |

Desaparecen los IDs GHSA-qw65-cvwx-89v3, GHSA-58mr-gqgx-xq4g y
GHSA-hrr3-gc8f-f4qj. Se comprobó que la suma de conteos coincide con la cantidad
de advisories y que no hay error de infraestructura antes de contrastar los IDs.
GHSA-58mr-gqgx-xq4g no informa patched_versions en el JSON recibido; sus notas
oficiales sí lo incluyen en 3.1.7. No se inventa un rango para rellenar ese campo.
Queda GHSA-r4xh-jqrq-34v2 de smol-toml, moderado, pendiente de remediación.

Desde apps/frontend/ondexia.landing, pnpm 11.11.0 y Node 22.22.0:

```sh
node herramientas/comprobar-direcciones.cjs
pnpm install --frozen-lockfile
pnpm run build
pnpm audit --json
pnpm audit --prod --audit-level=high --json
```

## Límites

Se reproduce y corrige el comportamiento de la biblioteca realmente instalada;
no se demuestra una explotación de Ondexia, SSRF en AWS ni vulnerabilidad de
los archivos estáticos publicados. La auditoría independiente no se ejecutó.
CI del nuevo PR y del merge pendientes al registrar; comprobar sus commits
exactos antes de integrar/cerrar la feature. Este lote no sustituye un PR de
Dependabot específico: no cerrar sus propuestas mayores de Astro/TypeScript
porque se hayan corregido estos avisos transitivos. No se desplegó ni cambió main.
