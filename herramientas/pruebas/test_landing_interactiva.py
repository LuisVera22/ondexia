"""Frontera de la landing: interacción local sin habilitar scripts de terceros."""
import re
import unittest
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]

class PoliticaDeLandingTest(unittest.TestCase):
    def directivas(self):
        texto = (RAIZ / 'ondexia.infra/estatico.tf').read_text()
        bloque = re.search(r'csp_landing\s*=\s*join\("; ",\s*\[(.*?)\]\)', texto, re.S)
        self.assertIsNotNone(bloque, 'La política de landing debe existir')
        return [cadena for cadena in re.findall(r'"([^"]+)"', bloque.group(1))]

    def test_permite_solo_scripts_del_mismo_sitio(self):
        scripts = [regla for regla in self.directivas() if regla.startswith('script-src ')]
        self.assertEqual(scripts, ["script-src 'self'"], 'La interacción requiere código local; no inline, eval ni terceros')

    def test_conserva_la_denegacion_de_conexiones_y_marcos(self):
        reglas = self.directivas()
        self.assertIn("default-src 'none'", reglas)
        self.assertIn("frame-ancestors 'none'", reglas)
        self.assertIn("form-action 'none'", reglas)
        self.assertFalse(any(regla.startswith('connect-src ') and regla != "connect-src 'none'" for regla in reglas))
        self.assertFalse(any(regla.startswith('frame-src ') and regla != "frame-src 'none'" for regla in reglas))

if __name__ == '__main__':
    unittest.main()
