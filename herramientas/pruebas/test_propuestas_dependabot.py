"""Contrato estructural de propuestas; no acredita aceptación ni ejecución del bot."""
from pathlib import Path
import re
import unittest

RAIZ = Path(__file__).resolve().parents[2]
AREAS = {('github-actions', '/'), ('maven', '/apps/backend'),
         ('npm', '/apps/frontend/ondexia.web'), ('npm', '/apps/frontend/ondexia.admin'),
         ('npm', '/apps/frontend/ondexia.landing')}


def campo(bloque, nombre, sangria=4):
    encontrado = re.search(r'^' + ' ' * sangria + re.escape(nombre) + r':\s*([^\n]+)$', bloque, re.M)
    return encontrado.group(1).strip().strip('"\'') if encontrado else None


class PropuestasDeDependabot(unittest.TestCase):
    def setUp(self):
        texto = (RAIZ / '.github/dependabot.yml').read_text()
        self.entradas = re.findall(
            r'^  - package-ecosystem: ([^\n]+)\n(.*?)(?=^  - package-ecosystem:|\Z)',
            texto, re.M | re.S)

    def test_conserva_las_cinco_areas_sin_duplicados(self):
        self.assertEqual(len(self.entradas), 5)
        self.assertEqual({(ecosistema, campo(bloque, 'directory'))
                          for ecosistema, bloque in self.entradas}, AREAS)

    def test_propuestas_se_dirigen_a_develop(self):
        self.assertEqual(len(self.entradas), 5)
        for ecosistema, bloque in self.entradas:
            with self.subTest(area=(ecosistema, campo(bloque, 'directory'))):
                self.assertEqual(campo(bloque, 'target-branch'), 'develop')

    def test_prefijo_convencional_con_ambito_espanol(self):
        self.assertEqual(len(self.entradas), 5)
        for ecosistema, bloque in self.entradas:
            with self.subTest(area=(ecosistema, campo(bloque, 'directory'))):
                self.assertEqual(campo(bloque, 'prefix', 6), 'build(dependencias)')

    def test_conserva_cadencia_y_limite_de_revision(self):
        self.assertEqual(len(self.entradas), 5)
        for ecosistema, bloque in self.entradas:
            with self.subTest(area=(ecosistema, campo(bloque, 'directory'))):
                self.assertEqual(campo(bloque, 'interval', 6), 'weekly')
                self.assertEqual(campo(bloque, 'day', 6), 'monday')
                self.assertEqual(campo(bloque, 'open-pull-requests-limit'), '5')


if __name__ == '__main__':
    unittest.main()
