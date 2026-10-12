"""Contrato sintético de artefactos; no contiene documentos de clientes."""
import sys
import tarfile
from pathlib import Path

CONTENIDOS = {'uno/original.txt': b'original uno\n', 'dos/original.txt': b'original dos\n'}

def preparar(raiz):
    origen = raiz / 'origen-informes'
    for nombre, contenido in CONTENIDOS.items():
        archivo = origen / nombre
        archivo.parent.mkdir(parents=True, exist_ok=True)
        archivo.write_bytes(contenido)
    paquete = raiz / 'origen-paquete' / 'artefactos.tgz'
    paquete.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(paquete, 'w:gz') as archivo:
        for nombre in CONTENIDOS:
            archivo.add(origen / nombre, arcname=nombre)

def comprobar(raiz):
    informes = raiz / 'descarga-informes'
    nombres = {str(p.relative_to(informes)) for p in informes.rglob('*') if p.is_file()}
    assert nombres == set(CONTENIDOS), 'Rutas de informes incorrectas'
    for nombre, contenido in CONTENIDOS.items():
        ruta = informes / nombre
        assert not ruta.is_symlink(), 'No se admiten enlaces en la fixture'
        assert ruta.read_bytes() == contenido, 'Contenido de informe alterado'
    with tarfile.open(raiz / 'descarga-paquete' / 'artefactos.tgz', 'r:gz') as archivo:
        miembros = archivo.getmembers()
        assert {m.name for m in miembros} == set(CONTENIDOS), 'Rutas del paquete incorrectas'
        assert len(miembros) == len(CONTENIDOS), 'Entradas duplicadas en el paquete'
        for miembro in miembros:
            assert miembro.isfile(), 'Entrada del paquete no regular'
            assert archivo.extractfile(miembro).read() == CONTENIDOS[miembro.name], 'Contenido del paquete alterado'

if __name__ == '__main__':
    raiz = Path(sys.argv[2] if sys.argv[1] == '--preparar' else sys.argv[1])
    if sys.argv[1] == '--preparar':
        preparar(raiz)
    else:
        comprobar(raiz)
        print('Paquete e informes comprobados contra bytes y rutas de la especificación')
