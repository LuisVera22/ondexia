"""Comprueba ventas a distintos anchos con API sintéticas, sin escrituras reales."""
import asyncio, base64, json, os, signal, subprocess, tempfile, time, urllib.request
from pathlib import Path
from decimal import Decimal
import websockets

EVIDENCIA = Path(os.environ.get('EVIDENCIA_MOVIL', '/workspace/ondexia/ondexia.docs/evidencias/2026-10-09-responsividad/verde'))
EVIDENCIA.mkdir(parents=True, exist_ok=True)

def serializar(valor):
    """Conserva decimales exactos como números JSON en las respuestas sintéticas."""
    if isinstance(valor, Decimal):
        return str(valor)
    if isinstance(valor, dict):
        return '{' + ','.join(json.dumps(clave) + ':' + serializar(dato) for clave, dato in valor.items()) + '}'
    if isinstance(valor, list):
        return '[' + ','.join(serializar(dato) for dato in valor) + ']'
    return json.dumps(valor, ensure_ascii=False)

async def comprobar():
    credencial = {'accessToken': 'credencial-sintetica', 'expiresIn': 3600}
    empresa = {'id': 'empresa-simulada', 'ruc': '20100000033', 'razonSocial': 'Ondexia S.A.C.', 'nombreComercial': 'Ondexia', 'rol': 'Administrador', 'regimenTributario': 'GENERAL', 'modoSunat': 'BETA', 'domicilioFiscal': 'Lima'}
    establecimientos = [{'id': 'local-simulado', 'codigo': '0000', 'nombre': 'Principal', 'direccion': 'Lima'}]
    contexto = {'usuario': {'id': 'usuario-simulado', 'nombre': 'Demostración', 'email': 'demo@example.test'}, 'cuenta': {'id': 'cuenta-simulada', 'esAdministrador': True, 'estadoSuscripcion': 'ACTIVA', 'soloLectura': False}, 'empresaActiva': empresa, 'empresas': [empresa], 'establecimientos': establecimientos,
        'permisos': ['ventas:acceder', 'ventas.caja:consultar', 'ventas.nota_venta:registrar', 'ventas.nota_venta:consultar', 'ventas.cliente:consultar', 'ventas.cliente:registrar', 'ventas.comprobante:emitir']}
    cajas = [{'id': 'caja-simulada', 'sucursalId': 'local-simulado', 'codigo': 'CAJA1', 'nombre': 'Caja mostrador', 'sesionAbierta': {'id': 'sesion-simulada', 'estado': 'ABIERTA'}}]
    nota = {'id': '00000000-0000-4000-8000-000000009901', 'tipo': 'NV', 'tipoNombre': 'Nota de venta', 'fiscal': False, 'serie': 'NV01', 'numero': 2,
        'numeroCompleto': 'NV01-00000002', 'estado': 'EMITIDO', 'sucursalId': cajas[0]['sucursalId'], 'sesionCajaId': 'sesion-simulada', 'cliente': {'nombre': 'CLIENTE SINTÉTICO '+ 'NOMBREEXTENSOSINESPACIOS'*6, 'tipoDocumento': 'RUC', 'numeroDocumento': '20512345671', 'direccion': 'Domicilio de prueba en Lima'},
        'fechaEmision': '2026-10-08', 'emitidoEn': '2026-10-08T18:00:00Z', 'emitidoPor': 'demo', 'moneda': 'PEN',
        'totalGravado': Decimal('381.36'), 'totalIgv': Decimal('68.64'), 'totalExonerado': 0, 'totalInafecto': 0, 'totalDescuento': 0, 'total': 450, 'observaciones': 'OBSERVACIONSINESPACIOS'*8,
        'origen': None, 'documentoOrigenId': None, 'motivo': None, 'motivoNombre': None,
        'pagos': [{'forma': 'EFECTIVO', 'monto': 450, 'referencia': None, 'entregado': None, 'vuelto': 0}],
        'lineas': [{'orden': 1, 'productoId': 'p-simulado', 'codigo': 'CAJ', 'descripcion': 'Cajas de envío '+ 'CODIGOEXTENSO'*10, 'unidad': 'NIU', 'cantidad': 3,
            'precioUnitario': 150, 'valorUnitario': Decimal('127.118644'), 'descuento': 0, 'afectacion': 'GRAVADO', 'valorVenta': Decimal('381.36'), 'igv': Decimal('68.64'), 'total': 450}]}
    consulta = {'datos': {'ruc': '20512345671', 'razonSocial': 'CLIENTE SINTÉTICO', 'domicilioFiscal': 'LIMA', 'aptaParaRegistro': True}, 'atestacion': 'atestacion-simulada'}
    sesion = {'acceso': credencial['accessToken'], 'refresco': None, 'expiraEn': int(time.time()*1000)+int(credencial['expiresIn'])*1000, 'correo': 'demo@ondexia.com', 'nombre': 'Demostración'}
    with tempfile.TemporaryDirectory(prefix='ondexia-ventas-', ignore_cleanup_errors=True) as perfil:
        entorno = dict(os.environ, XDG_CONFIG_HOME=perfil, XDG_CACHE_HOME=perfil)
        with (EVIDENCIA/'navegador.log').open('w') as registro:
            navegador = subprocess.Popen(['/usr/bin/chromium', '--headless', '--no-sandbox', '--disable-gpu', '--no-first-run', '--remote-debugging-port=9226', '--user-data-dir='+perfil, 'about:blank'], env=entorno, stdout=registro, stderr=registro, start_new_session=True)
            try:
                for _ in range(100):
                    try:
                        with urllib.request.urlopen('http://127.0.0.1:9226/json', timeout=1) as r: pagina = next(p for p in json.load(r) if p['type']=='page')
                        break
                    except Exception: await asyncio.sleep(.1)
                async with websockets.connect(pagina['webSocketDebuggerUrl'], max_size=10000000) as conexion:
                    secuencia=0; pendientes={}; errores=[]
                    async def llamar(metodo, parametros=None):
                        nonlocal secuencia
                        secuencia+=1; identificador=secuencia
                        futuro=asyncio.get_running_loop().create_future(); pendientes[identificador]=futuro
                        await conexion.send(json.dumps({'id':identificador,'method':metodo,'params':parametros or {}}))
                        return await asyncio.wait_for(futuro,30)
                    async def responder(evento):
                        peticion=evento['params']['request']; url=peticion['url']; metodo=peticion['method']; contenido=None
                        if metodo not in {'GET', 'OPTIONS'}: raise AssertionError('No se permiten escrituras reales: '+metodo+' '+url)
                        if '/contexto' in url: contenido=contexto
                        elif '/ventas/cajas' in url: contenido=cajas
                        elif '/relacionados' in url: contenido=[]
                        elif '/notas-de-venta/00000000-0000-4000-8000-000000009901' in url: contenido=nota
                        elif '/configuracion/empresa' in url: contenido=empresa
                        elif '/configuracion/establecimientos' in url: contenido=establecimientos
                        elif '/ventas/series' in url: contenido=[{'id': 'serie-simulada', 'serie': 'NV01', 'siguienteNumero': 'NV01-00000003'}]
                        elif '/consultas/ruc/' in url: contenido=consulta
                        else: contenido=[]
                        await llamar('Fetch.fulfillRequest',{'requestId':evento['params']['requestId'],'responseCode':201 if metodo=='POST' else 200,
                            'responseHeaders':[{'name':'Content-Type','value':'application/json'},{'name':'Access-Control-Allow-Origin','value':'http://localhost:9100'},
                                {'name':'Access-Control-Allow-Headers','value':'Authorization,Content-Type,X-Empresa-Id'},{'name':'Access-Control-Allow-Methods','value':'GET,POST,OPTIONS'}],
                            'body':base64.b64encode(serializar(contenido).encode()).decode()})
                    async def recibir():
                        async for mensaje in conexion:
                            evento=json.loads(mensaje)
                            if 'id' in evento:
                                futuro=pendientes.pop(evento['id'],None)
                                if futuro:
                                    if 'error' in evento: futuro.set_exception(RuntimeError(evento['error']))
                                    else: futuro.set_result(evento.get('result',{}))
                            elif evento.get('method')=='Fetch.requestPaused': asyncio.create_task(responder(evento))
                            elif evento.get('method')=='Runtime.exceptionThrown': errores.append(evento['params'])
                    receptor=asyncio.create_task(recibir())
                    async def evaluar(expresion):
                        resultado=await llamar('Runtime.evaluate',{'expression':expresion,'returnByValue':True})
                        if 'exceptionDetails' in resultado: raise RuntimeError(resultado['exceptionDetails'])
                        return resultado.get('result',{}).get('value')
                    async def esperar(expresion):
                        for _ in range(100):
                            if await evaluar(expresion): return
                            await asyncio.sleep(.1)
                        raise AssertionError(expresion)
                    await llamar('Page.enable'); await llamar('Runtime.enable')
                    await llamar('Emulation.setDeviceMetricsOverride',{'width':1440,'height':1100,'deviceScaleFactor':1,'mobile':False})
                    await llamar('Fetch.enable',{'patterns':[{'urlPattern':'*/api/v1/*'}, {'urlPattern':'*/consultas/ruc/*'}]})
                    await llamar('Page.navigate',{'url':'http://localhost:9100/config.json'})
                    await esperar("location.origin==='http://localhost:9100'")
                    await evaluar("sessionStorage.setItem('ondexia.sesion',"+json.dumps(json.dumps(sesion))+");sessionStorage.setItem('ondexia.empresa','00000000-0000-4000-8000-000000000010')")
                    resultados=[]; fallos=[]; impresiones=[]
                    async def revisar(nombre, ancho):
                        medidas=await evaluar("""(()=>{const raiz=document.documentElement; const contenedor=document.querySelector('app-detalle-documento, app-punto-de-venta'); const controles=[...contenedor.querySelectorAll('button,input,select,a')].filter(e=>e.getBoundingClientRect().width>0); return {ventana:innerWidth,pagina:raiz.scrollWidth,fuera:controles.filter(e=>{const r=e.getBoundingClientRect();return r.left < -1 || r.right > innerWidth+1}).map(e=>e.getAttribute('aria-label') || e.textContent.trim()),tablas:[...contenedor.querySelectorAll('table')].filter(e=>e.getBoundingClientRect().width>0).map(e=>({ancho:e.getBoundingClientRect().width,desborde:e.scrollWidth>e.clientWidth+1})),campos:[...contenedor.querySelectorAll('input,select')].filter(e=>e.getBoundingClientRect().width>0).map(e=>({alto:e.getBoundingClientRect().height,fuente:parseFloat(getComputedStyle(e).fontSize)}))};})()""")
                        correcto=medidas['pagina']<=ancho+1 and not medidas['fuera'] and not any(t['desborde'] for t in medidas['tablas'])
                        if ancho<640: correcto=correcto and all(c['alto']>=44 and c['fuente']>=16 for c in medidas['campos'])
                        resultados.append({'escenario':nombre,'ancho':ancho,'correcto':correcto,'medidas':medidas})
                        if not correcto: fallos.append(nombre+' '+str(ancho))
                        imagen=await llamar('Page.captureScreenshot',{'format':'png','captureBeyondViewport':False}); (EVIDENCIA/(nombre+'-'+str(ancho)+'.png')).write_bytes(base64.b64decode(imagen['data']))
                    for ancho in [320,390,768,1440]:
                        await llamar('Emulation.setDeviceMetricsOverride',{'width':ancho,'height':844,'deviceScaleFactor':1,'mobile':True})
                        await llamar('Page.navigate',{'url':'http://localhost:9100/ventas/documentos/NV/00000000-0000-4000-8000-000000009901'})
                        await esperar("!!document.querySelector('[aria-label=\"Registro de venta\"]') && document.querySelector('[aria-label=\"Registro de venta\"]').innerText.includes('450.00')")
                        await revisar('registro',ancho)
                        await evaluar("Array.from(document.querySelectorAll('button')).find(b=>b.textContent.includes('Previsualizar comprobante')).click()")
                        await esperar("document.querySelector('article').style.display!=='none'")
                        await revisar('ticket',ancho)
                        await evaluar("Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()==='A4').click()")
                        await revisar('a4',ancho)
                        for formato in ['ticket','a4']:
                            etiqueta='Ticket 80 mm' if formato=='ticket' else 'A4'
                            await evaluar("Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()==="+json.dumps(etiqueta)+").click()")
                            await llamar('Emulation.setEmulatedMedia',{'media':'print'})
                            impresion=await evaluar("(()=>{const hoja=document.querySelector('article');return {ancho:hoja.getBoundingClientRect().width,hoja:getComputedStyle(hoja).display,marco:getComputedStyle(document.querySelector('app-barra-superior')).display,texto:hoja.innerText,tarjetas:document.querySelectorAll('[aria-label=\"Registro de venta\"]').length};})()")
                            assert impresion['hoja']=='block' and impresion['marco']=='none' and impresion['tarjetas']==0, impresion
                            assert 'IGV' not in impresion['texto'] and impresion['texto'].count('S/ 450.00')>=2, impresion
                            if formato=='ticket': assert abs(impresion['ancho']-80*96/25.4)<1, impresion
                            impresiones.append({'ancho_ventana':ancho,'formato':formato,'correcto':True})
                            await llamar('Emulation.setEmulatedMedia',{'media':''})
                        await evaluar("Array.from(document.querySelectorAll('button')).find(b=>b.textContent.includes('Volver al registro')).click()")
                        await esperar("!!document.querySelector('[aria-label=\"Registro de venta\"]')")
                        await llamar('Emulation.setEmulatedMedia',{'media':'print'})
                        assert await evaluar("getComputedStyle(document.querySelector('[aria-label=\"Registro de venta\"]')).display==='none' && getComputedStyle(document.querySelector('article')).display==='block'")
                        await llamar('Emulation.setEmulatedMedia',{'media':''})
                        await llamar('Page.navigate',{'url':'http://localhost:9100/ventas/punto-de-venta'})
                        await esperar("!!document.querySelector('app-punto-de-venta aside')")
                        await evaluar("(()=>{const vista=ng.getComponent(document.querySelector('app-punto-de-venta'));vista.lineas.set([{productoId:'p-simulado',codigo:'CAJ',descripcion:'Cajas de envío '+'CODIGOEXTENSO'.repeat(10),unidad:'NIU',existencia:30,precio:150,cantidad:3,descuento:0}]);})()")
                        await esperar("!!document.querySelector('input[aria-label^=\"Cantidad de\"]')")
                        await revisar('venta',ancho)
                        await evaluar("Array.from(document.querySelectorAll('button')).find(b=>b.textContent.includes('Nuevo cliente')).click()")
                        await esperar("!!document.querySelector('[role=dialog]')")
                        await evaluar("(()=>{const campo=document.querySelector('input[formcontrolname=ruc]');campo.value='20512345671';campo.dispatchEvent(new Event('input',{bubbles:true}));Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()==='Consultar RUC').click();})()")
                        await esperar("!!document.querySelector('input[formcontrolname=nombre]')")
                        await asyncio.sleep(.4)
                        await revisar('alta-cliente',ancho)
                        assert await evaluar("Array.from(document.querySelectorAll('button')).some(b=>b.textContent.includes('Registrar y usar'))")
                        if ancho==390:
                            await llamar('Emulation.setDeviceMetricsOverride',{'width':390,'height':400,'deviceScaleFactor':1,'mobile':True})
                            await evaluar("document.querySelector('[role=dialog]').scrollTop=10000")
                            await revisar('alta-altura-reducida',390)
                            assert await evaluar("(()=>{const boton=Array.from(document.querySelectorAll('button')).find(b=>b.textContent.includes('Registrar y usar'));const r=boton.getBoundingClientRect();return r.top>=0 && r.bottom<=400;})()"), 'Registrar y usar queda fuera del área disponible'
                    (EVIDENCIA/'resultados.json').write_text(json.dumps({'resultados':resultados,'fallos':fallos,'impresiones':impresiones,'servicios_simulados':'Todas las API; sesión y datos sintéticos; no se emite ni se registra', 'dispositivos_fisicos':'no verificados'},ensure_ascii=False,indent=2)+'\n')
                    print(json.dumps({'escenarios':len(resultados),'fallos':fallos},ensure_ascii=False),flush=True)
                    assert not errores,errores
                    assert not fallos,fallos
                    receptor.cancel()
                    print('21 escenarios de pantalla y 8 de impresión correctos; sin escrituras reales.')
            finally:
                os.killpg(navegador.pid, signal.SIGTERM); navegador.wait(timeout=10)

asyncio.run(comprobar())
