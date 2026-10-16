"""Comprueba HTML y recursos servidos contra el artefacto que se acaba de publicar.

No ejecuta JavaScript ni reemplaza la revisión visual. Detecta MIME incorrecto,
recursos ausentes y contenido desactualizado incluso cuando la portada devuelve 200.
"""
import argparse
from pathlib import Path
import time
from urllib.error import URLError
from urllib.parse import quote, urljoin
from urllib.request import urlopen


TIPOS = {
    '.html': {'text/html'},
    '.js': {'text/javascript', 'application/javascript'},
    '.mjs': {'text/javascript', 'application/javascript'},
    '.css': {'text/css'},
}


def comprobar(direccion, directorio):
    paginas = [directorio / nombre for nombre in ('index.html', 'planes.html')]
    recursos = sorted(p for p in (directorio / '_astro').rglob('*') if p.suffix in ('.js', '.mjs', '.css'))
    if not all(p.is_file() for p in paginas) or not any(p.suffix in ('.js', '.mjs') for p in recursos):
        raise ValueError('El artefacto debe contener portada, planes y módulos JavaScript')
    base = direccion.rstrip('/') + '/'
    for archivo in paginas + recursos:
        ruta = archivo.relative_to(directorio).as_posix()
        destinos = [base, urljoin(base, quote(ruta))] if ruta == 'index.html' else [urljoin(base, quote(ruta))]
        for destino in destinos:
            with urlopen(destino, timeout=20) as respuesta:
                tipo = respuesta.headers.get_content_type()
                if respuesta.status != 200 or tipo not in TIPOS[archivo.suffix]:
                    raise ValueError(f'{ruta}: HTTP {respuesta.status}, MIME {tipo}; se esperaba {sorted(TIPOS[archivo.suffix])}')
                if respuesta.read() != archivo.read_bytes():
                    raise ValueError(f'{ruta}: el contenido servido no coincide con el artefacto publicado')
    return len(paginas) + len(recursos)


if __name__ == '__main__':
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument('--url', required=True)
    argumentos.add_argument('--directorio', type=Path, required=True)
    parametros = argumentos.parse_args()
    for intento in range(1, 11):
        try:
            cantidad = comprobar(parametros.url, parametros.directorio)
            print(f'Landing verificada: {cantidad} archivos con MIME y contenido correctos, incluida la portada en /.')
            break
        except (OSError, ValueError, URLError) as error:
            print(f'Intento {intento}/10: {error}', flush=True)
            if intento == 10:
                raise SystemExit('La landing publicada no supera la comprobación de contenido y MIME')
            time.sleep(10)
