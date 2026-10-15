"""Aceptación local de ventana cinematográfica, alternativa estática, ampliación y CSP.

Esperados independientes: tres PNG 1440 × 1000, cinco progresos y morph M/Q/Q/Q/Q/Z.
Requiere Playwright Python, Chromium y build servido con la cabecera Terraform.
No acredita AWS, servicios reales, dispositivos físicos ni conformidad WCAG.
"""
import argparse
import json
import re
import shutil
import struct
import sys
from pathlib import Path
from urllib.parse import urljoin, urlparse

from playwright.sync_api import expect, sync_playwright

ESCENAS = [
    ('Preparar la venta', 'venta-completa.png'),
    ('Revisar el registro', 'registro-completo.png'),
    ('Previsualizar el documento', 'documento-completo.png'),
]
NOMBRES_PASOS = [f'{i + 1:02d} {titulo}' for i, (titulo, _) in enumerate(ESCENAS)]
OPACIDADES = [(0, [1, 0, 0]), (.25, [.5, .5, 0]), (.5, [0, 1, 0]),
              (.75, [0, .5, .5]), (1, [0, 0, 1])]
CONTORNO_INICIAL = 'M80 40 Q720 0 1360 40 Q1440 500 1360 960 Q720 1000 80 960 Q0 500 80 40 Z'
PANTALLAS = [(320, 900), (390, 844), (768, 900), (1024, 900), (1440, 1000),
             (1440, 400), (320, 320), (360, 250), (390, 240), (320, 225)]


def exigir(condicion, mensaje):
    if not condicion:
        raise AssertionError(mensaje)


def dejar_pintar(pagina):
    pagina.evaluate('() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))')


def desplazarse(pagina, posicion):
    pagina.evaluate('y => window.scrollTo({top:y, behavior:"instant"})', posicion)
    dejar_pintar(pagina)


def activar_paso(pagina, indice, sin_script=False):
    enlace = pagina.locator('[data-paso]').nth(indice)
    if sin_script:
        # En ventanas bajas el nav es estático: el desplazamiento automático
        # nearest del driver puede situar el enlace debajo de la cabecera sticky.
        # Llegar al control mediante scroll no altera el ancla ni fuerza el clic.
        tope = pagina.locator('header').first.bounding_box()['height'] + 16
        posicion = enlace.evaluate('e => e.getBoundingClientRect().top + scrollY')
        pagina.evaluate('y => scrollTo({top:y,behavior:"instant"})', posicion - tope)
        pagina.wait_for_timeout(50)
    enlace.click()
    # La navegación puede usar scroll suave nativo. Esperar su destino no debe
    # convertir un fotograma intermedio en el esperado del paso seleccionado.
    if sin_script:
        anterior, quietos = pagina.evaluate('scrollY'), 0
        for _ in range(60):
            pagina.wait_for_timeout(50)
            actual = pagina.evaluate('scrollY')
            quietos = quietos + 1 if abs(actual - anterior) < .1 else 0
            anterior = actual
            if quietos >= 4:
                return
        raise AssertionError('El desplazamiento nativo sin JavaScript no se estabiliza')
    pagina.evaluate('''() => new Promise((resolver,rechazar) => {
      let anterior=scrollY,quietos=0;const inicio=performance.now();
      function observar(){
        quietos=Math.abs(scrollY-anterior)<.1?quietos+1:0;anterior=scrollY;
        if(performance.now()-inicio>150&&quietos>=4)resolver();
        else if(performance.now()-inicio>3000)rechazar(new Error('El desplazamiento manual no se estabiliza'));
        else requestAnimationFrame(observar);
      }
      requestAnimationFrame(observar);
    })''')
    dejar_pintar(pagina)


def direccion_local(direccion):
    url = urlparse(direccion)
    exigir(url.scheme == 'http' and url.hostname in {'127.0.0.1', 'localhost', '::1'}
           and not url.username and not url.password, 'Solo se admite HTTP local sin credenciales')


def contexto_local(navegador, direccion, **opciones):
    contexto = navegador.new_context(service_workers='block', **opciones)
    origen = urlparse(direccion)
    externas, errores = [], []

    def interceptar(ruta):
        url = urlparse(ruta.request.url)
        if (url.scheme, url.netloc) != (origen.scheme, origen.netloc):
            externas.append({'tipo': 'http', 'destino': ruta.request.url})
            ruta.abort('blockedbyclient')
        else:
            ruta.continue_()

    def bloquear_socket(socket):
        externas.append({'tipo': 'websocket', 'destino': socket.url})
        socket.close()

    contexto.route('**/*', interceptar)
    contexto.route_web_socket('**/*', bloquear_socket)
    contexto.on('page', lambda pagina: pagina.on('pageerror', lambda error: errores.append(str(error))))
    contexto.set_default_timeout(3500)
    contexto.set_default_navigation_timeout(15000)
    return contexto, externas, errores


def abrir_pagina(contexto, direccion, sin_script=False):
    pagina = contexto.new_page()
    respuesta = pagina.goto(direccion, wait_until='networkidle')
    exigir(respuesta and respuesta.status == 200, 'El build local debe responder HTTP 200')
    if not sin_script:
        pagina.evaluate('document.fonts.ready')
        dejar_pintar(pagina)
    return pagina, respuesta


def navegacion(pagina):
    return pagina.get_by_role('navigation', name='Recorrido del sistema', exact=True)


def comprobar_enlaces(pagina):
    enlaces = pagina.locator('[data-paso]')
    expect(enlaces).to_have_count(3)
    expect(navegacion(pagina).locator('[data-paso]')).to_have_count(3)
    for indice, nombre in enumerate(NOMBRES_PASOS):
        expect(enlaces.nth(indice)).to_have_attribute('aria-label', nombre)
        expect(enlaces.nth(indice)).to_have_accessible_name(nombre)
        destino = enlaces.nth(indice).get_attribute('href')
        exigir(destino and destino.startswith('#'), 'Cada paso conserva un ancla nativa')
        expect(pagina.locator(destino)).to_have_count(1)


def comprobar_imagenes_escritorio(pagina, direccion):
    """También se aplica al build anterior, sin aceptar sus variantes móviles."""
    escenas = pagina.locator('[data-explorador] article[data-escena]')
    expect(escenas).to_have_count(3)
    expect(pagina.locator('[data-explorador] source')).to_have_count(0)
    for indice, (_, archivo) in enumerate(ESCENAS):
        imagen = escenas.nth(indice).locator('img')
        expect(imagen).to_have_count(1)
        esperado = urljoin(direccion, '/capturas/' + archivo)
        exigir(urljoin(direccion, imagen.get_attribute('src') or '') == esperado,
               f'La escena {indice + 1} debe usar {archivo} en todos los anchos')
        original = escenas.nth(indice).locator('a[href$=".png"]')
        expect(original).to_have_count(1)
        exigir(urljoin(direccion, original.get_attribute('href') or '') == esperado,
               'El original debe conservar el PNG completo de escritorio')
        respuesta = pagina.request.get(esperado)
        cuerpo = respuesta.body()
        exigir(respuesta.status == 200 and cuerpo[:8] == b'\x89PNG\r\n\x1a\n', f'No se pudo cargar {archivo}')
        exigir(len(cuerpo) >= 24 and struct.unpack('>II', cuerpo[16:24]) == (1440, 1000),
               f'{archivo} debe medir realmente 1440 × 1000')
    return {'archivos': [archivo for _, archivo in ESCENAS], 'pixeles': [1440, 1000]}


def contorno(pagina):
    nodo = pagina.locator('[data-contorno]')
    expect(nodo).to_have_count(1)
    return nodo if nodo.evaluate('e => e.localName === "path"') else nodo.locator('path')


def analizar_contorno(valor):
    patron = r'[A-Za-z]|[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?'
    tokens = re.findall(patron, valor or '')
    exigir(not re.sub(patron, '', valor or '').strip(' ,\n\r\t'), 'Sintaxis SVG no válida')
    exigir([t for t in tokens if t.isalpha()] == ['M', 'Q', 'Q', 'Q', 'Q', 'Z'],
           'El morph debe conservar número, tipo y orden M/Q/Q/Q/Q/Z')
    numeros, posicion = [], 0
    for comando, cantidad in [('M', 2), ('Q', 4), ('Q', 4), ('Q', 4), ('Q', 4), ('Z', 0)]:
        exigir(posicion < len(tokens) and tokens[posicion] == comando, 'Orden SVG incorrecto')
        posicion += 1
        bloque = tokens[posicion:posicion + cantidad]
        exigir(len(bloque) == cantidad and not any(t.isalpha() for t in bloque),
               'Cada comando SVG debe conservar su número de coordenadas')
        numeros.extend(float(t) for t in bloque)
        posicion += cantidad
    exigir(posicion == len(tokens), 'Coordenadas SVG adicionales')
    return numeros


def estado_visual(pagina):
    capas = pagina.locator('[data-capa]')
    expect(capas).to_have_count(3)
    camino = contorno(pagina)
    expect(camino).to_have_count(1)
    exigir(camino.evaluate('e => e.namespaceURI === "http://www.w3.org/2000/svg" && !!e.ownerSVGElement'),
           'El contorno debe ser un path SVG real')
    return {'opacidades': capas.evaluate_all('xs => xs.map(x => Number(getComputedStyle(x).opacity))'),
            'contorno': analizar_contorno(camino.get_attribute('d'))}


def comprobar_estado(estado, progreso):
    esperado = next(opacidades for punto, opacidades in OPACIDADES if punto == progreso)
    exigir(all(abs(actual - valor) <= .03 for actual, valor in zip(estado['opacidades'], esperado)),
           f'Progreso {progreso}: {estado["opacidades"]}; esperado {esperado} ±0.03')
    if progreso == 0:
        exigir(max(abs(a - b) for a, b in zip(estado['contorno'], analizar_contorno(CONTORNO_INICIAL))) <= .03,
               'El contorno inicial difiere de las coordenadas del contrato')


def situar_progreso(pagina, progreso):
    expect(pagina.locator('[data-recorrido]')).to_have_count(1)
    expect(pagina.locator('[data-escenario]')).to_have_count(1)
    medidas = pagina.locator('[data-recorrido]').evaluate('''e => {
      const escenario=document.querySelector('[data-escenario]');
      const caja=e.getBoundingClientRect(), marco=escenario.getBoundingClientRect();
      return {inicio:caja.top+scrollY-parseFloat(getComputedStyle(escenario).top),
              distancia:caja.height-marco.height, posicion:getComputedStyle(escenario).position};
    }''')
    exigir(medidas['posicion'] == 'sticky' and medidas['distancia'] > 0,
           'Se requiere escenario sticky y recorrido positivo')
    destino = medidas['inicio'] + medidas['distancia'] * progreso
    desplazarse(pagina, destino)
    exigir(abs(pagina.evaluate('scrollY') - destino) <= 2, 'El scroll no permite alcanzar el progreso contractual')
    return estado_visual(pagina)


def comprobar_cinematica(pagina, direccion):
    expect(pagina.locator('[data-escenas]')).to_be_hidden()
    expect(pagina.locator('[data-escenario]')).to_be_visible()
    expect(pagina.locator('[data-titulo-actual]')).to_have_count(1)
    exigir(pagina.locator('[data-titulo-actual]').evaluate('e => e.localName === "h3"'), 'El título compartido debe ser h3')
    expect(pagina.locator('[data-descripcion-actual]')).to_have_count(1)
    expect(pagina.locator('[data-visual]')).to_have_attribute('role', 'img')
    expect(pagina.locator('[data-visual]')).to_have_accessible_name(re.compile(r'\S'))
    expect(pagina.locator('[data-capa]')).to_have_count(3)
    for indice, (_, archivo) in enumerate(ESCENAS):
        capa = pagina.locator('[data-capa]').nth(indice)
        imagen = capa if capa.evaluate('e => e.localName === "img"') else capa.locator('img')
        expect(imagen).to_have_count(1)
        exigir(urljoin(direccion, imagen.get_attribute('src') or '') == urljoin(direccion, '/capturas/' + archivo),
               'Cada capa debe usar la captura completa de escritorio correspondiente')


def comprobar_progresos(pagina, direccion):
    """Cinco puntos contractuales, pausa y reversa; no basta con una animación de entrada."""
    comprobar_cinematica(pagina, direccion)
    estados = {}
    for progreso, _ in OPACIDADES:
        estado = situar_progreso(pagina, progreso)
        comprobar_estado(estado, progreso)
        estados[progreso] = estado
        if progreso in {0, .5, 1}:
            indice = round(progreso * 2)
            expect(pagina.locator('[data-titulo-actual]')).to_have_text(ESCENAS[indice][0])
            expect(pagina.locator('[data-progreso]')).to_have_text(f'{indice + 1:02d} / 03')
            exigir(pagina.locator('[data-descripcion-actual]').inner_text().strip(), 'La explicación actual está vacía')
    exigir(estados[0]['contorno'] != estados[.5]['contorno']
           and estados[.5]['contorno'] != estados[1]['contorno']
           and estados[0]['contorno'] != estados[1]['contorno'], 'El morph no cambia en el centro y al final')
    pagina.wait_for_timeout(300)
    exigir(estado_visual(pagina) == estados[1], 'La escena cambia aunque el scroll esté pausado')
    for progreso in [.75, .5, .25, 0]:
        reversa = situar_progreso(pagina, progreso)
        comprobar_estado(reversa, progreso)
        exigir(max(abs(a - b) for a, b in zip(reversa['contorno'], estados[progreso]['contorno'])) <= .03,
               'La reversa no reproduce el contorno de la misma posición')
        exigir(max(abs(a - b) for a, b in zip(reversa['opacidades'], estados[progreso]['opacidades'])) <= .001,
               'La reversa no reproduce las opacidades de la misma posición')
    return [{'progreso': progreso, **estados[progreso]} for progreso, _ in OPACIDADES]


def comprobar_mutaciones(pagina, direccion):
    capa = pagina.locator('[data-capa]').first
    estilo = capa.get_attribute('style')
    detectada = False
    try:
        capa.evaluate('e => e.style.setProperty("opacity", "0", "important")')
        try:
            comprobar_estado(situar_progreso(pagina, 0), 0)
        except AssertionError:
            detectada = True
        exigir(detectada, 'El oráculo no detectó opacity:0!important')
    finally:
        capa.evaluate('(e,v) => v === null ? e.removeAttribute("style") : e.setAttribute("style",v)', estilo)
    comprobar_estado(situar_progreso(pagina, 0), 0)
    camino = contorno(pagina)
    original = camino.get_attribute('d')
    detectada = False
    try:
        camino.evaluate('''(e,d) => {
          window.mutacionContornoAceptacion=new MutationObserver(()=>{if(e.getAttribute('d')!==d)e.setAttribute('d',d)});
          window.mutacionContornoAceptacion.observe(e,{attributes:true,attributeFilter:['d']});
          e.setAttribute('d',d);
        }''', CONTORNO_INICIAL)
        try:
            comprobar_progresos(pagina, direccion)
        except AssertionError:
            detectada = True
        exigir(detectada, 'Los mismos oráculos no detectaron el morph fijado al contorno inicial')
    finally:
        pagina.evaluate('() => {window.mutacionContornoAceptacion?.disconnect();delete window.mutacionContornoAceptacion}')
        camino.evaluate('(e,d) => e.setAttribute("d",d)', original)
    comprobar_progresos(pagina, direccion)
    return {'opacidadDetectadaYRestaurada': True, 'morphForzadoDetectadoYRestaurado': True}


def comprobar_entrada_nativa(pagina):
    situar_progreso(pagina, .5)
    pagina.evaluate('''() => {
      window.entradasAceptacion=[];
      for(const tipo of ['wheel','keydown'])window.addEventListener(tipo,evento=>{
        if(tipo==='keydown'&&!['ArrowDown','PageDown','ArrowUp'].includes(evento.key))return;
        setTimeout(()=>window.entradasAceptacion.push({tipo,impedido:evento.defaultPrevented}),0);
      },true);
    }''')
    escenario = pagina.locator('[data-escenario]').bounding_box()
    pagina.mouse.move(escenario['x'] + escenario['width'] / 2, escenario['y'] + escenario['height'] / 2)
    anterior = pagina.evaluate('scrollY')
    pagina.mouse.wheel(0, 120)
    pagina.wait_for_function('y => scrollY > y', arg=anterior)
    pagina.locator('[data-titulo-actual]').focus()
    anterior = pagina.evaluate('scrollY')
    pagina.keyboard.press('PageDown')
    pagina.wait_for_function('y => scrollY > y', arg=anterior)
    pagina.wait_for_timeout(150)
    eventos = pagina.evaluate('window.entradasAceptacion')
    exigir({e['tipo'] for e in eventos} == {'wheel', 'keydown'} and not any(e['impedido'] for e in eventos),
           f'Se impidió el desplazamiento nativo de wheel o teclado: {eventos}')
    return {'wheelNativo': True, 'tecladoNativo': True}


def comprobar_titulo_descubierto(pagina, titulo):
    caja = titulo.bounding_box()
    exigir(caja and caja['y'] >= -1 and caja['y'] + caja['height'] <= pagina.viewport_size['height'] + 1,
           'El título no cabe en la ventana')
    for obstaculo in [pagina.locator('header').first, navegacion(pagina)]:
        if not obstaculo.is_visible():
            continue
        borde = obstaculo.bounding_box()
        cruce_x = min(caja['x'] + caja['width'], borde['x'] + borde['width']) - max(caja['x'], borde['x'])
        cruce_y = min(caja['y'] + caja['height'], borde['y'] + borde['height']) - max(caja['y'], borde['y'])
        exigir(cruce_x <= 1 or cruce_y <= 1, 'La cabecera o navegación cubre el título')


def comprobar_fallback(pagina, reducido=False):
    expect(pagina.locator('[data-escenas]')).to_be_visible()
    expect(pagina.locator('[data-escenas] article[data-escena]:visible')).to_have_count(3)
    expect(pagina.locator('[data-progreso]')).to_have_text('3 escenas')
    expect(pagina.locator('[data-escenario]')).to_be_hidden()
    exigir(pagina.locator('[data-escenario]').evaluate('e => getComputedStyle(e).position') != 'sticky',
           'La alternativa estática debe desactivar la fijación del escenario')
    if reducido:
        exigir(pagina.locator('[data-explorador]').evaluate('e => e.getAnimations({subtree:true}).length') == 0,
               'Quedan animaciones activas con movimiento reducido')
        exigir(pagina.evaluate('getComputedStyle(document.documentElement).scrollBehavior') == 'auto',
               'El movimiento reducido debe desactivar el desplazamiento suave')


def comprobar_navegacion(pagina, cinematica, sin_script=False):
    comprobar_enlaces(pagina)
    for indice, (titulo, _) in enumerate(ESCENAS):
        enlace = pagina.locator('[data-paso]').nth(indice)
        activar_paso(pagina, indice, sin_script=sin_script)
        if cinematica:
            encabezado = pagina.locator('[data-titulo-actual]')
            expect(encabezado).to_have_text(titulo)
            expect(encabezado).to_be_focused()
            comprobar_estado(estado_visual(pagina), indice / 2)
        else:
            escena = pagina.locator('[data-escenas] article[data-escena]').nth(indice)
            encabezado = escena.locator('h3')
            if not sin_script:
                expect(escena).to_be_focused()
            else:
                exigir(urlparse(pagina.url).fragment == (enlace.get_attribute('href') or '')[1:],
                       'Sin JavaScript, el paso debe navegar a su ancla nativa')
        if not sin_script:
            expect(enlace).to_have_attribute('aria-current', 'step')
        comprobar_titulo_descubierto(pagina, encabezado)


def control_original(pagina, indice, compartido):
    return (pagina.locator('[data-original-actual]') if compartido else
            pagina.locator('[data-escenas] article[data-escena]').nth(indice).locator('a[href$=".png"]'))


def comprobar_ampliacion(pagina, direccion, compartido, ancho=390, alto=844):
    pagina.set_viewport_size({'width': ancho, 'height': alto})
    dejar_pintar(pagina)
    for indice, (titulo, archivo) in enumerate(ESCENAS):
        activar_paso(pagina, indice)
        abrir = (pagina.locator('[data-ampliar-actual]') if compartido else
                 pagina.locator('[data-escenas] article[data-escena]').nth(indice).locator('[data-ampliar]'))
        esperado = urljoin(direccion, '/capturas/' + archivo)
        exigir(urljoin(direccion, control_original(pagina, indice, compartido).get_attribute('href') or '') == esperado,
               'El original actual no sigue la escena seleccionada')
        for repeticion in range(2):
            abrir.click()
            dialogo = pagina.locator('[data-dialogo]')
            expect(dialogo).to_be_visible()
            expect(dialogo).to_have_accessible_name(titulo)
            expect(pagina.locator('[data-cerrar]')).to_be_focused()
            imagen = pagina.locator('[data-captura-ampliada]')
            expect(imagen).to_have_js_property('currentSrc', esperado)
            expect(imagen).to_have_js_property('naturalWidth', 1440)
            expect(imagen).to_have_js_property('naturalHeight', 1000)
            boton = pagina.locator('[data-tamano-real]')
            expect(boton).to_have_attribute('aria-pressed', 'false')
            expect(boton).to_have_accessible_name('Ver tamaño real')
            lienzo = pagina.locator('[data-lienzo-ampliado]')
            medidas = lienzo.evaluate('e => ({x:e.scrollLeft,y:e.scrollTop,ancho:e.clientWidth,alto:e.clientHeight,anchoTotal:e.scrollWidth,altoTotal:e.scrollHeight,desborde:getComputedStyle(e).overflow})')
            exigir(medidas['x'] == medidas['y'] == 0 and dialogo.evaluate('e => e.scrollTop') == 0,
                   'La apertura no restableció el diálogo y los dos ejes del lienzo')
            exigir(medidas['desborde'] == 'auto', 'El lienzo debe permitir desplazamiento en ambos ejes')
            exigir(imagen.bounding_box()['width'] <= medidas['ancho'] + 1, 'La apertura debe ajustar la imagen')
            exigir(medidas['anchoTotal'] <= medidas['ancho'] + 1 and medidas['altoTotal'] <= medidas['alto'] + 1,
                   'Ajustar a ventana debe presentar la captura completa sin overflow del lienzo')
            exigir(urljoin(direccion, dialogo.locator('a[href$=".png"]').get_attribute('href') or '') == esperado,
                   'El diálogo debe conservar el original nativo correcto')
            boton.click()
            expect(boton).to_have_attribute('aria-pressed', 'true')
            expect(boton).to_have_accessible_name('Ajustar a ventana')
            dejar_pintar(pagina)
            ancho_real = imagen.bounding_box()['width']
            exigir(abs(ancho_real - 1440) <= 1, f'Tamaño real debe mostrar 1440 píxeles CSS; observado {ancho_real}')
            lienzo.evaluate('e => {e.scrollLeft=200;e.scrollTop=150}')
            exigir(lienzo.evaluate('e => e.scrollWidth>e.clientWidth && e.scrollLeft>0 && e.scrollTop>0'),
                   'La captura real no permite recorrido horizontal y vertical en móvil')
            cierre = pagina.locator('[data-cerrar]').bounding_box()
            exigir(cierre['y'] >= 0 and cierre['y'] + cierre['height'] <= alto, 'Cerrar quedó fuera de la ventana')
            # close se despacha después de retirar open y del retorno nativo del
            # foco. Esperar su manejador evita pasar al siguiente caso mientras
            # la aplicación todavía puede restaurar el foco del diálogo anterior.
            dialogo.evaluate('''e => {
              window.cierreDialogoAceptacion=false;
              e.addEventListener('close',()=>{window.cierreDialogoAceptacion=true},{once:true});
            }''')
            if repeticion == 0:
                pagina.keyboard.press('Escape')
            else:
                boton.click()
                dejar_pintar(pagina)
                expect(boton).to_have_attribute('aria-pressed', 'false')
                expect(boton).to_have_accessible_name('Ver tamaño real')
                pagina.locator('[data-cerrar]').click()
            expect(dialogo).to_be_hidden()
            pagina.wait_for_function('() => window.cierreDialogoAceptacion === true')
            expect(abrir).to_be_focused()
    return {'ancho': ancho, 'alto': alto, 'escenas': 3, 'aperturasPorEscena': 2,
            'ajusteInicialSinOverflow': True, 'tamanoReal': 1440, 'pan': True}


def comprobar_foco_resize(pagina, direccion):
    for tipo in ['ampliar', 'original']:
        pagina.locator('[data-paso]').first.focus()
        pagina.set_viewport_size({'width': 320, 'height': 900})
        pagina.emulate_media(reduced_motion='no-preference')
        dejar_pintar(pagina)
        comprobar_cinematica(pagina, direccion)
        activar_paso(pagina, 1)
        pagina.locator('[data-ampliar-actual]' if tipo == 'ampliar' else '[data-original-actual]').focus()
        pagina.set_viewport_size({'width': 320, 'height': 250})
        dejar_pintar(pagina)
        escena = pagina.locator('[data-escenas] article[data-escena]').nth(1)
        equivalente = escena.locator('[data-ampliar]' if tipo == 'ampliar' else 'a[href$=".png"]')
        expect(equivalente).to_be_focused()
        caja = equivalente.bounding_box()
        exigir(caja and caja['x'] >= 0 and caja['y'] >= 0
               and caja['x'] + caja['width'] <= 321 and caja['y'] + caja['height'] <= 251,
               f'Al reducir a 320 × 250, el control {tipo} debe quedar completamente visible: {caja}')
    return {'ventana': [320, 250], 'controlesCompletamenteVisibles': ['ampliar', 'original']}


def comprobar_reduccion_dinamica(pagina, direccion):
    pagina.set_viewport_size({'width': 390, 'height': 844})
    for tipo in ['ampliar', 'original', 'titulo']:
        # Permitir reactivar el escenario después de abandonar el control estático,
        # sin exigir que se oculte un artículo que todavía conserva el foco.
        pagina.locator('[data-paso]').first.focus()
        pagina.emulate_media(reduced_motion='no-preference')
        dejar_pintar(pagina)
        comprobar_cinematica(pagina, direccion)
        activar_paso(pagina, 1)
        pagina.locator({'ampliar': '[data-ampliar-actual]', 'original': '[data-original-actual]',
                        'titulo': '[data-titulo-actual]'}[tipo]).focus()
        pagina.emulate_media(reduced_motion='reduce')
        dejar_pintar(pagina)
        comprobar_fallback(pagina, reducido=True)
        escena = pagina.locator('[data-escenas] article[data-escena]').nth(1)
        equivalente = (escena.locator('[data-ampliar]') if tipo == 'ampliar' else
                       escena.locator('a[href$=".png"]') if tipo == 'original' else escena)
        expect(equivalente).to_be_focused()
    comprobar_navegacion(pagina, cinematica=False)
    comprobar_ampliacion(pagina, direccion, compartido=False)
    pagina.locator('[data-paso]').first.focus()
    pagina.emulate_media(reduced_motion='no-preference')
    dejar_pintar(pagina)
    comprobar_cinematica(pagina, direccion)
    expect(pagina.locator('[data-detener]')).to_have_accessible_name('Ver sin animación')
    pagina.locator('[data-detener]').click()
    comprobar_fallback(pagina)
    exigir(pagina.evaluate('''() => {
      const e=document.activeElement;
      return !!e && !e.closest('[hidden]') && !!e.getClientRects().length && getComputedStyle(e).visibility!=='hidden';
    }'''), 'Detener dejó el foco en un control oculto')
    pagina.emulate_media(reduced_motion='reduce')
    dejar_pintar(pagina)
    pagina.emulate_media(reduced_motion='no-preference')
    dejar_pintar(pagina)
    pagina.locator('[data-paso]').first.focus()
    dejar_pintar(pagina)
    comprobar_fallback(pagina)
    return {'focoEquivalente': ['ampliar', 'original', 'titulo'], 'retornoCinematica': True, 'detencionPersiste': True}


def comprobar_csp(pagina, contexto, direccion, respuesta):
    politica = respuesta.headers.get('content-security-policy', '')
    directivas = {t.strip().split()[0]: t.strip().split()[1:] for t in politica.split(';') if t.strip()}
    exigir(directivas.get('script-src') == ["'self'"], 'La CSP servida debe permitir únicamente script-src self')
    exigir(directivas.get('default-src') == ["'none'"], 'La CSP debe fallar cerrada')
    scripts = pagina.locator('script:not([type="application/ld+json"])').all()
    exigir(scripts and all(s.get_attribute('src') and s.get_attribute('src').startswith('/_astro/') for s in scripts),
           'Los scripts ejecutables deben ser archivos Astro locales')
    sonda = urljoin(direccion, '/sonda-aceptacion-csp.js')
    contexto.route(sonda, lambda ruta: ruta.fulfill(content_type='application/javascript', body='''
      window.localAceptacion=true;
      try {eval('window.evalAceptacion=true')} catch(e) {window.evalErrorAceptacion=e.name}
    '''))
    url = urlparse(direccion)
    otro_origen = f'http://127.0.0.1:{9 if url.port != 9 else 8}/'
    pagina.evaluate('''({sonda,otroOrigen}) => {
      window.violacionesAceptacion=[];
      document.addEventListener('securitypolicyviolation',e=>window.violacionesAceptacion.push({directiva:e.effectiveDirective,bloqueado:e.blockedURI}));
      const inline=document.createElement('script');inline.textContent='window.inlineAceptacion=true';document.head.append(inline);
      const local=document.createElement('script');local.src=sonda;document.head.append(local);
      const remoto=document.createElement('script');remoto.src=otroOrigen+'sonda-script.js';document.head.append(remoto);
      const marco=document.createElement('iframe');marco.src=otroOrigen+'sonda-marco';document.body.append(marco);
      fetch('/sonda-conexion-no-permitida').catch(()=>{});
      fetch(otroOrigen+'sonda-conexion').catch(()=>{});
    }''', {'sonda': sonda, 'otroOrigen': otro_origen})
    pagina.wait_for_function('''() => window.localAceptacion && window.evalErrorAceptacion==='EvalError'
      && window.violacionesAceptacion.some(e=>e.directiva==='script-src-elem'&&e.bloqueado==='inline')
      && window.violacionesAceptacion.some(e=>e.directiva==='script-src-elem'&&e.bloqueado.startsWith('http:'))
      && window.violacionesAceptacion.some(e=>e.directiva==='script-src'&&e.bloqueado==='eval')
      && window.violacionesAceptacion.some(e=>e.directiva==='frame-src')
      && window.violacionesAceptacion.filter(e=>e.directiva==='connect-src').length>=2''')
    exigir(pagina.evaluate('window.inlineAceptacion===undefined && window.evalAceptacion===undefined'),
           'Se ejecutó código inline o eval')
    return {'scriptLocal': True, 'inlineRechazado': True, 'evalRechazado': True,
            'conexionesLocalYOtroOrigenRechazadas': True, 'scriptOtroOrigenRechazado': True, 'iframeRechazado': True}


def comprobar_base(navegador, direccion):
    resultados = {}
    for nombre, prueba in [('desktop', comprobar_imagenes_escritorio), ('progresos', comprobar_progresos)]:
        contexto, externas, errores = contexto_local(navegador, direccion, viewport={'width': 1440, 'height': 1000},
                                                     reduced_motion='no-preference')
        try:
            pagina, _ = abrir_pagina(contexto, direccion)
            try:
                prueba(pagina, direccion)
            except AssertionError as fallo:
                resultados[nombre] = {'estado': 'falloEsperado', 'motivo': str(fallo)[:450]}
            else:
                raise AssertionError(f'El build anterior pasó inesperadamente el mismo oráculo {nombre}')
            exigir(not externas and not errores, 'El fallo base debe venir del oráculo, no de red o JavaScript roto')
        finally:
            contexto.close()
    return resultados


def comprobar_estatica(navegador, direccion):
    contexto, externas, errores = contexto_local(navegador, direccion, java_script_enabled=False,
                                                 viewport={'width': 390, 'height': 844})
    try:
        pagina, _ = abrir_pagina(contexto, direccion, sin_script=True)
        comprobar_imagenes_escritorio(pagina, direccion)
        expect(pagina.locator('[data-escenas] article[data-escena]:visible')).to_have_count(3)
        expect(pagina.locator('[data-recorrido]')).to_be_hidden()
        expect(pagina.locator('[data-ampliar]:visible, [data-ampliar-actual]:visible, [data-detener]:visible')).to_have_count(0)
        for ancho, alto in [(390, 844), (320, 225), (360, 250), (390, 240)]:
            pagina.set_viewport_size({'width': ancho, 'height': alto})
            comprobar_navegacion(pagina, cinematica=False, sin_script=True)
            exigir(pagina.evaluate('document.documentElement.scrollWidth') <= ancho, 'Desbordamiento sin JavaScript')
        exigir(not externas and not errores, 'El fallback consulta red externa o produce errores')
        return {'tresEscenas': True, 'anclasNativas': True, 'originalesDesktop': True, 'alturasBajas': True}
    finally:
        contexto.close()


def comprobar_politica_anterior(navegador, direccion):
    contexto, externas, errores = contexto_local(navegador, direccion, viewport={'width': 390, 'height': 844})
    try:
        pagina, respuesta = abrir_pagina(contexto, direccion)
        politica = respuesta.headers.get('content-security-policy', '')
        exigir("default-src 'none'" in politica and 'script-src' not in politica, 'La dirección no sirve la CSP anterior')
        expect(pagina.locator('[data-escenas] article[data-escena]:visible')).to_have_count(3)
        expect(pagina.locator('[data-ampliar]:visible, [data-ampliar-actual]:visible, [data-detener]:visible')).to_have_count(0)
        comprobar_imagenes_escritorio(pagina, direccion)
        comprobar_navegacion(pagina, cinematica=False, sin_script=True)
        exigir(not externas and not errores, 'La CSP anterior no conserva el HTML sin fallos de preparación')
        return {'scriptsBloqueados': True, 'anclasYOriginalesConservados': True}
    finally:
        contexto.close()


def comprobar_sin_capacidades(navegador, direccion):
    resultados = []
    for nombre, preparacion in [
        ('sinAnimate', 'Object.defineProperty(Element.prototype,"animate",{value:undefined,configurable:true})'),
        ('sinUnidadesYProporcion', 'Object.defineProperty(CSS,"supports",{value:()=>false,configurable:true})'),
    ]:
        contexto, externas, errores = contexto_local(navegador, direccion,
                                                     viewport={'width': 1440, 'height': 1000},
                                                     reduced_motion='no-preference')
        try:
            contexto.add_init_script(preparacion)
            pagina, _ = abrir_pagina(contexto, direccion)
            comprobar_fallback(pagina)
            comprobar_imagenes_escritorio(pagina, direccion)
            comprobar_navegacion(pagina, cinematica=False)
            exigir(not externas and not errores, 'La ausencia de capacidades debe conservar una alternativa operativa')
            resultados.append({'capacidad': nombre, 'tresEscenas': True, 'navegacion': True, 'sinErrores': True})
        finally:
            contexto.close()
    return resultados


def comprobar(direccion, anterior, base, destino):
    destino.mkdir(parents=True, exist_ok=True)
    resultado = {'estado': 'enCurso', 'url': direccion, 'etapa': 'preparacion', 'noVerificado':
                 ['AWS', 'SUNAT', 'API real', 'CI/SCA', 'dispositivos físicos', 'conformidad WCAG completa']}
    try:
        with sync_playwright() as p:
            ejecutable = shutil.which('chromium') or shutil.which('chromium-browser')
            exigir(ejecutable, 'Chromium debe estar instalado')
            navegador = p.chromium.launch(executable_path=ejecutable, args=['--no-sandbox', '--disable-background-networking'])
            try:
                if base:
                    resultado['etapa'] = 'buildAnterior'
                    resultado['recorridoAnterior'] = comprobar_base(navegador, base)
                if anterior:
                    resultado['etapa'] = 'politicaAnterior'
                    resultado['politicaAnterior'] = comprobar_politica_anterior(navegador, anterior)
                contexto, externas, errores = contexto_local(navegador, direccion, viewport={'width': 1440, 'height': 1000},
                                                             reduced_motion='no-preference')
                try:
                    pagina, respuesta = abrir_pagina(contexto, direccion)
                    resultado['scriptsServidos'] = pagina.locator('script[src]').evaluate_all('xs => xs.map(x=>x.getAttribute("src"))')
                    resultado['etapa'] = 'desktop'
                    resultado['imagenes'] = comprobar_imagenes_escritorio(pagina, direccion)
                    comprobar_enlaces(pagina)
                    resultado['etapa'] = 'progresos'
                    resultado['progresos'] = comprobar_progresos(pagina, direccion)
                    resultado['etapa'] = 'mutaciones'
                    resultado['mutaciones'] = comprobar_mutaciones(pagina, direccion)
                    resultado['etapa'] = 'entradaNativa'
                    resultado['entradaNativa'] = comprobar_entrada_nativa(pagina)
                    resultado['etapa'] = 'ampliacion'
                    resultado['ampliacion'] = [comprobar_ampliacion(pagina, direccion, compartido=True),
                                               comprobar_ampliacion(pagina, direccion, compartido=True, ancho=1440, alto=1000)]
                    resultado['etapa'] = 'pantallas'
                    resultado['pantallas'] = []
                    for ancho, alto in PANTALLAS:
                        pagina.set_viewport_size({'width': ancho, 'height': alto})
                        dejar_pintar(pagina)
                        comprobar_imagenes_escritorio(pagina, direccion)
                        cinematica = alto >= 700
                        if cinematica:
                            comprobar_progresos(pagina, direccion)
                        else:
                            comprobar_fallback(pagina)
                        comprobar_navegacion(pagina, cinematica)
                        exigir(pagina.evaluate('document.documentElement.scrollWidth') <= ancho,
                               f'Desbordamiento a {ancho} × {alto}')
                        resultado['pantallas'].append({'ancho': ancho, 'alto': alto, 'cinematica': cinematica,
                                                       'sinDesborde': True, 'titulosVisibles': True})
                    resultado['etapa'] = 'focoResize'
                    resultado['focoResize'] = comprobar_foco_resize(pagina, direccion)
                    resultado['etapa'] = 'reduccionDinamica'
                    resultado['reduccionDinamica'] = comprobar_reduccion_dinamica(pagina, direccion)
                    resultado['etapa'] = 'csp'
                    resultado['csp'] = comprobar_csp(pagina, contexto, direccion, respuesta)
                    exigir(not externas and not errores, 'La landing consulta red externa o produce errores JavaScript')
                    resultado['externas'] = externas
                    resultado['erroresJavascript'] = errores
                finally:
                    contexto.close()
                resultado['etapa'] = 'sinJavascript'
                resultado['sinJavascript'] = comprobar_estatica(navegador, direccion)
                resultado['etapa'] = 'sinCapacidades'
                resultado['sinCapacidades'] = comprobar_sin_capacidades(navegador, direccion)
                resultado['estado'], resultado['etapa'] = 'correcto', 'completado'
            finally:
                navegador.close()
    except Exception as fallo:
        resultado.update(estado='fallido', error={'tipo': type(fallo).__name__, 'mensaje': str(fallo)[:700]})
    finally:
        archivo = destino / 'interactividad.json'
        archivo.write_text(json.dumps(resultado, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'estado': resultado['estado'], 'etapa': resultado['etapa'], 'evidencia': str(archivo),
                      **({'error': resultado['error']['mensaje'][:250]} if 'error' in resultado else {})}, ensure_ascii=False))
    return resultado['estado'] == 'correcto'


if __name__ == '__main__':
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument('--url', default='http://127.0.0.1:4324/')
    argumentos.add_argument('--politica-anterior')
    argumentos.add_argument('--recorrido-anterior', help='Build anterior: debe fallar los mismos oráculos desktop y progresos')
    argumentos.add_argument('--evidencia', type=Path, default=Path('/tmp/ondexia-landing-cinematica'))
    parametros = argumentos.parse_args()
    try:
        for direccion in [parametros.url, parametros.politica_anterior, parametros.recorrido_anterior]:
            if direccion:
                direccion_local(direccion)
    except AssertionError as fallo:
        argumentos.error(str(fallo))
    sys.exit(0 if comprobar(parametros.url, parametros.politica_anterior, parametros.recorrido_anterior, parametros.evidencia) else 1)
