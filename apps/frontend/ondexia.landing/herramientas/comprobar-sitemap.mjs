import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const directorio = resolve(process.argv[2] ?? 'dist');
const [sitemap, indice, pagina] = await Promise.all([
  readFile(resolve(directorio, 'sitemap-0.xml'), 'utf8'),
  readFile(resolve(directorio, 'sitemap-index.xml'), 'utf8'),
  readFile(resolve(directorio, 'index.html'), 'utf8'),
]);

// El inventario actual tiene una sola página; nuevas rutas requieren ampliar el oráculo.
const direcciones = (xml) => [...xml.matchAll(/<loc>([^<]*)<\/loc>/g)].map((grupo) => grupo[1]);
assert.deepEqual(direcciones(sitemap), ['https://ondexia.com/'], 'La raíz del sitemap debe conservar /');
assert.deepEqual(direcciones(indice), ['https://ondexia.com/sitemap-0.xml'], 'El índice debe referir el sitemap del sitio');

const etiquetas = [...pagina.matchAll(/<(?:link|meta)\b[^>]*>/g)].map((grupo) => grupo[0]);
const atributo = (etiqueta, nombre) => etiqueta.match(new RegExp(`\\b${nombre}="([^"]*)"`))?.[1];
const canonicas = etiquetas.filter((etiqueta) => atributo(etiqueta, 'rel') === 'canonical');
const enlacesSociales = etiquetas.filter((etiqueta) => atributo(etiqueta, 'property') === 'og:url');
assert.deepEqual(canonicas.map((etiqueta) => atributo(etiqueta, 'href')), ['https://ondexia.com/'], 'Canonical debe apuntar a la raíz del sitio');
assert.deepEqual(enlacesSociales.map((etiqueta) => atributo(etiqueta, 'content')), ['https://ondexia.com/'], 'Open Graph debe apuntar a la raíz del sitio');

console.log('Sitemap, índice, canonical y Open Graph comprobados');
