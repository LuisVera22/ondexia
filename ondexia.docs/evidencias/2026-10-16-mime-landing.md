# MIME de los módulos de la landing

## Incidencia y alcance

El propietario reportó en producción `Failed to load module script` con MIME
`binary/octet-stream` para Explorador y Modulos. La portada HTML respondía, pero
el navegador rechazaba los módulos y mostraba el recorrido estático. Deploy #66
(38944410005, commit 124747178555bcaf2c0b74d46be37a7d5b8cae4e) había terminado en verde:
la sonda solo comprobaba HTTP 200 de la portada.

El workflow copiaba `_astro` de S3 a S3 con `--metadata-directive REPLACE` para
fijar la caché, sin declarar `Content-Type`. Se elimina esa dependencia de la
inferencia de metadatos: JS/MJS y CSS se publican desde el artefacto local con
MIME explícito. No se modifica la CSP ni se relaja la validación del navegador.

Los módulos pasan a `/_astro/modulos/`: la caché inmutable de un navegador puede
conservar el MIME erróneo y no se vacía al invalidar CloudFront. La nueva URL
evita reutilizar esos objetos. El despliegue invalida `/_astro/*`, raíz, portada,
planes y metadatos de búsqueda, y espera la invalidación. Portada y planes
comparten ahora la política de revalidación HTML.

La sonda de publicación comprueba ambas páginas, raíz y todos los JS/MJS/CSS del
artefacto: HTTP 200, MIME permitido y bytes idénticos al build. Incluye los
módulos importados, aunque no aparezcan directamente en el HTML. Rechaza un
HTML de error servido como JS y una versión antigua con MIME correcto.

## Evidencia local

- `test_publicacion_landing.py` ejecuta el paso real de shell sustituyendo AWS
  y Terraform, sin credenciales ni nube. Antes del arreglo falló por falta de
  publicación explícita del módulo; después pasó. Comprueba MIME, fuentes
  locales, caché de planes, invalidación de módulos y espera.
- `test_landing_publicada.py`: cinco casos de aceptación/rechazo, con respuestas
  HTTP independientes de la implementación. HTTP 200 con MIME binario falla;
  contenido viejo, ausencia de recursos y artefacto sin JS también fallan.
- Suite completa `python3 -m unittest discover -s herramientas/pruebas -v`:
  **69 pruebas correctas**.
- Astro check: 20 archivos, cero errores, advertencias e indicaciones. Build
  final de dos páginas, sitemap/canonical/OG y tres pruebas de direcciones pasan.
- Servidor HTTP de loopback sobre el build: la sonda rechazó el módulo servido
  como `binary/octet-stream`; con su MIME JavaScript verificó siete archivos.
  Los cinco módulos tienen URL nueva y las referencias del HTML existen.

## Límites y verificación posterior

El error de producción está acreditado por la consola aportada por el propietario.
La reproducción y el arreglo se verificaron localmente; no se consultaron los
metadatos vivos de S3 ni se ejecutó un despliegue AWS en esta corrección. El
simulador de comandos no acredita permisos reales del rol. La política declarada
usa PowerUserAccess con frontera que no deniega CloudFront; no se amplían permisos.

Después de publicar esta corrección en main, un Deploy manual con `solo_landing`
debe publicar los MIME, invalidar CloudFront y superar la nueva sonda. La revisión
visual del hover, scroll y ampliación en producción sigue siendo necesaria: la
sonda HTTP no ejecuta JavaScript ni constituye una auditoría independiente.
