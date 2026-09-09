// @ts-check
import { defineConfig } from 'astro/config';
import tailwindcss from '@tailwindcss/vite';
import sitemap from '@astrojs/sitemap';

/**
 * Landing de Ondexia.
 *
 * Estatica pura: se compila a HTML y se sirve desde S3 detras de CloudFront
 * (ondexia.infra/estatico.tf, sitio "landing"). No consulta datos ni conoce la
 * API — esa frontera esta en el doc 03 §3 y conviene no cruzarla: el dia que
 * la landing necesite el backend deja de poder desplegarse sola.
 *
 * `site` no es cosmetico: de ahi salen las URL absolutas del canonical, del
 * Open Graph y del sitemap. Con un valor equivocado, el buscador indexa
 * direcciones que no existen.
 */
export default defineConfig({
  site: 'https://ondexia.com',

  // Un solo archivo por pagina. La landing no tiene rutas anidadas, asi que el
  // 404 de CloudFront no necesita reescritura de directorios (estatico.tf deja
  // `es_spa = false` a proposito: aqui un 404 es un 404).
  trailingSlash: 'never',

  // El sitemap se genera solo a partir de las paginas. Hoy es una y parece de
  // sobra; el dia que existan /precios o el blog, ya esta puesto y nadie tiene
  // que acordarse. robots.txt lo declara.
  integrations: [sitemap()],

  build: {
    // El CSS va dentro del HTML. Es una sola pagina y pesa poco: quitar el
    // viaje extra al servidor mejora el LCP, que es la metrica que Google
    // mira en una landing.
    inlineStylesheets: 'always',
  },

  vite: {
    plugins: [tailwindcss()],
  },
});
