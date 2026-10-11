"""Inventaría una copia local de documentos; no acredita autenticidad ni recupera datos."""
import argparse
import hashlib
import json
import os
import stat
import sys
from pathlib import Path


def _registrar(ruta, relativa, limite_bytes):
    registro = {'ruta': relativa, 'estado': 'NO_INVENTARIADO'}
    try:
        # test_no_sigue_enlaces_a_archivos_ni_directorios comprueba la omisión.
        descriptor = os.open(ruta, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        with os.fdopen(descriptor, 'rb') as fuente:
            anterior = os.fstat(fuente.fileno())
            if not stat.S_ISREG(anterior.st_mode):
                return registro | {'codigo': 'ARCHIVO_NO_REGULAR'}
            if anterior.st_size > limite_bytes:
                return registro | {'codigo': 'TAMANO_EXCEDIDO'}
            resumen = hashlib.sha256()
            cantidad = 0
            while bloque := fuente.read(1024 * 1024):
                cantidad += len(bloque)
                if cantidad > limite_bytes:
                    return registro | {'codigo': 'TAMANO_EXCEDIDO'}
                resumen.update(bloque)
            posterior = os.fstat(fuente.fileno())
            if (anterior.st_size, anterior.st_mtime_ns, anterior.st_ctime_ns) != (
                    posterior.st_size, posterior.st_mtime_ns, posterior.st_ctime_ns):
                return registro | {'codigo': 'FUENTE_CAMBIANTE'}
            return registro | {'estado': 'CANDIDATO_LOCALIZADO', 'bytes': cantidad,
                               'sha256': resumen.hexdigest(),
                               'tipo': 'REFERENCIA' if ruta.suffix.lower() == '.json' else 'DOCUMENTO'}
    except OSError:
        # Mensaje fijo: no incluye contenido ni textos del sistema de archivos.
        return registro | {'codigo': 'LECTURA_NO_DISPONIBLE'}


def inventariar(carpeta, limite_bytes=64 * 1024 * 1024):
    raiz = Path(carpeta)
    if raiz.is_symlink() or not raiz.is_dir():
        raise ValueError('Se requiere una carpeta de documentos existente, sin enlace simbólico.')
    if limite_bytes <= 0:
        raise ValueError('El límite de bytes debe ser positivo.')
    raiz = raiz.resolve()
    archivos = []
    for ruta in sorted(raiz.rglob('*')):
        relativa = ruta.relative_to(raiz).as_posix()
        if ruta.is_symlink():
            archivos.append({'ruta': relativa, 'estado': 'NO_INVENTARIADO', 'codigo': 'ENLACE_OMITIDO'})
        elif ruta.is_dir():
            continue
        elif ruta.suffix.lower() in {'.xml', '.zip', '.pdf'} or ruta.name in {'envio.json', 'recepcion.json'}:
            archivos.append(_registrar(ruta, relativa, limite_bytes))
    return {'estado': 'BUSQUEDA_PENDIENTE', 'autenticidad_verificada': False,
            'alcance': 'COPIA_LOCAL', 'limite_bytes': limite_bytes, 'archivos': archivos}


def principal():
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument('--documentos', required=True, type=Path,
                            help='Copia estable del prefijo documentos/, sin credenciales.')
    argumentos.add_argument('--limite-bytes', type=int, default=64 * 1024 * 1024)
    opciones = argumentos.parse_args()
    try:
        informe = inventariar(opciones.documentos, opciones.limite_bytes)
    except (ValueError, OSError):
        print('No se pudo inventariar la carpeta; comprueba ruta, acceso y límite.', file=sys.stderr)
        return 2
    print(json.dumps(informe, ensure_ascii=False, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(principal())
