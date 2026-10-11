# URL de inicio en el sitemap de la landing

Lote ACT-23: adaptar Dependabot #23 a develop
`c550ebb744012f434be3d7bb74d247a004491271`.

## Regla y alcance

La landing declara `site: https://ondexia.com`, solo tiene la página inicial y
usa `trailingSlash: never`. La ruta raíz de esa URL es `/`: su dirección absoluta
esperada es `https://ondexia.com/`. Sitemap, canonical y Open Graph deben referir
esa misma página. No se modifica el dominio ni la política de otras rutas.

Las notas oficiales de @astrojs/sitemap 3.7.4 (#17851) corrigen la omisión de
la barra raíz bajo trailingSlash=never o build.format=file:
<https://github.com/withastro/astro/pull/17851>.

## Comprobaciones

1. Compilar con 3.7.3 y comprobar los archivos estáticos contra URLs literales:
   sitemap-0.xml contiene solo https://ondexia.com/; sitemap-index.xml apunta a
   https://ondexia.com/sitemap-0.xml; index.html tiene canonical y og:url raíz.
   No construir los esperados desde el resultado de la compilación.
2. Actualizar únicamente sitemap a 3.7.4 y su lock. Repetir el mismo comprobador.
3. Mutar por separado canonical, Open Graph e índice en copias temporales de
   dist: cada alteración debe detectarse; no modificar los archivos originales.
4. Instalación congelada, build y auditorías completa/producción. Mantener los
   avisos independientes registrados: este parche no es una corrección de seguridad.
5. Incorporar la comprobación al build de la landing existente para que su CI
   ejecute el control; integrar tras CI completo y comprobar CI de develop.

No cambia API, importes, datos, migraciones ni UX visual. No mide posicionamiento
ni retorno comercial; evita una inconsistencia verificable de salida estática.
No se publica la landing ni se prueba CloudFront. La propuesta #23 se cierra
como sustituida solo después de verde posterior y verificar su delta/head intactos.
