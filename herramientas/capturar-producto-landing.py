"""Captura Angular real en local con fixtures GET y ninguna escritura ni red externa.

El servidor SPA debe estar preparado previamente en 127.0.0.1:8030. Este script
no compila, cambia archivos de la aplicación, emite documentos ni modifica DOM.
Ejemplo desde la raíz del repo: python herramientas/capturar-producto-landing.py
--destino ../ondexia-vistas/landing-interactiva/capturas-producto
--compilado apps/frontend/ondexia.web/dist/ondexia-web/browser
"""
import argparse
import asyncio
import hashlib
import json
import re
import shutil
import subprocess
import time
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from playwright.async_api import async_playwright


RAIZ = Path(__file__).resolve().parents[1]
ID_EMPRESA = '00000000-0000-4000-8000-000000000010'
ID_LOCAL = '00000000-0000-4000-8000-000000000020'
ID_NOTA = '00000000-0000-4000-8000-000000009901'


def serializar(valor):
    """Fixture monetario conserva números decimales exactos en JSON."""
    if isinstance(valor, Decimal):
        return str(valor)
    if isinstance(valor, dict):
        return '{' + ','.join(json.dumps(c) + ':' + serializar(v) for c, v in valor.items()) + '}'
    if isinstance(valor, list):
        return '[' + ','.join(serializar(v) for v in valor) + ']'
    return json.dumps(valor, ensure_ascii=False)


def construir_fixtures():
    empresa = {
        'id': ID_EMPRESA, 'ruc': '20000000000', 'razonSocial': 'Comercial Demo S.A.C.',
        'nombreComercial': 'Comercial Demo', 'rol': 'Administrador',
        'regimenTributario': 'OTRO', 'emiteFacturas': True, 'permiteVentaSinStock': False,
        'modoSunat': 'BETA', 'domicilioFiscal': 'Av. Demostración 123, Lima',
        'ubigeo': '150101', 'activa': True, 'editable': [],
    }
    locales = [{'id': ID_LOCAL, 'codigo': '0000', 'nombre': 'Principal',
                'direccion': empresa['domicilioFiscal'], 'ubigeo': '150101', 'activa': True}]
    permisos = [
        'ventas:acceder', 'ventas.caja:acceder', 'ventas.caja:consultar',
        'ventas.nota_venta:acceder', 'ventas.nota_venta:registrar', 'ventas.nota_venta:consultar',
        'ventas.cliente:acceder', 'ventas.cliente:consultar', 'ventas.cliente:registrar',
        'ventas.comprobante:acceder', 'ventas.comprobante:emitir', 'ventas.comprobante:consultar',
        'almacen:acceder', 'almacen.producto:acceder', 'almacen.producto:consultar',
        'configuracion:acceder', 'configuracion.identidad:acceder', 'configuracion.identidad:consultar',
        'configuracion.empresa:acceder', 'configuracion.empresa:consultar',
        'configuracion.serie:acceder', 'configuracion.serie:consultar',
    ]
    contexto = {
        'usuario': {'id': 'usuario-demo', 'nombre': 'Demostración', 'email': 'demo@example.invalid'},
        'cuenta': {'id': 'cuenta-demo', 'esAdministrador': True, 'estadoSuscripcion': 'ACTIVA',
                   'soloLectura': False},
        'empresaActiva': empresa, 'empresas': [empresa], 'establecimientos': locales,
        'permisos': permisos,
    }
    cajas = [{'id': 'caja-demo', 'sucursalId': ID_LOCAL, 'codigo': 'CAJA1',
              'nombre': 'Caja mostrador', 'activa': True,
              'sesionAbierta': {'id': 'sesion-demo', 'cajaId': 'caja-demo', 'estado': 'ABIERTA',
                               'abiertaPor': 'usuario-demo', 'abiertaEn': '2026-10-14T10:00:00Z',
                               'montoInicial': Decimal('100.00'), 'declarado': {}, 'calculado': {}, 'diferencia': {}}}]
    cliente = {
        'id': 'cliente-demo', 'tipoDocumento': 'DNI', 'tipoDocumentoNombre': 'DNI',
        'numeroDocumento': '00000000', 'nombre': 'Cliente de demostración',
        'direccion': 'Dirección de demostración, Lima', 'correo': 'cliente@example.invalid',
        'telefono': None, 'verificadoEn': None, 'admiteFactura': False, 'activo': True,
    }
    productos = [
        {'id': 'producto-cajas', 'codigo': 'CAJ', 'nombre': 'Cajas de envío',
         'unidad': 'NIU', 'unidadNombre': 'Unidad', 'afectacion': 'GRAVADO',
         'llevaIgv': True, 'precio': '150.000000', 'controlaStock': True, 'existencia': '48'},
        {'id': 'producto-cinta', 'codigo': 'CIN', 'nombre': 'Cinta de embalaje',
         'unidad': 'NIU', 'unidadNombre': 'Unidad', 'afectacion': 'GRAVADO',
         'llevaIgv': True, 'precio': '25.000000', 'controlaStock': True, 'existencia': '72'},
        {'id': 'servicio-entrega', 'codigo': 'ENV', 'nombre': 'Servicio de entrega',
         'unidad': 'ZZ', 'unidadNombre': 'Servicio', 'afectacion': 'GRAVADO',
         'llevaIgv': True, 'precio': '35.000000', 'controlaStock': False, 'existencia': None},
    ]
    series = [
        {'id': 'serie-demo-nv', 'sucursalId': ID_LOCAL, 'tipoDocumento': 'NV',
         'tipoDocumentoNombre': 'Nota de venta', 'serie': 'NV01', 'ultimoNumero': 41,
         'siguienteNumero': 'NV01-00000042', 'activa': True},
        {'id': 'serie-demo-factura', 'sucursalId': ID_LOCAL, 'tipoDocumento': '01',
         'tipoDocumentoNombre': 'Factura', 'serie': 'F001', 'ultimoNumero': 125,
         'siguienteNumero': 'F001-00000126', 'activa': True},
        {'id': 'serie-demo-boleta', 'sucursalId': ID_LOCAL, 'tipoDocumento': '03',
         'tipoDocumentoNombre': 'Boleta', 'serie': 'B001', 'ultimoNumero': 218,
         'siguienteNumero': 'B001-00000219', 'activa': True},
    ]
    lineas = []
    valores = [(3, '127.118644', '381.36', '68.64', '450.00'),
               (2, '21.186441', '42.37', '7.63', '50.00'),
               (1, '29.661017', '29.66', '5.34', '35.00')]
    for orden, (producto, valores_linea) in enumerate(zip(productos, valores), start=1):
        cantidad, valor_unitario, valor_venta, igv, total = valores_linea
        lineas.append({'orden': orden, 'productoId': producto['id'], 'codigo': producto['codigo'],
                       'descripcion': producto['nombre'], 'unidad': producto['unidad'],
                       'cantidad': cantidad, 'precioUnitario': Decimal(producto['precio']),
                       'valorUnitario': Decimal(valor_unitario), 'descuento': Decimal('0.00'),
                       'afectacion': 'GRAVADO', 'valorVenta': Decimal(valor_venta),
                       'igv': Decimal(igv), 'total': Decimal(total)})
    nota = {
        'id': ID_NOTA, 'tipo': 'NV', 'tipoNombre': 'Nota de venta', 'fiscal': False,
        'serie': 'NV01', 'numero': 42, 'numeroCompleto': 'NV01-00000042', 'estado': 'EMITIDO',
        'sucursalId': ID_LOCAL, 'sesionCajaId': 'sesion-demo', 'cliente': cliente,
        'fechaEmision': '2026-10-14', 'emitidoEn': '2026-10-14T11:00:00Z',
        'emitidoPor': 'usuario-demo', 'moneda': 'PEN', 'totalGravado': Decimal('453.39'),
        'totalIgv': Decimal('81.61'), 'totalExonerado': Decimal('0.00'), 'totalInafecto': Decimal('0.00'),
        'totalDescuento': Decimal('0.00'), 'total': Decimal('535.00'),
        'observaciones': 'Entrega coordinada en el local.', 'origen': None,
        'documentoOrigenId': None, 'motivo': None, 'motivoNombre': None,
        'pagos': [{'forma': 'EFECTIVO', 'monto': Decimal('535.00'), 'referencia': None,
                   'entregado': Decimal('550.00'), 'vuelto': Decimal('15.00')}],
        'lineas': lineas, 'logoPrincipal': '/apple-touch-icon.png',
        'logoTicket': '/apple-touch-icon.png',
        'datosHistoricos': {'emisor': empresa, 'local': locales[0], 'cliente': cliente},
    }
    logos = [{'logo': 'logo_principal', 'nombre': 'Logo principal',
              'url': '/apple-touch-icon.png', 'maximoBytes': 1048576},
             {'logo': 'logo_ticket', 'nombre': 'Logo para ticket',
              'url': '/apple-touch-icon.png', 'maximoBytes': 262144}]
    return empresa, locales, contexto, cajas, cliente, productos, series, nota, logos


def validar_origen(origen):
    """La herramienta jamás navega a un entorno remoto ni acepta URL con ruta."""
    url = urlparse(origen)
    try:
        puerto = url.port
    except ValueError as error:
        raise ValueError('El origen local tiene un puerto no válido.') from error
    if (url.scheme != 'http' or url.hostname not in {'localhost', '127.0.0.1'}
            or url.username is not None or url.password is not None
            or url.path not in {'', '/'} or url.query or url.fragment):
        raise ValueError('Solo se permite un origen HTTP local puro: localhost o 127.0.0.1, sin credenciales ni ruta.')
    return f'http://{url.hostname}' + (f':{puerto}' if puerto not in {None, 80} else '')


def validar_destino(destino):
    """La ejecución no escribe evidencia ni capturas dentro del repositorio."""
    destino = Path(destino).expanduser().resolve()
    if destino == RAIZ or RAIZ in destino.parents:
        raise ValueError('El destino de evidencia debe quedar fuera del repositorio.')
    return destino


async def capturar(origen, destino, compilado):
    origen = validar_origen(origen)
    destino = validar_destino(destino)
    compilado = compilado.expanduser().resolve() if compilado else None
    empresa, locales, contexto, cajas, cliente, productos, series, nota, logos = construir_fixtures()
    destino.mkdir(parents=True, exist_ok=True)
    peticiones, bloqueadas, errores, capturas, controles_recorrido = [], [], [], [], []
    configuracion = {'api': origen, 'consultas': origen, 'autoservicio': False,
                     'cognito': {'dominio': origen + '/cognito-no-disponible', 'clienteId': 'demo-sin-cognito'}}

    async def interceptar(ruta):
        peticion = ruta.request
        url = urlparse(peticion.url)
        evento = {'metodo': peticion.method, 'ruta': url.path, 'origen': f'{url.scheme}://{url.netloc}'}
        if evento['origen'] != origen or peticion.method != 'GET':
            bloqueadas.append(evento)
            await ruta.abort('blockedbyclient')
            return
        contenido = None
        if url.path == '/config.json': contenido = configuracion
        elif url.path == '/api/v1/contexto': contenido = contexto
        elif url.path == '/api/v1/ventas/cajas': contenido = cajas
        elif url.path == '/api/v1/configuracion/empresa': contenido = empresa
        elif url.path == '/api/v1/configuracion/establecimientos': contenido = locales
        elif url.path == '/api/v1/configuracion/identidad': contenido = logos
        elif url.path == '/api/v1/configuracion/series': contenido = series
        elif url.path == '/api/v1/ventas/series':
            tipo = parse_qs(url.query).get('tipo', ['NOTA_VENTA'])[0]
            codigo = {'NOTA_VENTA': 'NV', 'FACTURA': '01', 'BOLETA': '03'}.get(tipo, tipo)
            contenido = [s for s in series if s['tipoDocumento'] == codigo]
        elif url.path == '/api/v1/almacen/productos/disponibles':
            termino = parse_qs(url.query).get('q', [''])[0].casefold()
            contenido = [p for p in productos if termino in (p['nombre'] + ' ' + p['codigo']).casefold()]
        elif url.path == '/api/v1/ventas/clientes': contenido = [cliente]
        elif url.path == '/api/v1/ventas/notas-de-venta/' + ID_NOTA: contenido = nota
        elif url.path == '/api/v1/ventas/documentos/' + ID_NOTA + '/relacionados': contenido = []
        elif url.path.startswith('/api/') or url.path.startswith('/consultas/'):
            bloqueadas.append(dict(evento, motivo='API sin fixture explícito'))
            await ruta.abort('blockedbyclient')
            return
        if contenido is not None:
            peticiones.append(evento)
            await ruta.fulfill(status=200, content_type='application/json', body=serializar(contenido))
        else:
            # Un archivo local tampoco puede redirigir el navegador fuera del origen.
            respuesta = await ruta.fetch(max_redirects=0)
            if 300 <= respuesta.status < 400:
                bloqueadas.append(dict(evento, motivo='Redirección local no autorizada'))
                await ruta.abort('blockedbyclient')
            else:
                await ruta.fulfill(response=respuesta)

    async def bloquear_socket(socket):
        url = urlparse(socket.url)
        bloqueadas.append({'metodo': 'WEBSOCKET', 'ruta': url.path,
                           'origen': f'{url.scheme}://{url.netloc}',
                           'motivo': 'Solo se permiten GET HTTP locales y fixtures.'})
        await socket.close(code=1008, reason='Captura local sin WebSockets.')

    async with async_playwright() as p:
        ejecutable = shutil.which('chromium') or shutil.which('chromium-browser')
        navegador = await p.chromium.launch(executable_path=ejecutable, headless=True,
                                            args=['--no-sandbox', '--disable-background-networking'])
        try:
            sesion = {'acceso': 'sesion-demostracion-sin-validez', 'refresco': None,
                      'expiraEn': int(time.time() * 1000) + 3600000,
                      'correo': 'demo@example.invalid', 'nombre': 'Demostración'}
            for ancho, alto, sufijo in [(1440, 1000, '1440'), (390, 844, '390')]:
                navegador_contexto = await navegador.new_context(viewport={'width': ancho, 'height': alto},
                                                                  device_scale_factor=1, locale='es-PE',
                                                                  timezone_id='America/Lima',
                                                                  service_workers='block')
                await navegador_contexto.route('**/*', interceptar)
                await navegador_contexto.route_web_socket('**/*', bloquear_socket)
                await navegador_contexto.add_init_script(
                    'sessionStorage.setItem("ondexia.sesion",' + json.dumps(json.dumps(sesion)) + ');'
                    'sessionStorage.setItem("ondexia.empresa",' + json.dumps(ID_EMPRESA) + ');')
                pagina = await navegador_contexto.new_page()
                pagina.on('pageerror', lambda error: errores.append(str(error)))

                async def guardar(nombre, selector, completo=False):
                    await pagina.evaluate('document.fonts.ready')
                    await pagina.wait_for_timeout(250)
                    medidas = await pagina.evaluate('({ancho:innerWidth, anchoPagina:document.documentElement.scrollWidth})')
                    if medidas['anchoPagina'] > ancho + 1:
                        raise AssertionError('Desborde horizontal: ' + nombre + ' ' + str(medidas))
                    archivo = destino / f'{nombre}-{sufijo}.png'
                    await pagina.screenshot(path=str(archivo), full_page=completo, animations='disabled')
                    capturas.append({'archivo': archivo.name, 'ruta': urlparse(pagina.url).path,
                                     'componente': selector, 'ventana': {'ancho': ancho, 'alto': alto},
                                     'paginaCompleta': completo, 'medidas': medidas,
                                     'sha256': hashlib.sha256(archivo.read_bytes()).hexdigest()})
                    nombres = {'venta': 'venta', 'registro': 'registro',
                               'comprobante-logo': 'documento', 'identidad': 'identidad'}
                    archivo_componente = destino / (nombres[nombre] + '-' + ('escritorio' if ancho > 1000 else 'movil') + '.png')
                    # Captura propia del componente real; conserva sus estilos, controles y texto.
                    selector_captura = 'app-detalle-documento' if nombre == 'comprobante-logo' else selector
                    componente = pagina.locator(selector_captura)
                    await pagina.evaluate('window.scrollTo(0,0)')
                    caja = await componente.bounding_box()
                    # El rectángulo se captura sobre la página completa para que la cabecera
                    # fija no se superponga al desplazar un componente alto en móvil.
                    await pagina.screenshot(path=str(archivo_componente), full_page=True,
                                            clip=caja, animations='disabled')
                    capturas.append({'archivo': archivo_componente.name, 'ruta': urlparse(pagina.url).path,
                                     'componente': selector_captura, 'ventana': {'ancho': ancho, 'alto': alto},
                                     'recorteNativo': caja,
                                     'sha256': hashlib.sha256(archivo_componente.read_bytes()).hexdigest()})

                await pagina.goto(origen + '/ventas/punto-de-venta', wait_until='networkidle')
                await pagina.locator('app-punto-de-venta aside').wait_for()
                buscador = pagina.get_by_placeholder('Nombre o código, y Enter para agregar')
                for producto, cantidad in zip(productos, ['3', '2', '1']):
                    await buscador.fill(producto['codigo'])
                    await pagina.locator('app-buscador-entidad button').filter(has_text=producto['nombre']).click()
                    await pagina.get_by_label('Cantidad de ' + producto['nombre'], exact=True).fill(cantidad)
                await pagina.get_by_placeholder('Nombre o documento').fill('demostración')
                await pagina.locator('app-buscador-entidad button').filter(has_text=cliente['nombre']).click()
                await pagina.get_by_label('Efectivo entregado', exact=True).fill('550.00')
                await pagina.locator('#observaciones').fill('Entrega coordinada en el local.')
                await pagina.locator('#observaciones').blur()
                await pagina.evaluate('window.scrollTo(0,0)')
                texto_venta = await pagina.locator('app-punto-de-venta').inner_text()
                assert 'S/ 535.00' in texto_venta
                coincidencia = re.search(r'Siguiente:\s*([A-Z0-9]+-\d{8})', texto_venta)
                if not coincidencia:
                    raise AssertionError('No se encontró el siguiente correlativo visible en Venta.')
                numero_venta = coincidencia.group(1)
                await guardar('venta', 'app-punto-de-venta', completo=ancho < 1000)

                await pagina.goto(origen + '/ventas/documentos/NV/' + ID_NOTA, wait_until='networkidle')
                await pagina.get_by_label('Registro de venta', exact=True).wait_for()
                assert 'Cliente de demostración' in await pagina.locator('app-detalle-documento').inner_text()
                numero_registro = (await pagina.locator('app-detalle-documento h1').inner_text()).strip()
                await guardar('registro', 'app-detalle-documento', completo=ancho < 1000)

                await pagina.goto(origen + '/configuracion/identidad', wait_until='networkidle')
                await pagina.locator('app-identidad app-cabecera-comprobante img').wait_for()
                await pagina.wait_for_function('document.querySelector("app-identidad app-cabecera-comprobante img").naturalWidth > 0')
                if ancho < 1000:
                    await pagina.locator('app-identidad aside').scroll_into_view_if_needed()
                    await pagina.wait_for_timeout(100)
                await guardar('identidad', 'app-identidad', completo=False)
                archivo_preview = destino / f'previsualizacion-logo-{sufijo}.png'
                preview = pagina.locator('app-identidad aside')
                await pagina.evaluate('window.scrollTo(0,0)')
                caja_preview = await preview.bounding_box()
                await pagina.screenshot(path=str(archivo_preview), full_page=True,
                                        clip=caja_preview, animations='disabled')
                capturas.append({'archivo': archivo_preview.name, 'ruta': urlparse(pagina.url).path,
                                 'componente': 'app-identidad aside', 'recorteNativo': caja_preview,
                                 'sha256': hashlib.sha256(archivo_preview.read_bytes()).hexdigest()})

                # Vista documental adicional desde el botón nativo, con el logo guardado al emitir.
                await pagina.goto(origen + '/ventas/documentos/NV/' + ID_NOTA, wait_until='networkidle')
                await pagina.get_by_role('button', name='Previsualizar comprobante', exact=True).click()
                await pagina.get_by_role('button', name='A4', exact=True).click()
                await pagina.locator('article header img').wait_for()
                await pagina.wait_for_function('document.querySelector("article header img").naturalWidth > 0')
                await pagina.evaluate('window.scrollTo(0,0)')
                numero_documento = (await pagina.locator('article app-cabecera-comprobante .numero').inner_text()).strip()
                controles_recorrido.append({
                    'ancho': ancho, 'esperado': 'NV01-00000042',
                    'ventaSiguiente': numero_venta, 'registroNumero': numero_registro,
                    'documentoNumero': numero_documento,
                    'correcto': numero_venta == numero_registro == numero_documento == 'NV01-00000042',
                    'regla': 'Las tres capturas representan el mismo recorrido de la nota de demostración 42.',
                    'fuenteObservada': 'Texto visible del DOM Angular real: Siguiente, título de registro y cabecera documental.',
                })
                await guardar('comprobante-logo', 'app-detalle-documento article', completo=ancho < 1000)
                await navegador_contexto.close()
        finally:
            await navegador.close()

    fuentes = [
        'apps/frontend/ondexia.web/src/app/pages/ventas/punto-de-venta/punto-de-venta.component.html',
        'apps/frontend/ondexia.web/src/app/pages/ventas/documentos/detalle-documento.component.html',
        'apps/frontend/ondexia.web/src/app/pages/configuracion/identidad/identidad.component.html',
        'apps/frontend/ondexia.web/public/apple-touch-icon.png',
    ]
    evidencia = {
        'generadoEn': datetime.now(timezone.utc).isoformat(),
        'scriptSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'commit': subprocess.check_output(['git', '-C', str(RAIZ), 'rev-parse', 'HEAD'], text=True).strip(),
        'origen': origen, 'capturas': capturas, 'peticionesFixture': peticiones,
        'peticionesBloqueadas': bloqueadas, 'erroresJavascript': errores,
        'controlesRecorrido': controles_recorrido,
        'datos': 'Datos de demostración creados para esta captura; API y sesión sintéticas.',
        'interaccion': 'Búsqueda y selección nativas de productos/cliente; entrada nativa de cantidades y cobro.',
        'logo': 'Activo PNG existente de Ondexia, usado como logo del fixture demo; sin edición de imagen.',
        'captura': 'Chromium/Playwright del frontend Angular compilado; sin recreación de HTML ni modificación del DOM.',
        'servicios': 'Sin conexión a AWS, SUNAT ni base de datos; solo GET locales y fixtures de lista cerrada.',
        'limites': 'Demostración visual del frontend. No acredita emisión, escritura, autenticación real ni producción.',
        'fuentesSha256': {f: hashlib.sha256((RAIZ / f).read_bytes()).hexdigest() for f in fuentes},
    }
    if compilado:
        evidencia['compilado'] = str(compilado)
        evidencia['compiladoSha256'] = {
            str(f.relative_to(compilado)): hashlib.sha256(f.read_bytes()).hexdigest()
            for f in sorted(compilado.rglob('*')) if f.is_file() and f.suffix in {'.js', '.css', '.html'}
        }
    (destino / 'procedencia.json').write_text(json.dumps(evidencia, ensure_ascii=False, indent=2) + '\n')
    (destino / 'fixtures.json').write_text(serializar({'empresa': empresa, 'cliente': cliente,
                                                      'productos': productos, 'series': series,
                                                      'nota': nota, 'logos': logos}) + '\n')
    (destino / 'control-recorrido.json').write_text(
        json.dumps({'generadoEn': evidencia['generadoEn'], 'controles': controles_recorrido,
                    'correcto': all(c['correcto'] for c in controles_recorrido)},
                   ensure_ascii=False, indent=2) + '\n')
    if bloqueadas or errores:
        raise AssertionError(f'Captura incompleta: {len(bloqueadas)} peticiones bloqueadas; {len(errores)} errores JS.')
    if not all(c['correcto'] for c in controles_recorrido):
        raise AssertionError('El correlativo visible no coincide entre Venta, Registro y Documento. Ver control-recorrido.json.')
    print(json.dumps({'capturas': len(capturas), 'directorio': str(destino),
                      'peticionesExternas': 0, 'escriturasReales': 0}, ensure_ascii=False))


if __name__ == '__main__':
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument('--origen', default='http://127.0.0.1:8030')
    argumentos.add_argument('--destino', type=Path, required=True,
                            help='Directorio de evidencia fuera del repositorio.')
    argumentos.add_argument('--compilado', type=Path)
    parametros = argumentos.parse_args()
    asyncio.run(capturar(parametros.origen.rstrip('/'), parametros.destino, parametros.compilado))
