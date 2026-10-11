import io
import shutil
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from comprobar_artefactos import CONTENIDOS, comprobar, preparar

class ContratoArtefactos(unittest.TestCase):
    def setUp(self):
        self.temporal = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporal.cleanup)
        self.raiz = Path(self.temporal.name)
        preparar(self.raiz)
        shutil.copytree(self.raiz / 'origen-informes', self.raiz / 'descarga-informes')
        shutil.copytree(self.raiz / 'origen-paquete', self.raiz / 'descarga-paquete')

    def test_archivo_unico_y_varias_rutas_conservan_los_originales(self):
        comprobar(self.raiz)

    def test_informe_alterado_se_rechaza(self):
        (self.raiz / 'descarga-informes/uno/original.txt').write_bytes(b'alterado\n')
        with self.assertRaisesRegex(AssertionError, 'Contenido de informe alterado'):
            comprobar(self.raiz)

    def test_ruta_adicional_se_rechaza(self):
        (self.raiz / 'descarga-informes/inesperado.txt').write_bytes(b'inesperado')
        with self.assertRaisesRegex(AssertionError, 'Rutas de informes incorrectas'):
            comprobar(self.raiz)

    def test_paquete_con_contenido_alterado_se_rechaza(self):
        paquete = self.raiz / 'descarga-paquete/artefactos.tgz'
        with tarfile.open(paquete, 'w:gz') as archivo:
            for nombre, contenido in CONTENIDOS.items():
                datos = b'alterado\n' if nombre.startswith('uno/') else contenido
                entrada = tarfile.TarInfo(nombre)
                entrada.size = len(datos)
                archivo.addfile(entrada, io.BytesIO(datos))
        with self.assertRaisesRegex(AssertionError, 'Contenido del paquete alterado'):
            comprobar(self.raiz)

    def test_paquete_con_entrada_no_regular_se_rechaza(self):
        paquete = self.raiz / 'descarga-paquete/artefactos.tgz'
        with tarfile.open(paquete, 'w:gz') as archivo:
            for nombre in CONTENIDOS:
                entrada = tarfile.TarInfo(nombre)
                entrada.type = tarfile.SYMTYPE
                entrada.linkname = 'inexistente'
                archivo.addfile(entrada)
        with self.assertRaisesRegex(AssertionError, 'Entrada del paquete no regular'):
            comprobar(self.raiz)

if __name__ == '__main__':
    unittest.main()
