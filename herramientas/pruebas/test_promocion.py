"""Oráculos sintéticos fijos: no consultan AWS ni justifican un apply."""
from copy import deepcopy
import importlib.util
from pathlib import Path
import re
import unittest
import os
import subprocess

RUTA = Path(__file__).resolve().parents[1] / 'comprobar_promocion.py'
ESPECIFICACION = importlib.util.spec_from_file_location('promocion', RUTA)
PROMOCION = importlib.util.module_from_spec(ESPECIFICACION)
ESPECIFICACION.loader.exec_module(PROMOCION)


def estado_existente():
    identidades = [
        ('aws_db_instance', 'principal', 'identifier', 'ondexia-dev'),
        ('aws_cognito_user_pool', 'inquilinos', 'name', 'ondexia-dev-inquilinos'),
        ('aws_cognito_user_pool', 'personal', 'name', 'ondexia-dev-personal'),
        ('aws_lambda_function', 'api', 'function_name', 'ondexia-dev-api'),
    ]
    return {'version': 4, 'lineage': 'estado-sintetico', 'serial': 12,
            'resources': [{'mode': 'managed', 'type': tipo, 'name': nombre,
                           'instances': [{'attributes': {campo: valor}}]}
                          for tipo, nombre, campo, valor in identidades]}


def plan_con(acciones, tipo='aws_db_instance'):
    return {'format_version': '1.2', 'complete': True,
            'resource_changes': [{'mode': 'managed', 'type': tipo,
                                  'change': {'actions': acciones}}]}


class PromocionProductiva(unittest.TestCase):
    def test_destino_productivo_ejecuta_rechazos_del_workflow(self):
        texto = (RUTA.parents[1] / '.github/workflows/deploy.yml').read_text()
        bloque = texto.split('      - name: Comprobar destino productivo\n', 1)[1].split('      - uses:', 1)[0]
        cuerpo = bloque.split('        run: |\n', 1)[1]
        cuerpo = '\n'.join(linea[10:] for linea in cuerpo.splitlines() if linea.startswith('          '))
        for entorno, rama, plan, landing, esperado in (
                ('prod', 'refs/heads/main', 'false', 'false', 0),
                ('prod', 'refs/heads/feature/prueba', 'true', 'false', 0),
                ('dev', 'refs/heads/main', 'false', 'false', 1),
                ('prod', 'refs/heads/develop', 'false', 'false', 1),
                ('prod', 'refs/heads/main', 'false', 'true', 1)):
            with self.subTest(entorno=entorno, rama=rama, plan=plan, landing=landing):
                resultado = subprocess.run(['bash', '-e', '-o', 'pipefail', '-c', cuerpo],
                    env={'PATH': os.defpath, 'ENTORNO_DESTINO': entorno,
                         'GITHUB_REF': rama, 'SOLO_PLAN': plan, 'SOLO_LANDING': landing},
                    capture_output=True, timeout=5)
                self.assertEqual(resultado.returncode, esperado)

    def test_puertas_preceden_a_credenciales_y_a_escrituras(self):
        texto = (RUTA.parents[1] / '.github/workflows/deploy.yml').read_text()
        self.assertLess(texto.index('name: Comprobar destino productivo'), texto.index('name: Asumir rol de solo lectura'))
        for bloque in texto.split('      - name: Terraform init\n')[1:]:
            estado = bloque.index('name: Comprobar estado promovido')
            plan = bloque.index('terraform plan')
            self.assertLess(estado, plan)
        self.assertEqual(texto.count('python3 ../herramientas/comprobar_promocion.py plan'), 3)
        self.assertNotIn('terraform apply -auto-approve \\\n', texto)

    def test_acepta_estado_existente(self):
        PROMOCION.comprobar_estado(estado_existente())

    def test_rechaza_estado_vacio(self):
        with self.assertRaises(ValueError):
            PROMOCION.comprobar_estado({})

    def test_rechaza_otra_identidad(self):
        estado = estado_existente()
        estado['resources'][0]['instances'][0]['attributes']['identifier'] = 'otra-base'
        with self.assertRaises(ValueError):
            PROMOCION.comprobar_estado(estado)

    def test_rechaza_recurso_ausente(self):
        estado = estado_existente()
        estado['resources'].pop()
        with self.assertRaises(ValueError):
            PROMOCION.comprobar_estado(estado)

    def test_rechaza_segunda_base(self):
        estado = estado_existente()
        otra = deepcopy(estado['resources'][0])
        otra['name'] = 'segunda'
        estado['resources'].append(otra)
        with self.assertRaises(ValueError):
            PROMOCION.comprobar_estado(estado)

    def test_acepta_actualizacion_sin_reemplazo(self):
        PROMOCION.comprobar_plan(plan_con(['update']))

    def test_acepta_nuevo_dns(self):
        PROMOCION.comprobar_plan(plan_con(['create'], 'aws_route53_record'))

    def test_rechaza_modificar_identidad_del_pipeline(self):
        plan = plan_con(['update'], 'aws_iam_role')
        plan['resource_changes'][0]['address'] = 'aws_iam_role.despliegue["dev"]'
        with self.assertRaises(ValueError):
            PROMOCION.comprobar_plan(plan)

    def test_plan_parcial_solo_para_migraciones_conserva_rechazo_de_borrado(self):
        plan = dict(plan_con(['update']), complete=False)
        PROMOCION.comprobar_plan(plan, parcial=True)
        with self.assertRaises(ValueError):
            PROMOCION.comprobar_plan(dict(plan_con(['delete']), complete=False), parcial=True)

    def test_rechaza_borrado_y_ambos_ordenes_de_reemplazo(self):
        for acciones in (['delete'], ['delete', 'create'], ['create', 'delete']):
            with self.subTest(acciones=acciones), self.assertRaises(ValueError):
                PROMOCION.comprobar_plan(plan_con(acciones))

    def test_rechaza_creacion_de_otra_pila(self):
        for tipo in ('aws_db_instance', 'aws_cognito_user_pool', 'aws_s3_bucket',
                     'aws_lambda_function', 'aws_cloudfront_distribution', 'aws_vpc'):
            with self.subTest(tipo=tipo), self.assertRaises(ValueError):
                PROMOCION.comprobar_plan(plan_con(['create'], tipo))

    def test_rechaza_plan_incompleto_y_formato_incompatible(self):
        for plan in ({}, {'format_version': '2.0', 'resource_changes': []},
                     dict(plan_con(['update']), complete=False),
                     dict(plan_con(['update']), errored=True)):
            with self.subTest(plan=plan), self.assertRaises(ValueError):
                PROMOCION.comprobar_plan(plan)


BUCKET = 'ondexia-dev-artefactos-370930247103'
VIEJA = 'api/d0d5de8566f24faeaebe2f761b3a102f.zip'
NUEVA = 'api/f29707af483991e7c38fdbede20b541b.zip'


def rotacion(**cambios):
    """Una rotación de artefacto válida; cada prueba altera UN solo campo."""
    d = {'direccion': 'aws_s3_object.api', 'tipo': 'aws_s3_object',
         'bucket_antes': BUCKET, 'bucket_despues': BUCKET,
         'clave_antes': VIEJA, 'clave_despues': NUEVA,
         'acciones': ['delete', 'create'], 'replace_paths': [['key']],
         'after_unknown': {}}
    d.update(cambios)
    return {'format_version': '1.2', 'complete': True, 'resource_changes': [{
        'mode': 'managed', 'type': d['tipo'], 'address': d['direccion'],
        'change': {'actions': d['acciones'], 'replace_paths': d['replace_paths'],
                   'before': {'bucket': d['bucket_antes'], 'key': d['clave_antes']},
                   'after': {'bucket': d['bucket_despues'], 'key': d['clave_despues']},
                   'after_unknown': d['after_unknown']}}]}


class RotacionDeArtefactos(unittest.TestCase):
    """La excepción del reemplazo por clave direccionada por contenido.

    Existe porque la clave lleva el hash del artefacto (api.tf), lo que hace
    que cada despliegue REEMPLACE el objeto; sin esta excepción el checker
    bloquea todo despliegue productivo con código nuevo, no solo la promoción.
    """

    def test_acepta_los_cuatro_artefactos(self):
        for direccion, carpeta in (('aws_s3_object.api', 'api'),
                                   ('aws_s3_object.consultas[0]', 'consultas'),
                                   ('aws_s3_object.facturacion[0]', 'facturacion'),
                                   ('aws_s3_object.panel[0]', 'panel')):
            with self.subTest(direccion=direccion):
                PROMOCION.comprobar_plan(rotacion(
                    direccion=direccion,
                    clave_antes=f'{carpeta}/{"a" * 32}.zip',
                    clave_despues=f'{carpeta}/{"b" * 32}.zip'))

    def test_acepta_los_dos_ordenes_del_reemplazo(self):
        for acciones in (['delete', 'create'], ['create', 'delete']):
            with self.subTest(acciones=acciones):
                PROMOCION.comprobar_plan(rotacion(acciones=acciones))

    def test_rechaza_otro_objeto_aunque_tenga_la_misma_forma(self):
        for direccion in ('aws_s3_object.otro', 'aws_s3_object.config',
                          'module.x.aws_s3_object.api'):
            with self.subTest(direccion=direccion), self.assertRaises(ValueError):
                PROMOCION.comprobar_plan(rotacion(direccion=direccion))

    def test_rechaza_cambio_de_bucket(self):
        with self.assertRaises(ValueError):
            PROMOCION.comprobar_plan(rotacion(
                bucket_despues='ondexia-dev-artefactos-999999999999'))

    def test_rechaza_bucket_ausente_vacio_o_ajeno(self):
        for bucket in (None, '', 'otro-bucket', 'ondexia-dev-emision-370930247103',
                       'ondexia-dev-artefactos-37093024710'):
            with self.subTest(bucket=bucket), self.assertRaises(ValueError):
                PROMOCION.comprobar_plan(rotacion(bucket_antes=bucket,
                                                  bucket_despues=bucket))

    def test_rechaza_valores_desconocidos_en_el_plan(self):
        for desconocido in ({'key': True}, {'bucket': True}):
            with self.subTest(desconocido=desconocido), self.assertRaises(ValueError):
                PROMOCION.comprobar_plan(rotacion(after_unknown=desconocido))

    def test_rechaza_clave_ausente_igual_o_de_otro_artefacto(self):
        for antes, despues in ((VIEJA, VIEJA), (None, NUEVA), (VIEJA, None),
                               (VIEJA, 'panel/' + 'f' * 32 + '.zip')):
            with self.subTest(antes=antes, despues=despues), self.assertRaises(ValueError):
                PROMOCION.comprobar_plan(rotacion(clave_antes=antes, clave_despues=despues))

    def test_rechaza_clave_con_formato_ajeno(self):
        for clave in ('api/' + 'f' * 31 + '.zip', 'api/' + 'F' * 32 + '.zip',
                      'api/' + 'f' * 32 + '.jar', 'api/libre.zip',
                      'api/sub/' + 'f' * 32 + '.zip'):
            with self.subTest(clave=clave), self.assertRaises(ValueError):
                PROMOCION.comprobar_plan(rotacion(clave_despues=clave))

    def test_rechaza_reemplazo_forzado_por_otro_atributo(self):
        for rutas in ([['source']], [['key'], ['bucket']], [], None, [['Key']]):
            with self.subTest(rutas=rutas), self.assertRaises(ValueError):
                PROMOCION.comprobar_plan(rotacion(replace_paths=rutas))

    def test_rechaza_borrado_simple_del_artefacto(self):
        with self.assertRaises(ValueError):
            PROMOCION.comprobar_plan(rotacion(acciones=['delete']))

    def test_la_excepcion_no_alcanza_a_la_pila(self):
        for tipo in ('aws_s3_bucket', 'aws_db_instance', 'aws_lambda_function'):
            with self.subTest(tipo=tipo), self.assertRaises(ValueError):
                PROMOCION.comprobar_plan(rotacion(tipo=tipo))

    def test_cada_guarda_es_portante_bajo_mutacion_aislada(self):
        """Afloja UNA guarda y comprueba que su rechazo deja de producirse.

        Si al relajar la guarda el plan malo siguiera rechazándose, la guarda
        no estaría haciendo el trabajo y la prueba negativa sería decorativa.
        """
        mutaciones = (
            ('ARTEFACTOS', dict(PROMOCION.ARTEFACTOS, **{'aws_s3_object.otro': 'api'}),
             rotacion(direccion='aws_s3_object.otro')),
            ('BUCKET_ARTEFACTOS', re.compile('.*'),
             rotacion(bucket_antes='otro-bucket', bucket_despues='otro-bucket')),
            ('CLAVE_ARTEFACTO', re.compile('.*'),
             rotacion(clave_despues='api/libre.zip')),
            ('REEMPLAZOS', (['delete', 'create'], ['create', 'delete'], ['delete']),
             rotacion(acciones=['delete'])),
        )
        for nombre, relajada, plan in mutaciones:
            with self.subTest(guarda=nombre):
                with self.assertRaises(ValueError):
                    PROMOCION.comprobar_plan(plan)
                original = getattr(PROMOCION, nombre)
                setattr(PROMOCION, nombre, relajada)
                try:
                    PROMOCION.comprobar_plan(plan)
                finally:
                    setattr(PROMOCION, nombre, original)
                self.assertIs(getattr(PROMOCION, nombre), original)
                with self.assertRaises(ValueError):
                    PROMOCION.comprobar_plan(plan)


if __name__ == '__main__':
    unittest.main()
