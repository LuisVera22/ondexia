"""Contrato declarativo de CORS; no demuestra configuración efectiva en AWS."""
from pathlib import Path
import re
import unittest


class CorsDeMarca(unittest.TestCase):
    def setUp(self):
        ruta = Path(__file__).resolve().parents[2] / "ondexia.infra" / "estatico.tf"
        recurso = re.search(
            r'^resource "aws_s3_bucket_cors_configuration" "marca" \{\n(.*?)^\}',
            ruta.read_text(), re.M | re.S)
        self.assertIsNotNone(recurso)
        self.recurso = recurso.group(1)

    def lista(self, nombre):
        campo = re.search(r'^\s*' + nombre + r'\s*=\s*\[([^\]]*)\]', self.recurso, re.M)
        self.assertIsNotNone(campo)
        return [valor.strip() for valor in campo.group(1).split(',')]

    def test_condicion_firmada_permitida_en_preflight(self):
        self.assertEqual(set(self.lista('allowed_headers')), {'"content-type"', '"if-none-match"'})

    def test_origen_y_metodo_acotados(self):
        self.assertEqual(self.lista('allowed_origins'), ['local.origen_app'])
        self.assertEqual(self.lista('allowed_methods'), ['"PUT"'])


if __name__ == '__main__':
    unittest.main()
