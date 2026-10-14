"""Aceptación local del recorrido, parallax, ampliación, alternativa estática y CSP.

Requiere Playwright Python, Chromium y build servido con la cabecera Terraform.
No verifica configuración efectiva AWS ni servicios fiscales o comerciales.
"""
import argparse
import json
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

ESCENAS = ['Preparar la venta', 'Revisar el registro', 'Previsualizar el documento']
NOMBRES_PASOS = [f'{indice + 1:02d} {titulo}' for indice, titulo in enumerate(ESCENAS)]


def dejar_pintar(pagina):
    pagina.evaluate('() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))')


def desplazarse(pagina, posicion):
    pagina.evaluate('y => window.scrollTo({top:y, behavior:"instant"})', posicion)
    dejar_pintar(pagina)


def comprobar_recorrido(pagina):
    expect(pagina.locator('[data-escena]:visible')).to_have_count(3)
    navegacion = pagina.get_by_role('navigation', name='Recorrido del sistema', exact=True)
    expect(navegacion.get_by_role('link')).to_have_count(3)
    expect(pagina.get_by_role('tab')).to_have_count(0)
    for indice, titulo in enumerate(ESCENAS):
        enlace = navegacion.get_by_role('link', name=NOMBRES_PASOS[indice], exact=True)
        enlace.click()
        escena = pagina.locator('[data-escena]').nth(indice)
        expect(escena).to_be_focused()
        expect(enlace).to_have_attribute('aria-current', 'step')
        pagina.wait_for_timeout(500)
        encabezado = escena.locator('h3').bounding_box()
        cabecera = pagina.locator('header').bounding_box()
        borde = max(cabecera['y'] + cabecera['height'], navegacion.bounding_box()['y'] + navegacion.bounding_box()['height'])
        assert encabezado['y'] >= borde - 1, 'La navegación no debe tapar el título de escena'
        assert encabezado['y'] + encabezado['height'] <= pagina.viewport_size['height']
        expect(pagina.locator('[data-progreso]')).to_have_text(f'Escena {indice + 1} de 3')
    expect(pagina.get_by_role('button', name='Escena siguiente', exact=True)).to_be_disabled()
    pagina.get_by_role('button', name='Escena anterior', exact=True).click()
    expect(pagina.locator('[data-escena]').nth(1)).to_be_focused()
    expect(navegacion.get_by_role('link', name=NOMBRES_PASOS[1], exact=True)).to_have_attribute('aria-current', 'step')
    navegacion.get_by_role('link', name=NOMBRES_PASOS[0], exact=True).click()
    expect(pagina.get_by_role('button', name='Escena anterior', exact=True)).to_be_disabled()
    pagina.get_by_role('button', name='Escena siguiente', exact=True).click()
    expect(pagina.locator('[data-escena]').nth(1)).to_be_focused()
    print('RECORRIDO: PASA — escenas visibles, indicadores y avance manual coherentes')


def medir_movimiento(pagina):
    escena = pagina.locator('[data-escena]').first
    plano = escena.locator('[data-plano]')
    posicion = escena.evaluate('e => e.getBoundingClientRect().top + scrollY')
    valores = []
    for avance in [0, 180]:
        desplazarse(pagina, posicion - 160 + avance)
        valores.append(plano.evaluate('e => new DOMMatrixReadOnly(getComputedStyle(e).transform).m42'))
    assert all(abs(valor) <= 12.01 for valor in valores), 'El parallax debe estar acotado a ±12 px'
    return valores


def comprobar_movimiento(pagina):
    pagina.set_viewport_size({'width':1440, 'height':1000})
    pagina.emulate_media(reduced_motion='no-preference')
    dejar_pintar(pagina)
    valores = medir_movimiento(pagina)
    assert abs(valores[1] - valores[0]) > 0.5, 'La captura debe responder al desplazamiento, no solo animar su entrada'
    plano = pagina.locator('[data-plano]').first
    plano.evaluate('e => e.style.setProperty("transform", "none", "important")')
    try:
        mutados = medir_movimiento(pagina)
        assert abs(mutados[1] - mutados[0]) > 0.5, 'Parallax desactivado'
        raise RuntimeError('La mutación del parallax no fue detectada')
    except AssertionError:
        print('MUTACIÓN PARALLAX: DETECTADA — transform desactivado en DOM')
    finally:
        plano.evaluate('e => e.style.removeProperty("transform")')
    restaurados = medir_movimiento(pagina)
    assert abs(restaurados[1] - restaurados[0]) > 0.5
    pagina.emulate_media(reduced_motion='reduce')
    dejar_pintar(pagina)
    assert medir_movimiento(pagina) == [0, 0]
    assert not pagina.locator('.figura-fijada').count()
    assert pagina.locator('[data-escena]').first.evaluate('e => e.getAnimations({subtree:true}).length') == 0
    print('MOVIMIENTO: PASA — parallax acotado; reduce dinámico sin movimiento ni fijación')
    return valores


def comprobar_ampliacion(pagina):
    pagina.set_viewport_size({'width':390, 'height':844})
    for indice, titulo in enumerate(ESCENAS):
        escena = pagina.locator('[data-escena]').nth(indice)
        abrir = escena.get_by_role('button', name='Ampliar captura', exact=True)
        abrir.click()
        dialogo = pagina.get_by_role('dialog', name=titulo, exact=True)
        expect(dialogo).to_be_visible()
        pagina.wait_for_function('() => document.querySelector("[data-captura-ampliada]").complete')
        assert dialogo.evaluate('e => e.scrollTop') == 0
        dialogo.evaluate('e => {e.scrollTop = e.scrollHeight}')
        assert dialogo.evaluate('e => e.scrollTop') > 0
        pagina.keyboard.press('Escape')
        expect(dialogo).not_to_be_visible()
        expect(abrir).to_be_focused()
    print('AMPLIACIÓN: PASA — escenas correctas, reapertura arriba, Escape y retorno del foco')


def comprobar(direccion, anterior, base, destino):
    destino.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as p:
        navegador = p.chromium.launch(executable_path='/usr/bin/chromium', args=['--no-sandbox'])
        if base:
            pagina_base = navegador.new_page()
            pagina_base.goto(base, wait_until='networkidle')
            try:
                assert pagina_base.locator('[data-escena]:visible').count() == 3, 'La versión anterior muestra solo una escena del selector'
                raise RuntimeError('El build base no reprodujo la diferencia esperada')
            except AssertionError as fallo:
                print('SIN RECORRIDO: FALLA —', fallo)
            pagina_base.close()
        if anterior:
            pagina_anterior = navegador.new_page()
            pagina_anterior.goto(anterior, wait_until='networkidle')
            assert pagina_anterior.get_by_role('button', name='Escena siguiente', exact=True).count() == 0
            assert pagina_anterior.locator('[data-escena]:visible').count() == 3
            print('CSP ANTERIOR: interacción bloqueada; recorrido nativo conservado')
            pagina_anterior.close()
        contexto = navegador.new_context(viewport={'width':1440,'height':1000}, reduced_motion='reduce')
        pagina = contexto.new_page()
        externas, errores = [], []
        origen = urlparse(direccion).netloc
        pagina.on('request', lambda r: externas.append(r.url) if urlparse(r.url).netloc not in {'', origen} else None)
        pagina.on('pageerror', lambda e: errores.append(str(e)))
        pagina.goto(direccion, wait_until='networkidle')
        assert pagina.locator('h1').count() == 1
        scripts = pagina.locator('script:not([type="application/ld+json"])').all()
        assert scripts and all(s.get_attribute('src') and s.get_attribute('src').startswith('/_astro/') for s in scripts)
        for enlace in pagina.locator('a[href^="#"]').all():
            assert pagina.locator(enlace.get_attribute('href')).count() == 1
        comprobar_recorrido(pagina)
        movimiento = comprobar_movimiento(pagina)
        comprobar_ampliacion(pagina)
        pantallas = []
        for ancho, alto in [(320,900),(390,844),(768,900),(1024,900),(1440,1000),(1440,400),(320,320),(360,250),(390,240),(320,225)]:
            pagina.set_viewport_size({'width':ancho,'height':alto})
            comprobar_recorrido(pagina)
            assert pagina.evaluate('document.documentElement.scrollWidth') <= ancho
            variante = 'movil' if ancho <= 600 else 'escritorio'
            imagen = pagina.locator('[data-escena]').first.locator('[data-captura]')
            expect(imagen).to_have_js_property('currentSrc', direccion.rstrip('/') + f'/capturas/venta-{variante}.png')
            if ancho < 1024:
                menu = pagina.locator('.menu-movil > summary')
                menu.focus()
                pagina.keyboard.press('Enter')
                expect(pagina.locator('.enlaces-movil')).to_be_visible()
                pagina.locator('.enlaces-movil a[href="#explorar"]').click()
                assert pagina.locator('.menu-movil').get_attribute('open') is None
            pantallas.append({'ancho':ancho,'alto':alto,'desbordamiento':False,'variante':variante,'navegacion':True})
        pagina.evaluate('''() => {
          window.violacionesPrueba = [];
          document.addEventListener('securitypolicyviolation', e => window.violacionesPrueba.push(e.violatedDirective));
          const script = document.createElement('script');
          script.textContent = 'window.inlineNoPermitido = true';
          document.body.append(script);
          fetch('/sonda-no-permitida').catch(() => {});
        }''')
        pagina.wait_for_function('() => window.violacionesPrueba.includes("script-src-elem") && window.violacionesPrueba.includes("connect-src")')
        assert pagina.evaluate('window.inlineNoPermitido === undefined')
        assert not externas and not errores, {'externas':externas,'errores':errores}
        print('CSP: PASA — script local operativo; inline y conexiones rechazados')
        contexto.close()
        sin_script = navegador.new_context(java_script_enabled=False, viewport={'width':390,'height':844})
        estatica = sin_script.new_page()
        estatica.goto(direccion, wait_until='networkidle')
        assert estatica.locator('[data-escena]:visible').count() == 3
        enlace = estatica.get_by_role('navigation', name='Recorrido del sistema').get_by_role('link', name=NOMBRES_PASOS[1], exact=True)
        enlace.click()
        assert estatica.url.endswith('#escena-registro')
        assert estatica.locator('[data-ampliar]:visible').count() == 0
        assert estatica.locator('[data-siguiente]:visible').count() == 0
        assert estatica.locator('a[href="/capturas/venta-movil.png"]:visible').count() == 1
        for ancho, alto in [(320,225),(360,250),(390,240)]:
            estatica.set_viewport_size({'width':ancho,'height':alto})
            for indice in range(3):
                estatica.get_by_role('navigation', name='Recorrido del sistema').get_by_role('link', name=NOMBRES_PASOS[indice], exact=True).click()
                estatica.wait_for_timeout(500)
                encabezado = estatica.locator('[data-escena]').nth(indice).locator('h3').bounding_box()
                cabecera = estatica.locator('header').bounding_box()
                assert encabezado['y'] >= cabecera['y'] + cabecera['height'] - 1
                assert encabezado['y'] + encabezado['height'] <= alto, 'El título también debe caber sin JavaScript'
        print('NOMBRES Y FALLBACK: PASA — números en nombre accesible y títulos visibles sin JavaScript')
        sin_script.close()
        navegador.close()
    (destino/'interactividad.json').write_text(json.dumps({'pantallas':pantallas,'parallaxObservado':movimiento,'sinJavascript':True,'csp':True,'externas':externas,'errores':errores}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument('--url', default='http://127.0.0.1:4324/')
    argumentos.add_argument('--politica-anterior')
    argumentos.add_argument('--recorrido-anterior')
    argumentos.add_argument('--evidencia', type=Path, default=Path('/tmp/ondexia-landing-recorrido'))
    parametros = argumentos.parse_args()
    for direccion in [parametros.url,parametros.politica_anterior,parametros.recorrido_anterior]:
        if direccion and urlparse(direccion).hostname not in {'127.0.0.1','localhost','::1'}:
            argumentos.error('La comprobación solo admite servidores locales')
    comprobar(parametros.url,parametros.politica_anterior,parametros.recorrido_anterior,parametros.evidencia)
