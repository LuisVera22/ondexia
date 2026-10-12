"""Rechaza estados ajenos y planes destructivos; nunca imprime sus contenidos."""
import argparse
import json
from pathlib import Path
import re
import sys

# ── La única excepción al rechazo de reemplazos ────────────────────────────
#
# La clave de cada artefacto lleva dentro el hash de su contenido —ver el
# comentario de `aws_s3_object.api` en api.tf—, y esa decisión es un arreglo,
# no un descuido: con una clave fija, subir un artefacto nuevo cambia el objeto
# pero Lambda sigue viendo la misma `s3_key`, no se entera, y el despliegue
# «funciona» corriendo el código viejo.
#
# El efecto secundario es que un artefacto distinto es un objeto distinto, así
# que Terraform lo REEMPLAZA. Y esta puerta rechazaba todo reemplazo, de modo
# que bloqueaba cualquier despliegue productivo con código nuevo: el checker de
# plan corre tres veces en cada ejecución del workflow. No era una puerta
# protegiendo un caso raro; era una regla demasiado ancha para donde está.
#
# Lo que la regla protege es la PILA: lo que tiene estado y no se puede volver
# a generar. Un artefacto de compilación no es eso —sale del mismo jar que
# acaba de construir el CI, y su bucket tiene versionado—, y conviene no
# confundir «derivado» con «con estado».
#
# La excepción se escribe estrecha a propósito. Lo que la delimita no es el
# tipo de recurso sino `replace_paths`, donde Terraform declara QUÉ atributo
# fuerza el reemplazo: si algún día lo fuerza cualquier otra cosa, esto vuelve
# a cerrarse solo.
ARTEFACTOS = {
    'aws_s3_object.api': 'api',
    'aws_s3_object.consultas': 'consultas',
    'aws_s3_object.facturacion': 'facturacion',
    'aws_s3_object.panel': 'panel',
}
# `local.nombre` conserva «ondexia-dev» tras la promoción (entorno_recursos),
# igual que las anclas de `comprobar_estado`. El sufijo es la cuenta.
BUCKET_ARTEFACTOS = re.compile(r'ondexia-dev-artefactos-[0-9]{12}')
# `filemd5` produce 32 hexadecimales en minúscula, y nada más.
CLAVE_ARTEFACTO = re.compile(r'(api|consultas|facturacion|panel)/[0-9a-f]{32}\.zip')
REEMPLAZOS = (['delete', 'create'], ['create', 'delete'])


def es_rotacion_de_artefacto(recurso):
    """Cierto solo si el cambio es rotar uno de los cuatro jars por su hash.

    Todo lo que no se pueda demostrar aquí se trata como no demostrado: un
    valor ausente, desconocido en el plan o fuera de formato devuelve falso, y
    el llamador rechaza el plan.
    """
    if recurso.get('type') != 'aws_s3_object':
        return False
    carpeta = ARTEFACTOS.get(recurso.get('address', '').split('[', 1)[0])
    if carpeta is None:
        return False

    cambio = recurso.get('change') or {}
    if cambio.get('actions') not in REEMPLAZOS:
        return False
    # Un borrado simple no llega aquí, y un reemplazo por cualquier atributo
    # que no sea la clave tampoco: entonces no es una rotación de artefacto.
    if cambio.get('replace_paths') != [['key']]:
        return False

    desconocido = cambio.get('after_unknown') or {}
    if desconocido.get('bucket') or desconocido.get('key'):
        return False

    antes, despues = cambio.get('before') or {}, cambio.get('after') or {}
    bucket = antes.get('bucket')
    if (not isinstance(bucket, str) or bucket != despues.get('bucket')
            or not BUCKET_ARTEFACTOS.fullmatch(bucket)):
        return False

    claves = (antes.get('key'), despues.get('key'))
    if not all(isinstance(clave, str) for clave in claves) or claves[0] == claves[1]:
        return False
    return all(CLAVE_ARTEFACTO.fullmatch(clave) and clave.startswith(carpeta + '/')
               for clave in claves)


def comprobar_estado(estado):
    if (estado.get('version') != 4 or not estado.get('lineage')
            or type(estado.get('serial')) is not int or estado['serial'] < 0):
        raise ValueError('El estado debe ser un estado Terraform existente y válido.')
    recursos = estado.get('resources', [])
    requeridos = {
        ('aws_db_instance', 'principal'): ('identifier', 'ondexia-dev'),
        ('aws_cognito_user_pool', 'inquilinos'): ('name', 'ondexia-dev-inquilinos'),
        ('aws_cognito_user_pool', 'personal'): ('name', 'ondexia-dev-personal'),
        ('aws_lambda_function', 'api'): ('function_name', 'ondexia-dev-api'),
    }
    for (tipo, nombre), (campo, esperado) in requeridos.items():
        candidatos = [r for r in recursos if r.get('mode') == 'managed'
                      and r.get('type') == tipo and r.get('name') == nombre
                      and not r.get('module')]
        if len(candidatos) != 1 or len(candidatos[0].get('instances', [])) != 1:
            raise ValueError('Falta un recurso único de la pila que se desea promover.')
        instancia = candidatos[0]['instances'][0]
        if instancia.get('deposed') or instancia.get('attributes', {}).get(campo) != esperado:
            raise ValueError('La identidad del estado no corresponde a la pila existente.')
    bases = [i for r in recursos if r.get('mode') == 'managed'
             and r.get('type') == 'aws_db_instance' for i in r.get('instances', [])]
    if len(bases) != 1:
        raise ValueError('La promoción debe conservar exactamente una instancia RDS.')


def comprobar_plan(plan, parcial=False):
    if not str(plan.get('format_version', '')).startswith('1.') or 'resource_changes' not in plan:
        raise ValueError('Se requiere el JSON completo del plan Terraform.')
    if plan.get('errored') or (plan.get('complete') is False and not parcial):
        raise ValueError('El plan debe estar completo y sin errores.')
    pila = {'aws_db_instance', 'aws_cognito_user_pool', 'aws_s3_bucket',
            'aws_lambda_function', 'aws_cloudfront_distribution', 'aws_vpc'}
    for recurso in plan['resource_changes']:
        if recurso.get('mode') == 'data':
            continue
        acciones = recurso.get('change', {}).get('actions')
        if (acciones not in (['no-op'], ['update'], ['create'])
                and not es_rotacion_de_artefacto(recurso)):
            raise ValueError('El plan contiene borrados, reemplazos o acciones no admitidas.')
        if recurso.get('type') in pila and acciones == ['create']:
            raise ValueError('El plan intenta crear otra pila de aplicación o datos.')
        identidad = recurso.get('address', '').split('[', 1)[0]
        if acciones != ['no-op'] and identidad in {
                'aws_iam_role.despliegue', 'aws_iam_role.plan',
                'aws_iam_role_policy.despliegue_iam',
                'aws_iam_role_policy_attachment.despliegue_poweruser',
                'aws_iam_role_policy_attachment.plan_lectura',
                'aws_iam_openid_connect_provider.github',
                'aws_iam_policy.frontera_despliegue'}:
            raise ValueError('Las identidades del pipeline requieren mantenimiento previo autorizado.')


def principal():
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument('tipo', choices=('estado', 'plan'))
    argumentos.add_argument('archivo', type=Path)
    argumentos.add_argument('--parcial', action='store_true', help='Solo para el plan previo acotado de migraciones.')
    opciones = argumentos.parse_args()
    try:
        contenido = json.loads(opciones.archivo.read_text())
        if not isinstance(contenido, dict):
            raise ValueError('Se requiere un objeto JSON.')
        if opciones.tipo == 'estado':
            comprobar_estado(contenido)
        else:
            comprobar_plan(contenido, parcial=opciones.parcial)
    except (ValueError, OSError, TypeError, KeyError, AttributeError):
        print('Promoción rechazada: estado o plan incompatible; revisar sin divulgar secretos.', file=sys.stderr)
        return 1
    print('Comprobación local de promoción correcta; falta contrastar AWS y aprobar el plan.')
    return 0


if __name__ == '__main__':
    sys.exit(principal())
