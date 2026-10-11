# Corrección de la URL raíz del sitemap

Lote ACT-23 sobre develop `c550ebb744012f434be3d7bb74d247a004491271`.
[Especificación](../especificaciones/2026-10-11-url-sitemap.md) y
[resultados, fuentes, mutaciones y huellas](2026-10-11-url-sitemap.json).

## Comportamiento comprobado

El build anterior con sitemap 3.7.3 pasa, pero el comprobador nuevo devuelve 1:
la salida contiene `https://ondexia.com` y el oráculo exige
`https://ondexia.com/`. Con 3.7.4, instalación congelada y el mismo comprobador,
el build devuelve 0. Se mantiene Astro 7.2.10, Node 22.22.0 y TypeScript 5.9.3.

El control exige URLs literales fijadas antes del cambio: una página inicial en
sitemap-0.xml, índice apuntando a ese sitemap y una etiqueta canonical y og:url
apuntando a la raíz. Se incorpora al script build existente de la landing;
el CI ejecuta ese build, sin añadir un workflow ni desactivar controles.
Si se agregan páginas deberá ampliarse el inventario esperado de forma explícita.
El extractor se limita a etiquetas conocidas de estos archivos generados:
no es un validador general de XML, HTML, SEO ni accesibilidad.

Tres mutaciones independientes en copias temporales cambiaron canonical,
Open Graph e índice. Cada una devolvió 1 con su aserción pertinente; las copias
se destruyeron y las huellas de los tres archivos originales permanecieron
iguales. La comprobación final de los originales devuelve 0. No se atribuye
al parche el canonical/Open Graph: ya se generan con new URL en Base.astro.

## Resolución y seguridad

Se aplicó el parche propuesto por Dependabot #23 y la retirada de
stream-replace-string, eliminada por el proveedor. El gestor propuso además
actualizaciones ajenas de tipos de Node, undici-types y Zod: se acotó la resolución
para conservar sus versiones anteriores. Zod 4.4.3 cumple el rango publicado
^4.3.6; sitemap 9.0.1 cumple ^9.0.0. La instalación con lock congelado validó
el resultado. No hay overrides ni cambio de dominio o política de rutas.

| Auditoría | Altos | Moderados | Críticos | Salida |
| --- | ---: | ---: | ---: | ---: |
| Completa antes | 2 | 2 | 0 | 1 |
| Completa después | 2 | 2 | 0 | 1 |
| Producción después, umbral high usado en CI | 0 | 1 | 0 | 0 |

Los avisos completos corresponden a fast-uri (dos altos y un moderado) y
smol-toml (un moderado). Siguen pendientes de caracterización/remediación;
este parche no los corrige ni acredita exposición en la landing estática.
La auditoría con --prod no equivale a revisar todas las herramientas.

Comandos desde apps/frontend/ondexia.landing, con pnpm 11.11.0:

```sh
pnpm run build
node herramientas/comprobar-sitemap.mjs
pnpm install --frozen-lockfile
pnpm audit --json
pnpm audit --prod --audit-level=high --json
```

Las salidas completas están comprimidas y sus huellas constan en el JSON.
CI del nuevo PR, integración y CI posterior pendientes al registrar. No se
publicó la landing, no se probó CloudFront ni se realizó auditoría independiente.
#23 permanece abierto hasta verificar el verde posterior y su head/delta intactos.
