const { test } = require('node:test');
const afirmar = require('node:assert/strict');
const postcss = require('postcss');

test('los espacios dentro de comentarios no dividen una lista CSS', () => {
  afirmar.deepEqual(postcss.list.space('a /* x y */ b'), ['a', '/* x y */', 'b']);
});

test('las comas dentro de comentarios no dividen una lista CSS', () => {
  afirmar.deepEqual(postcss.list.comma('a, /* , */ b'), ['a', '/* , */ b']);
});

test('escapa el cierre style repartido entre nodos del procesador', () => {
  afirmar.equal(postcss.parse(':@x</style>').toString(), ':@x\\3c /style>');
});
