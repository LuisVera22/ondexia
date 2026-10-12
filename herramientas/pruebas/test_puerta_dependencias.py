"""Ejecuta los pasos reales con Maven sustituido; no invoca NVD ni despliega."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest

RAIZ = Path(__file__).resolve().parents[2]
CLAVE_FICTICIA = 'credencial-ficticia-prueba'


def cuerpo_del_paso(workflow, nombre):
    texto = (RAIZ / '.github/workflows' / workflow).read_text()
    bloques = re.findall(
        r'^      - name: ([^\n]+)\n(.*?)(?=^      - (?:name:|uses:)|\Z)',
        texto, re.M | re.S)
    encontrados = [bloque for titulo, bloque in bloques if titulo == nombre]
    if len(encontrados) != 1 or encontrados[0].count('        run: |\n') != 1:
        raise ValueError('El paso de análisis requiere revisar su estructura.')
    cuerpo = encontrados[0].split('        run: |\n', 1)[1]
    return '\n'.join(linea[10:] for linea in cuerpo.splitlines() if linea.startswith('          '))


class PuertaDeDependencias(unittest.TestCase):
    def ejecutar(self, workflow, clave='', analizar='true', entorno='prod', codigo=0):
        nombre = 'Backend' if workflow == 'ci.yml' else 'Verificar el backend'
        cuerpo = cuerpo_del_paso(workflow, nombre)
        self.assertIn(entorno, ('dev', 'prod'))
        cuerpo = cuerpo.replace('${{ inputs.entorno }}', entorno)
        self.assertNotIn('${{', cuerpo, 'Una expresión nueva requiere contraste explícito')
        with tempfile.TemporaryDirectory(prefix='puerta dependencias ') as temporal:
            carpeta = Path(temporal)
            sustituto = carpeta / 'mvnw'
            sustituto.write_text('#!' + sys.executable + '\n' + '''
import json
import os
from pathlib import Path
import sys
Path('argumentos.json').write_text(json.dumps(sys.argv[1:]))
sys.exit(int(os.environ['CODIGO_SUSTITUTO']))
''')
            sustituto.chmod(0o700)
            guion = carpeta / 'paso.sh'
            guion.write_text(cuerpo)
            resultado = subprocess.run(
                ['bash', '--noprofile', '--norc', '-e', '-o', 'pipefail', str(guion)],
                cwd=carpeta, env={'PATH': os.defpath, 'NVD_API_KEY': clave,
                                 'ANALIZAR_DEPENDENCIAS': analizar, 'CODIGO_SUSTITUTO': str(codigo)},
                capture_output=True, text=True, timeout=10)
            registro = carpeta / 'argumentos.json'
            argumentos = json.loads(registro.read_text()) if registro.exists() else None
        self.assertNotIn(CLAVE_FICTICIA, resultado.stdout + resultado.stderr)
        return resultado, argumentos

    def test_produccion_sin_clave_bloquea_antes_de_maven(self):
        resultado, argumentos = self.ejecutar('deploy.yml')
        self.assertNotEqual(resultado.returncode, 0)
        self.assertIn('::error', resultado.stdout)
        self.assertIn('Falta NVD_API_KEY', resultado.stdout)
        self.assertIsNone(argumentos)

    def test_desarrollo_sin_clave_declara_omision_y_verifica(self):
        resultado, argumentos = self.ejecutar('deploy.yml', entorno='dev')
        self.assertEqual(resultado.returncode, 0)
        self.assertIn('::warning', resultado.stdout)
        self.assertIn('omitido', resultado.stdout)
        self.assertEqual(argumentos, ['-B', 'verify', '-Ddependency-check.skip=true'])

    def test_ci_sin_clave_declara_omision_y_verifica(self):
        resultado, argumentos = self.ejecutar('ci.yml')
        self.assertEqual(resultado.returncode, 0)
        self.assertIn('::warning', resultado.stdout)
        self.assertIn('no se ejecutó', resultado.stdout)
        self.assertEqual(argumentos, ['-B', 'verify', '-Ddependency-check.skip=true'])

    def test_rama_con_clave_declara_aplazamiento(self):
        resultado, argumentos = self.ejecutar('ci.yml', clave=CLAVE_FICTICIA, analizar='false')
        self.assertEqual(resultado.returncode, 0)
        self.assertIn('::notice', resultado.stdout)
        self.assertIn('aplazado', resultado.stdout)
        self.assertEqual(argumentos, ['-B', 'verify', '-Ddependency-check.skip=true'])

    def test_camino_con_clave_analiza_sin_skip(self):
        for workflow in ('ci.yml', 'deploy.yml'):
            with self.subTest(workflow=workflow):
                resultado, argumentos = self.ejecutar(workflow, clave=CLAVE_FICTICIA)
                self.assertEqual(resultado.returncode, 0)
                self.assertEqual(argumentos, ['-B', 'verify', '-Ddependency-check.nvdApiKey=' + CLAVE_FICTICIA])
                self.assertNotIn('omitido', resultado.stdout)
                self.assertNotIn('aplazado', resultado.stdout)

    def test_fallo_del_analisis_detiene_el_paso(self):
        for workflow in ('ci.yml', 'deploy.yml'):
            with self.subTest(workflow=workflow):
                resultado, argumentos = self.ejecutar(workflow, clave=CLAVE_FICTICIA, codigo=17)
                self.assertEqual(resultado.returncode, 17)
                self.assertEqual(argumentos, ['-B', 'verify', '-Ddependency-check.nvdApiKey=' + CLAVE_FICTICIA])

    def test_fallo_de_verify_sin_analisis_tampoco_se_oculta(self):
        for workflow in ('ci.yml', 'deploy.yml'):
            with self.subTest(workflow=workflow):
                resultado, argumentos = self.ejecutar(workflow, entorno='dev', codigo=23)
                self.assertEqual(resultado.returncode, 23)
                self.assertEqual(argumentos, ['-B', 'verify', '-Ddependency-check.skip=true'])


if __name__ == '__main__':
    unittest.main()
