"""Ejecuta el paso real de publicación con AWS/Terraform sustituidos, sin nube."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest

RAIZ = Path(__file__).resolve().parents[2]


class PublicacionLandingTest(unittest.TestCase):
    def test_publica_modulos_con_mime_explicito_y_purga_su_cache(self):
        texto = (RAIZ / '.github/workflows/deploy.yml').read_text()
        paso = texto.split('      - name: Publicar la landing\n', 1)[1]
        cuerpo = paso.split('        run: |\n', 1)[1].split('\n      # En su propio paso', 1)[0]
        with tempfile.TemporaryDirectory() as temporal:
            base = Path(temporal)
            infra = base / 'ondexia.infra'
            infra.mkdir()
            destino = base / 'apps/frontend/ondexia.landing/dist'
            (destino / '_astro').mkdir(parents=True)
            for nombre in ('explorador.hash.js', 'movimiento.hash.mjs', 'estilos.hash.css'):
                (destino / '_astro' / nombre).write_text('contenido de prueba')
            (destino / 'index.html').write_text('<h1>Inicio</h1>')
            (destino / 'planes.html').write_text('<h1>Planes</h1>')
            binarios = base / 'bin'
            binarios.mkdir()
            registro = base / 'llamadas.jsonl'
            programas = {
                'terraform': '#!/bin/sh\nprintf \'{"bucket":"prueba","distribucion":"DPRUEBA"}\'\n',
                'aws': '''#!/usr/bin/env python3
import json, os, sys
with open(os.environ['REGISTRO_PRUEBA'], 'a') as archivo:
    archivo.write(json.dumps(sys.argv[1:]) + '\\n')
if sys.argv[1:3] == ['s3', 'ls']:
    print('archivo')
if sys.argv[1:3] == ['cloudfront', 'create-invalidation']:
    print('IPRUEBA')
''',
            }
            for nombre, contenido in programas.items():
                archivo = binarios / nombre
                archivo.write_text(contenido)
                archivo.chmod(0o755)
            entorno = dict(os.environ, PATH=f'{binarios}:{os.environ["PATH"]}', REGISTRO_PRUEBA=str(registro))
            subprocess.run(['bash', '-e', '-o', 'pipefail', '-c', textwrap.dedent(cuerpo)],
                           cwd=infra, env=entorno, check=True, capture_output=True, text=True)
            llamadas = [json.loads(linea) for linea in registro.read_text().splitlines()]
            for nombre, mime in [('explorador.hash.js', 'text/javascript; charset=utf-8'),
                                 ('movimiento.hash.mjs', 'text/javascript; charset=utf-8'),
                                 ('estilos.hash.css', 'text/css; charset=utf-8')]:
                copias = [c for c in llamadas if c[:2] == ['s3', 'cp'] and c[3].endswith('/' + nombre)]
                self.assertTrue(copias, f'Falta publicación explícita de {nombre}')
                self.assertEqual(copias[-1][copias[-1].index('--content-type') + 1], mime)
                self.assertFalse(copias[-1][2].startswith('s3://'), 'Publicar desde el artefacto local')
            invalidacion = next(c for c in llamadas if c[:2] == ['cloudfront', 'create-invalidation'])
            self.assertIn('/_astro/*', invalidacion)
            self.assertIn('/planes.html', invalidacion)
            self.assertTrue(any(c[:3] == ['cloudfront', 'wait', 'invalidation-completed'] for c in llamadas))
            html = [c for c in llamadas if c[:2] == ['s3', 'cp'] and c[3].endswith('/planes.html')]
            self.assertTrue(html, 'Planes también debe revalidar su caché')
            self.assertIn('no-cache, no-store, must-revalidate', html[-1])


if __name__ == '__main__':
    unittest.main()
