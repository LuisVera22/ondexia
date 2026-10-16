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

  // La página de planes se sirve como archivo explícito desde S3, sin reescrituras.
  // Canonical, enlaces y sitemap conservan esa misma dirección.
  integrations: [sitemap({ serialize: (pagina) => ({ ...pagina, url: pagina.url.replace(/\/planes$/, '/planes.html') }) })],

  build: {
    format: 'file',
    // No reutilizar módulos cacheados con MIME binario. Invalidar CloudFront
    // no elimina la caché de los navegadores: los recursos reciben otra URL.
    assets: '_astro/modulos',
    // CSS integrado por página; los scripts siguen siendo archivos locales.
    inlineStylesheets: 'always',
  },

  vite: {
    // La CSP admite archivos locales, nunca scripts ejecutables inline.
    build: { assetsInlineLimit: 0 },
    plugins: [tailwindcss()],
  },
});
