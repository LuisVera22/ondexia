const { test } = require('node:test');
const afirmar = require('node:assert/strict');
const { realpathSync } = require('node:fs');
const { createRequire } = require('node:module');

// Comprueba la biblioteca de las herramientas; no acredita una frontera productiva.
let paquete = realpathSync('node_modules/@astrojs/check/package.json');
for (const dependencia of ['@astrojs/language-server', 'volar-service-yaml', 'yaml-language-server', 'ajv']) {
  paquete = createRequire(paquete).resolve(`${dependencia}/package.json`);
}
const direcciones = createRequire(paquete)('fast-uri');

test('rechaza la inyección de autoridad mediante el puerto', () => {
  afirmar.throws(() => direcciones.serialize({
    scheme: 'http', host: 'origen.example', port: '@destino.invalid', path: '/ruta',
  }), /URI port is malformed\./);
});

test('rechaza un corchete de dirección sin cierre', () => {
  afirmar.equal(direcciones.parse('http://[fe80').error, 'URI host is malformed.');
});

test('normaliza a minúsculas el carácter escapado del host', () => {
  afirmar.equal(direcciones.normalize('//%41.example'), '//a.example');
});
