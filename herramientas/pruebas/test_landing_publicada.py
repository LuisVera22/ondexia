"""Oráculo HTTP independiente: HTTP 200 no basta para ejecutar un módulo."""
from email.message import Message
import importlib.util
from io import BytesIO
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.parse import urlparse

RAIZ = Path(__file__).resolve().parents[2]
ESPECIFICACION = importlib.util.spec_from_file_location('landing_publicada', RAIZ / 'herramientas/comprobar-landing-publicada.py')
MODULO = importlib.util.module_from_spec(ESPECIFICACION)
ESPECIFICACION.loader.exec_module(MODULO)


class LandingPublicadaTest(unittest.TestCase):
    def setUp(self):
        self.temporal = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporal.cleanup)
        self.directorio = Path(self.temporal.name)
        self.archivos = {'index.html': b'<h1>Inicio</h1>', 'planes.html': b'<h1>Planes</h1>',
                         '_astro/explorador.js': b'export const inicio = true;',
                         '_astro/comun.mjs': b'export const comun = true;',
                         '_astro/estilos.css': b'body { color: black; }'}
        for nombre, contenido in self.archivos.items():
            destino = self.directorio / nombre
            destino.parent.mkdir(exist_ok=True)
            destino.write_bytes(contenido)

    def servir(self, url, timeout, tipo_js='text/javascript; charset=utf-8', contenido_js=None):
        ruta = urlparse(url).path.lstrip('/') or 'index.html'
        contenido = self.archivos[ruta]
        tipo = 'text/html' if ruta.endswith('.html') else 'text/css' if ruta.endswith('.css') else tipo_js
        if contenido_js is not None and ruta.endswith('.js'):
            contenido = contenido_js
        respuesta = BytesIO(contenido)
        respuesta.status = 200
        respuesta.headers = Message()
        respuesta.headers['Content-Type'] = tipo
        return respuesta

    def test_acepta_contenido_y_mime_correctos(self):
        with patch.object(MODULO, 'urlopen', side_effect=self.servir) as solicitudes:
            self.assertEqual(MODULO.comprobar('https://ejemplo.test', self.directorio), 5)
            self.assertEqual(solicitudes.call_count, 6)

    def test_rechaza_el_error_mime_reportado_aunque_http_sea_200(self):
        for tipo in ('binary/octet-stream', 'application/octet-stream', 'text/html'):
            with self.subTest(tipo=tipo), patch.object(MODULO, 'urlopen', side_effect=lambda url, timeout: self.servir(url, timeout, tipo_js=tipo)):
                with self.assertRaisesRegex(ValueError, 'MIME'):
                    MODULO.comprobar('https://ejemplo.test', self.directorio)

    def test_rechaza_cache_de_otra_version_con_mime_correcto(self):
        with patch.object(MODULO, 'urlopen', side_effect=lambda url, timeout: self.servir(url, timeout, contenido_js=b'viejo')):
            with self.assertRaisesRegex(ValueError, 'no coincide'):
                MODULO.comprobar('https://ejemplo.test', self.directorio)

    def test_rechaza_recurso_ausente(self):
        with patch.object(MODULO, 'urlopen', side_effect=HTTPError('https://ejemplo.test', 404, 'Ausente', None, None)):
            with self.assertRaises(HTTPError):
                MODULO.comprobar('https://ejemplo.test', self.directorio)

    def test_no_acepta_artefacto_sin_javascript(self):
        for ruta in self.directorio.glob('_astro/*.?js'):
            ruta.unlink()
        (self.directorio / '_astro/explorador.js').unlink()
        with self.assertRaisesRegex(ValueError, 'módulos JavaScript'):
            MODULO.comprobar('https://ejemplo.test', self.directorio)


if __name__ == '__main__':
    unittest.main()
