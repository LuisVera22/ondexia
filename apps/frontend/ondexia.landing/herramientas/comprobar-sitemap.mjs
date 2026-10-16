import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const directorio = resolve(process.argv[2] ?? 'dist');
const [sitemap, indice] = await Promise.all([
  readFile(resolve(directorio, 'sitemap-0.xml'), 'utf8'),
  readFile(resolve(directorio, 'sitemap-index.xml'), 'utf8'),
]);
const direcciones = (xml) => [...xml.matchAll(/<loc>([^<]*)<\/loc>/g)].map((grupo) => grupo[1]);
const rutas = [['index.html', 'https://ondexia.com/'], ['planes.html', 'https://ondexia.com/planes.html']];
assert.deepEqual(direcciones(sitemap).sort(), rutas.map(([, url]) => url).sort(), 'El sitemap debe contener solo raíz y planes');
assert.deepEqual(direcciones(indice), ['https://ondexia.com/sitemap-0.xml'], 'El índice debe referir el sitemap del sitio');
const atributo = (etiqueta, nombre) => etiqueta.match(new RegExp(`\\b${nombre}="([^"]*)"`))?.[1];
for (const [archivo, url] of rutas) {
  const pagina = await readFile(resolve(directorio, archivo), 'utf8');
  const etiquetas = [...pagina.matchAll(/<(?:link|meta)\b[^>]*>/g)].map((grupo) => grupo[0]);
  const canonicas = etiquetas.filter((etiqueta) => atributo(etiqueta, 'rel') === 'canonical');
  const sociales = etiquetas.filter((etiqueta) => atributo(etiqueta, 'property') === 'og:url');
  assert.deepEqual(canonicas.map((etiqueta) => atributo(etiqueta, 'href')), [url], `Canonical incorrecto en ${archivo}`);
  assert.deepEqual(sociales.map((etiqueta) => atributo(etiqueta, 'content')), [url], `Open Graph incorrecto en ${archivo}`);
}
console.log('Sitemap, índice, canonical y Open Graph comprobados para portada y planes');
