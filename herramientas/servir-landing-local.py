"""Sirve el build local de la landing con su CSP declarada en Terraform.

Solo escucha en loopback. No consulta AWS ni demuestra la configuración desplegada.
--politica-csp permite reproducir una cabecera anterior guardada como evidencia.
"""
import argparse
import re
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[1]

def leer_politica(archivo):
    if archivo:
        return archivo.read_text().strip()
    texto = (RAIZ / 'ondexia.infra/estatico.tf').read_text()
    bloque = re.search(r'csp_landing\s*=\s*join\("; ",\s*\[(.*?)\]\)', texto, re.S)
    if not bloque:
        raise ValueError('No se encontró csp_landing en Terraform')
    return '; '.join(re.findall(r'"([^"]+)"', bloque.group(1)))

if __name__ == '__main__':
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument('--puerto', type=int, default=4324)
    argumentos.add_argument('--politica-csp', type=Path)
    parametros = argumentos.parse_args()
    directorio = RAIZ / 'apps/frontend/ondexia.landing/dist'
    if not (directorio / 'index.html').is_file():
        argumentos.error('Primero compilar ondexia.landing')
    politica = leer_politica(parametros.politica_csp)
    if not politica or '\r' in politica or '\n' in politica:
        argumentos.error('La CSP debe ser una cabecera no vacía de una sola línea')

    class Pagina(SimpleHTTPRequestHandler):
        def __init__(self, *datos, **opciones):
            super().__init__(*datos, directory=str(directorio), **opciones)

        def end_headers(self):
            self.send_header('Content-Security-Policy', politica)
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.send_header('Cache-Control', 'no-store')
            super().end_headers()

        def log_message(self, formato, *datos):
            pass

    print(f'Landing local: http://127.0.0.1:{parametros.puerto}/', flush=True)
    try:
        ThreadingHTTPServer(('127.0.0.1', parametros.puerto), Pagina).serve_forever()
    except KeyboardInterrupt:
        pass
