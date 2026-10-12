"""Rechaza estados ajenos y planes destructivos; nunca imprime sus contenidos."""
import argparse
import json
from pathlib import Path
import sys


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
        if acciones not in (['no-op'], ['update'], ['create']):
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
