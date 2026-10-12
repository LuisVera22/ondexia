# Direcciones procesadas por las herramientas de la landing

ACT-19/23, lote independiente desde develop
`117712f5fcd379d0abb60f15155ae00c9e717766`.

## Problema y alcance

La auditoría completa identifica fast-uri 3.1.6 bajo
@astrojs/check → language-server → volar-service-yaml → yaml-language-server →
Ajv. Las notas oficiales de 3.1.7 corrigen GHSA-qw65-cvwx-89v3 y
GHSA-58mr-gqgx-xq4g; 3.1.8 corrige GHSA-hrr3-gc8f-f4qj:
<https://github.com/fastify/fast-uri/releases/tag/v3.1.7> y
<https://github.com/fastify/fast-uri/releases/tag/v3.1.8>.

Actualizar solo la resolución transitiva a 3.1.8, compatible con ^3.0.1 de
Ajv 8.20.0. No incorporar fast-uri como dependencia directa, no usar overrides
ni migrar Astro o TypeScript. Es un arreglo de biblioteca de herramientas:
no demuestra que el flujo de Ondexia permita explotar esas entradas ni exposición AWS.

## Oráculos independientes y aceptación

Casos derivados de las pruebas oficiales de v3.1.7/v3.1.8, antes de actualizar:

- Serializar un puerto `@destino.invalid` debe lanzar el error del proveedor
  `URI port is malformed.` y no convertir ese texto en autoridad.
- Analizar `http://[fe80` debe devolver `URI host is malformed.`.
- Normalizar `//%41.example` debe producir `//a.example`.

Se usan solo cadenas locales; ningún caso establece conexión de red. Resolver
la biblioteca real desde la cadena instalada de @astrojs/check/Ajv, sin fijar
una ruta interna del almacén ni un número de versión dentro del test.
Los tres casos deben fallar con 3.1.6 y pasar con 3.1.8. Añadirlos al build
existente junto a las comprobaciones estáticas de la landing.

Repetir auditoría completa y de producción: los tres IDs deben desaparecer;
registrar por separado cualquier aviso residual. Instalación congelada y build
correctos; CI completo del PR y del merge para sus commits exactos antes de
cerrar la feature. No sustituye auditoría independiente, no modifica reglas
de negocio, contratos, dinero, migraciones ni datos, y no habilita despliegues.
