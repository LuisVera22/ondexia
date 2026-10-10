import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { runInNewContext } from 'node:vm';

// Diagnóstico F03: exit 1 significa divergencia; no sustituye las pruebas de Angular.
const raiz = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const cargar = createRequire(resolve(raiz, 'apps/frontend/ondexia.web/package.json'));
const ts = cargar('typescript');
const ruta = resolve(raiz, 'apps/frontend/ondexia.web/src/app/pages/ventas/punto-de-venta/punto-de-venta.component.ts');
const fuente = ts.createSourceFile(ruta, readFileSync(ruta, 'utf8'), ts.ScriptTarget.Latest, true);
const nombres = ['redondear', 'totalDeLinea'];
const funciones = fuente.statements.filter((nodo) => ts.isFunctionDeclaration(nodo)
  && nombres.includes(nodo.name?.text));
if (funciones.length !== nombres.length) throw new Error('Cambió la estructura del cálculo: revisar el diagnóstico.');
const codigo = funciones.map((nodo) => nodo.getText(fuente)).join('\n');
const compilado = ts.transpileModule(codigo, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const auxiliares = { exports: {} };
const rutaDecimal = resolve(raiz, 'apps/frontend/ondexia.web/src/app/nucleo/decimal-exacto.ts');
runInNewContext(ts.transpileModule(readFileSync(rutaDecimal, 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText, auxiliares, { timeout: 1000 });
const entorno = { ...auxiliares.exports, exports: {} };
runInNewContext(compilado, entorno, { timeout: 1000 });

// Oráculos fijados con multiplicación/resta decimal y HALF_UP a céntimos (doc 13 §4.3).
const casos = [
  { id: 'dos-bolsas', cantidad: '2', precio: '32.50', descuento: '0', esperado: '65.00' },
  { id: 'decima', cantidad: '3', precio: '0.10', descuento: '0', esperado: '0.30' },
  { id: 'mitad-centimo', cantidad: '1', precio: '10.075000', descuento: '0', esperado: '10.08' },
  { id: 'cantidad-fraccionaria', cantidad: '0.5', precio: '20.150000', descuento: '0', esperado: '10.08' },
  { id: 'descuento', cantidad: '1', precio: '20.075000', descuento: '10', esperado: '10.08' },
  { id: 'precio-seis-decimales', cantidad: '5000', precio: '0.000001', descuento: '0', esperado: '0.01' },
  { id: 'importe-grande', cantidad: '1', precio: '123456789012.344999', descuento: '0', esperado: '123456789012.34' },
];
const resultados = casos.map((caso) => {
  const observado = entorno.exports.totalDeLinea({
    cantidad: caso.cantidad, precio: caso.precio, descuento: caso.descuento,
  });
  return { ...caso, observado, coincide: observado === caso.esperado };
});
console.log(JSON.stringify({ alcance: 'Funciones reales extraídas mediante AST; sin HTTP, plantilla ni persistencia', resultados }, null, 2));
process.exitCode = resultados.every((caso) => caso.coincide) ? 0 : 1;
