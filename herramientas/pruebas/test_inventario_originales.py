"""Oráculos literales y fuentes efímeras; no contienen documentos de clientes."""
import importlib.util
import tempfile
import os
import subprocess
import sys
from unittest.mock import patch
import unittest
from pathlib import Path

RUTA = Path(__file__).resolve().parents[1] / 'inventariar_originales_legado.py'
ESPECIFICACION = importlib.util.spec_from_file_location('inventario_originales', RUTA)
INVENTARIO = importlib.util.module_from_spec(ESPECIFICACION)
ESPECIFICACION.loader.exec_module(INVENTARIO)


class InventarioOriginales(unittest.TestCase):
    def setUp(self):
        self.temporal = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporal.cleanup)
        self.raiz = Path(self.temporal.name)
        self.documentos = self.raiz / 'documentos'
        self.documentos.mkdir()

    def test_hash_literal_y_originales_intactos(self):
        for nombre in ['comprobante.xml', 'envio.zip', 'representacion.pdf']:
            (self.documentos / nombre).write_bytes(b'abc')
        informe = INVENTARIO.inventariar(self.documentos)
        self.assertEqual(informe['estado'], 'BUSQUEDA_PENDIENTE')
        self.assertFalse(informe['autenticidad_verificada'])
        self.assertEqual(len(informe['archivos']), 3)
        for archivo in informe['archivos']:
            self.assertEqual(archivo['estado'], 'CANDIDATO_LOCALIZADO')
            self.assertEqual(archivo['bytes'], 3)
            self.assertEqual(archivo['sha256'], 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad')
        for nombre in ['comprobante.xml', 'envio.zip', 'representacion.pdf']:
            self.assertEqual((self.documentos / nombre).read_bytes(), b'abc')
        self.assertEqual(len(list(self.documentos.iterdir())), 3)

    def test_vacio_no_significa_irrecuperable(self):
        informe = INVENTARIO.inventariar(self.documentos)
        self.assertEqual(informe['estado'], 'BUSQUEDA_PENDIENTE')
        self.assertEqual(informe['archivos'], [])
        self.assertFalse(informe['autenticidad_verificada'])

    def test_carpeta_ausente_falla(self):
        with self.assertRaises(ValueError):
            INVENTARIO.inventariar(self.raiz / 'ausente')

    def test_no_sigue_enlaces_a_archivos_ni_directorios(self):
        (self.raiz / 'externo.xml').write_bytes(b'no leer')
        (self.documentos / 'enlace.xml').symlink_to(self.raiz / 'externo.xml')
        (self.documentos / 'enlace-directorio').symlink_to(self.raiz, target_is_directory=True)
        archivos = INVENTARIO.inventariar(self.documentos)['archivos']
        self.assertEqual(len(archivos), 2)
        self.assertEqual({archivo['codigo'] for archivo in archivos}, {'ENLACE_OMITIDO'})
        self.assertTrue(all(archivo['estado'] == 'NO_INVENTARIADO' for archivo in archivos))
        self.assertTrue(all('sha256' not in archivo for archivo in archivos))

    def test_raiz_simbolica_falla(self):
        enlace = self.raiz / 'alias'
        enlace.symlink_to(self.documentos, target_is_directory=True)
        with self.assertRaises(ValueError):
            INVENTARIO.inventariar(enlace)

    def test_exceso_de_tamano_no_trunca_fuente(self):
        (self.documentos / 'grande.zip').write_bytes(b'abc')
        archivos = INVENTARIO.inventariar(self.documentos, limite_bytes=2)['archivos']
        self.assertEqual(len(archivos), 1)
        self.assertEqual(archivos[0]['codigo'], 'TAMANO_EXCEDIDO')
        self.assertEqual(archivos[0]['estado'], 'NO_INVENTARIADO')
        self.assertNotIn('sha256', archivos[0])
        self.assertEqual((self.documentos / 'grande.zip').read_bytes(), b'abc')

    def test_referencias_y_exclusion_de_credenciales(self):
        for nombre in ['envio.json', 'recepcion.json', 'credenciales.json', 'cliente.pfx']:
            (self.documentos / nombre).write_bytes(b'abc')
        archivos = INVENTARIO.inventariar(self.documentos)['archivos']
        self.assertEqual({archivo['ruta'] for archivo in archivos}, {'envio.json', 'recepcion.json'})
        self.assertTrue(all(archivo['tipo'] == 'REFERENCIA' for archivo in archivos))

    def test_archivo_no_regular_no_se_lee(self):
        os.mkfifo(self.documentos / 'canal.xml')
        archivos = INVENTARIO.inventariar(self.documentos)['archivos']
        self.assertEqual(len(archivos), 1)
        self.assertEqual(archivos[0]['codigo'], 'ARCHIVO_NO_REGULAR')
        self.assertNotIn('sha256', archivos[0])

    def test_error_de_lectura_no_expone_texto_externo(self):
        (self.documentos / 'comprobante.xml').write_bytes(b'abc')
        with patch.object(INVENTARIO.os, 'open', side_effect=PermissionError('texto privado')):
            archivos = INVENTARIO.inventariar(self.documentos)['archivos']
        self.assertEqual(archivos[0]['codigo'], 'LECTURA_NO_DISPONIBLE')
        self.assertNotIn('texto privado', str(archivos))

    def test_cli_falla_sin_informe_si_carpeta_no_existe(self):
        proceso = subprocess.run([sys.executable, str(RUTA), '--documentos',
                                  str(self.raiz / 'ausente')], capture_output=True, text=True)
        self.assertEqual(proceso.returncode, 2)
        self.assertEqual(proceso.stdout, '')
        self.assertIn('No se pudo inventariar', proceso.stderr)

    def test_limite_invalido_falla(self):
        with self.assertRaises(ValueError):
            INVENTARIO.inventariar(self.documentos, limite_bytes=0)


if __name__ == '__main__':
    unittest.main()
