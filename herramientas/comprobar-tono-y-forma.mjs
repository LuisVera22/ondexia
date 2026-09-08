#!/usr/bin/env node
/**
 * El tono y la forma de doc 12 §7.2, comprobados en el CI.
 *
 * ── POR QUÉ SOBRE LO COMPILADO Y NO SOBRE EL CÓDIGO ────────────────────────
 *
 * Se leen `dist/`, no `src/`, y eso resuelve dos problemas de golpe.
 *
 * El primero es el tono: los comentarios del código están en español y hablan
 * de lo que la pantalla decía antes —«decía Papelito manda»—, así que buscar
 * palabras prohibidas en el código pondría el CI en rojo por explicar la propia
 * decisión. En la salida compilada no hay comentarios: lo que queda es lo que
 * el cliente lee.
 *
 * El segundo es la forma. Tailwind solo emite las utilidades que se usan, así
 * que la ausencia de `.rounded-lg` en el CSS compilado no es una aproximación
 * a «nadie la escribió»: es exactamente eso. Un radio de más no se busca
 * leyendo plantillas, se comprueba mirando la hoja que se sirve.
 *
 * ── QUÉ COMPRUEBA ──────────────────────────────────────────────────────────
 *
 *   1. Un radio. Solo `rounded-base` y sus variantes por lado, más
 *      `rounded-full`, que en una insignia o un avatar no es decoración sino la
 *      forma del elemento.
 *   2. Una sombra, `shadow-flotante`, y solo para lo que flota. Ninguna en
 *      superficies.
 *   3. Tres pesos: 400, 500 y 600. Nada de 700 ni superiores.
 *   4. Ninguna palabra de la lista de abajo en el texto que se sirve.
 *
 * ── CÓMO SE EJECUTA ────────────────────────────────────────────────────────
 *
 *     node herramientas/comprobar-tono-y-forma.mjs
 *
 * Necesita que la aplicación y la landing estén construidas. Si falta alguna,
 * falla diciéndolo en vez de pasar en verde sin haber mirado nada, que es la
 * forma habitual de que una comprobación deje de comprobar.
 */
import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = resolve(dirname(fileURLToPath(import.meta.url)), '..');

/** Radios admitidos, con y sin variante por lado. */
const RADIO_ADMITIDO = /^rounded(?:-[lrtb]{1,2})?-base$|^rounded-full$/;

/** La única sombra que queda. */
const SOMBRA_ADMITIDA = /^shadow-flotante$/;

/**
 * Pesos por encima de 600.
 *
 * Outfit se usa en 400, 500 y 600. Con menos pesos el conjunto se ve más
 * sobrio, y un titular en 800 al lado de una pantalla cuyo texto más fuerte es
 * 600 se lee como otra marca (doc 10 §5).
 */
const PESO_PROHIBIDO = /^font-(?:bold|extrabold|black)$/;

/**
 * Lo que no se escribe.
 *
 * Peruanismos y coloquialismos de la landing original, y las formas de tuteo
 * que más aparecían en la aplicación. La lista es cerrada a propósito: una
 * heurística de «segunda persona» daría falsos positivos con cualquier palabra
 * que empiece por «tu», y un rojo que no se entiende se acaba desactivando.
 */
const PROHIBIDAS = [
  // Peruanismos y coloquialismos.
  'chamba',
  'al toque',
  'paltea',
  'paltee',
  'la yapa',
  'papelito manda',
  'avísenme',
  'avisenme',
  'quiero mi acceso',
  // Fórmulas de mercadotecnia que el aviso de suscripción ya prohíbe, aquí
  // extendidas a toda la superficie.
  'haz clic',
  'pulsa aquí',
  'no te pierdas',
  // Tuteo.
  'tu sesión',
  'tu cuenta',
  'tu empresa',
  'tu plan',
  'tus datos',
  'inténtalo',
  'escríbenos',
  'regístrate',
  'vuelve a intentar',
];

const objetivos = [
  {
    nombre: 'aplicación',
    dist: join(raiz, 'apps/frontend/ondexia.web/dist/ondexia-web/browser'),
    como: 'pnpm run build en apps/frontend/ondexia.web',
  },
  {
    nombre: 'landing',
    dist: join(raiz, 'apps/frontend/ondexia.landing/dist'),
    como: 'pnpm run build en apps/frontend/ondexia.landing',
  },
];

function archivos(directorio, extensiones) {
  const encontrados = [];
  for (const entrada of readdirSync(directorio)) {
    const ruta = join(directorio, entrada);
    if (statSync(ruta).isDirectory()) {
      encontrados.push(...archivos(ruta, extensiones));
    } else if (extensiones.some((ext) => entrada.endsWith(ext))) {
      encontrados.push(ruta);
    }
  }
  return encontrados;
}

const fallos = [];

for (const objetivo of objetivos) {
  if (!existsSync(objetivo.dist)) {
    fallos.push(
      `No está construida la ${objetivo.nombre}: falta ${objetivo.dist}. ` +
        `Se construye con ${objetivo.como}.`
    );
    continue;
  }

  // La landing sirve el CSS dentro del HTML; la aplicación, en archivos aparte.
  const hojas = archivos(objetivo.dist, ['.css', '.html']);
  const clases = new Set();
  for (const hoja of hojas) {
    const contenido = readFileSync(hoja, 'utf8');
    for (const coincidencia of contenido.matchAll(/\.(rounded[\w-]*|shadow[\w-]*|font-[\w-]*)[\s,{:]/g)) {
      clases.add(coincidencia[1]);
    }
  }

  for (const clase of [...clases].sort()) {
    if (clase.startsWith('rounded') && !RADIO_ADMITIDO.test(clase)) {
      fallos.push(
        `[${objetivo.nombre}] radio «${clase}» en el CSS compilado. Un radio, ` +
          `«rounded-base»; «rounded-full» solo en insignias y avatares (doc 12 §7.2).`
      );
    }
    if (clase.startsWith('shadow') && !SOMBRA_ADMITIDA.test(clase)) {
      fallos.push(
        `[${objetivo.nombre}] sombra «${clase}» en el CSS compilado. Ninguna en ` +
          `superficies; «shadow-flotante» solo para lo que flota (doc 12 §7.2).`
      );
    }
    if (PESO_PROHIBIDO.test(clase)) {
      fallos.push(
        `[${objetivo.nombre}] peso «${clase}» en el CSS compilado. Outfit se usa ` +
          `en 400, 500 y 600; el 700 y superiores no (doc 12 §7.2).`
      );
    }
  }

  const textos = archivos(objetivo.dist, ['.html', '.js']);
  for (const texto of textos) {
    const contenido = readFileSync(texto, 'utf8').toLowerCase();
    for (const palabra of PROHIBIDAS) {
      if (contenido.includes(palabra)) {
        fallos.push(
          `[${objetivo.nombre}] «${palabra}» aparece en ${texto.slice(raiz.length + 1)}. ` +
            `El registro es formal e impersonal (doc 12 §7.2).`
        );
      }
    }
  }
}

if (fallos.length > 0) {
  console.error('\nTono y forma — no cumple:\n');
  for (const fallo of fallos) {
    console.error(`  · ${fallo}`);
  }
  console.error('');
  process.exit(1);
}

console.log('Tono y forma: un radio, una sombra, tres pesos y ninguna palabra prohibida.');
