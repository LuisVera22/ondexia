"""Contrasta ambos workflows con una caché inmutable y datos independientes."""
import ast
import re
import subprocess
import tempfile
import unittest
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]


def pasos(ruta):
    texto = ruta.read_text()
    resultado = {}
    for nombre, bloque in re.findall(r'^      - name: ([^\n]+)\n(.*?)(?=^      - (?:name:|uses:)|\Z)', texto, re.M | re.S):
        if nombre not in ('Restaurar la base de vulnerabilidades', 'Guardar la base de vulnerabilidades'):
            continue
        clave = re.search(r'^          key: (.+)$', bloque, re.M).group(1)
        prefijos = re.search(r'^          restore-keys: (.*)$', bloque, re.M)
        if prefijos and prefijos.group(1) == '|':
            prefijos = re.findall(r'^            (\S+)$', bloque, re.M)
        else:
            prefijos = [prefijos.group(1)] if prefijos else []
        resultado[nombre] = {'clave': clave, 'prefijos': prefijos,
                             'condicion': re.search(r'^        if: (.+)$', bloque, re.M).group(1)}
    return resultado


def clave_en(plantilla, intento):
    return plantilla.replace('${{ github.run_id }}', '900').replace('${{ github.run_attempt }}', str(intento))


def permite_guardar(condicion, informe):
    # Solo admite las condiciones de estos pasos; cualquier expresión nueva requiere revisión.
    expresion = condicion.replace('${{', '').replace('}}', '').strip()
    expresion = expresion.replace('always()', 'True').replace('inputs.solo_landing', 'False')
    expresion = re.sub(r"hashFiles\('([^']+)'\)",
                      lambda m: repr('huella' if m.group(1) == 'apps/backend/pom.xml' or informe else ''), expresion)
    expresion = expresion.replace('&&', ' and ')
    expresion = re.sub(r'!(?!=)', 'not ', expresion)
    arbol = ast.parse(expresion, mode='eval')
    permitidos = (ast.Expression, ast.BoolOp, ast.And, ast.Or, ast.Compare,
                  ast.NotEq, ast.Eq, ast.UnaryOp, ast.Not, ast.Constant)
    if any(not isinstance(nodo, permitidos) for nodo in ast.walk(arbol)):
        raise ValueError('La condición de caché requiere revisión.')
    return bool(eval(compile(arbol, '<condicion de cache>', 'eval'), {'__builtins__': {}}, {}))



class CacheNvd(unittest.TestCase):
    def limpieza(self, nombre):
        texto = (RAIZ / '.github/workflows' / nombre).read_text()
        coincidencia = re.search(
            r'^      - name: Limpiar el bloqueo NVD restaurado\n(.*?)(?=^      - (?:name:|uses:)|\Z)',
            texto, re.M | re.S)
        return texto, coincidencia

    def test_bloqueo_restaurado_se_retira_sin_tocar_la_base(self):
        for nombre in ['ci.yml', 'deploy.yml']:
            with self.subTest(workflow=nombre), tempfile.TemporaryDirectory(prefix='nvd prueba ') as temporal:
                _, paso = self.limpieza(nombre)
                # Sin paso de limpieza, la restauración deja intacto el bloqueo.
                comando = re.search(r'^        run: (.+)$', paso.group(1), re.M).group(1) if paso else ':'
                # Solo sustituimos el directorio de datos por una fixture aislada.
                ruta_datos = '$HOME/.m2/repository/org/owasp/dependency-check-data'
                if paso:
                    self.assertEqual(comando.count(ruta_datos), 1)
                comando = comando.replace(ruta_datos, temporal)
                directorio = Path(temporal)
                bloqueo = directorio / 'odc.update.lock'
                base = directorio / 'odc.mv.db'
                datos = b'base NVD de prueba\x00\xff'
                base.write_bytes(datos)
                bloqueo.write_text('bloqueo de otro runner')
                subprocess.run(['bash', '-e', '-c', comando], check=True)
                self.assertFalse(bloqueo.exists(), 'El bloqueo heredado impediría actualizar NVD')
                self.assertEqual(base.read_bytes(), datos)
                # Ausencia de bloqueo (caché sana) y de directorio (caché fría).
                subprocess.run(['bash', '-e', '-c', comando], check=True)
                self.assertEqual(base.read_bytes(), datos)
                base.unlink()
                directorio.rmdir()
                subprocess.run(['bash', '-e', '-c', comando], check=True)

    def test_limpieza_solo_tras_restaurar_y_antes_de_maven(self):
        for nombre in ['ci.yml', 'deploy.yml']:
            with self.subTest(workflow=nombre):
                texto, paso = self.limpieza(nombre)
                self.assertIsNotNone(paso, 'Falta limpiar el bloqueo del runner anterior')
                self.assertLess(texto.index('- name: Restaurar la base de vulnerabilidades'), paso.start())
                self.assertLess(paso.end(), texto.index('./mvnw'))
                condicion = re.search(r'^        if: (.+)$', paso.group(1), re.M).group(1)
                self.assertEqual(condicion, pasos(RAIZ / '.github/workflows' / nombre)[
                    'Restaurar la base de vulnerabilidades']['condicion'])

    def configuraciones(self):
        for nombre in ['ci.yml', 'deploy.yml']:
            yield nombre, pasos(RAIZ / '.github/workflows' / nombre)

    def test_reintentar_no_colisiona_con_la_copia_anterior(self):
        for nombre, configuracion in self.configuraciones():
            with self.subTest(workflow=nombre):
                guardar = configuracion['Guardar la base de vulnerabilidades']
                almacen = {clave_en(guardar['clave'], 1): 'copia anterior'}
                clave = clave_en(guardar['clave'], 2)
                if clave not in almacen:
                    almacen[clave] = 'base completa del reintento'
                self.assertEqual(almacen[clave], 'base completa del reintento')
                self.assertEqual(len(almacen), 2)

    def test_descarga_fallida_no_se_publica_como_base_completa(self):
        for nombre, configuracion in self.configuraciones():
            with self.subTest(workflow=nombre):
                condicion = configuracion['Guardar la base de vulnerabilidades']['condicion']
                self.assertFalse(permite_guardar(condicion, informe=False))
                self.assertTrue(permite_guardar(condicion, informe=True))

    def test_restaura_base_completa_antes_de_una_copia_antigua_incompleta(self):
        almacen = {'nvd-completa-800-1': 'base completa', 'nvd-899': 'descarga incompleta más reciente'}
        for nombre, configuracion in self.configuraciones():
            with self.subTest(workflow=nombre):
                restaurar = configuracion['Restaurar la base de vulnerabilidades']
                recuperado = None
                for prefijo in restaurar['prefijos']:
                    candidatas = [clave for clave in almacen if clave.startswith(prefijo)]
                    if candidatas:
                        recuperado = almacen[candidatas[-1]]
                        break
                self.assertEqual(recuperado, 'base completa')

    def test_misma_clave_al_restaurar_y_guardar_y_respaldo_heredado(self):
        for nombre, configuracion in self.configuraciones():
            with self.subTest(workflow=nombre):
                restaurar = configuracion['Restaurar la base de vulnerabilidades']
                guardar = configuracion['Guardar la base de vulnerabilidades']
                self.assertEqual(restaurar['clave'], guardar['clave'])
                self.assertEqual(restaurar['prefijos'][-1], 'nvd-')


if __name__ == '__main__':
    unittest.main()
