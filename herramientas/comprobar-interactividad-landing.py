"""Aceptación local de pestañas, ampliación, mejora progresiva y CSP de la landing.

Requiere Playwright Python, Chromium y builds servidos con las cabeceras Terraform.
No verifica configuración efectiva en AWS ni servicios fiscales o comerciales.
"""
import argparse
import json
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

ESCENAS = ['Preparar la venta', 'Revisar el registro', 'Previsualizar el documento']

def comprobar_reapertura(pagina):
    pagina.set_viewport_size({'width': 390, 'height': 844})
    for indice in range(3):
        pagina.get_by_role('tab', name=ESCENAS[indice], exact=True).click()
        pagina.get_by_role('button', name='Ampliar captura', exact=True).click()
        dialogo = pagina.get_by_role('dialog', name=ESCENAS[indice], exact=True)
        expect(dialogo).to_be_visible()
        pagina.wait_for_function('document.querySelector("[data-captura-ampliada]").complete')
        assert dialogo.evaluate('e => e.scrollTop') == 0, 'Cada ampliación debe abrir desde el principio, sin heredar el desplazamiento'
        dialogo.evaluate('e => { e.scrollTop = e.scrollHeight; }')
        assert dialogo.evaluate('e => e.scrollTop') > 0, 'La prueba debe recorrer una captura más alta que el diálogo'
        pagina.get_by_role('button', name='Cerrar', exact=True).click()
    print('REAPERTURA: PASA — las tres escenas comienzan desde arriba')

def comprobar_politica_anterior(navegador, direccion):
    pagina = navegador.new_page()
    pagina.goto(direccion, wait_until='networkidle')
    try:
        assert pagina.get_by_role('tab').count() == 3, 'La CSP anterior bloquea la inicialización de pestañas'
        raise RuntimeError('La política anterior no reprodujo el bloqueo esperado')
    except AssertionError as fallo:
        print('SIN ARREGLO CSP: FALLA —', fallo)
    assert pagina.locator('[data-escena]:visible').count() == 3
    pagina.close()

def comprobar(direccion, anterior, destino):
    destino.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as p:
        navegador = p.chromium.launch(executable_path='/usr/bin/chromium', args=['--no-sandbox'])
        if anterior:
            comprobar_politica_anterior(navegador, anterior)
        contexto = navegador.new_context(viewport={'width': 1440, 'height': 1000}, reduced_motion='reduce')
        pagina = contexto.new_page()
        externas, errores = [], []
        origen = urlparse(direccion).netloc
        pagina.on('request', lambda r: externas.append(r.url) if urlparse(r.url).netloc not in {'', origen} else None)
        pagina.on('pageerror', lambda e: errores.append(str(e)))
        pagina.goto(direccion, wait_until='networkidle')
        expect(pagina.get_by_role('tab')).to_have_count(3)
        assert pagina.locator('h1').count() == 1
        ejecutables = pagina.locator('script:not([type="application/ld+json"])')
        assert ejecutables.count() > 0
        assert all(s.get_attribute('src') and s.get_attribute('src').startswith('/_astro/') for s in ejecutables.all()), 'Todos los scripts ejecutables deben ser archivos locales'
        for enlace in pagina.locator('a[href^="#"]').all():
            assert pagina.locator(enlace.get_attribute('href')).count() == 1
        primera = pagina.get_by_role('tab', name=ESCENAS[0], exact=True)
        expect(primera).to_have_attribute('aria-selected', 'true')
        primera.focus()
        for tecla, indice in [('ArrowRight', 1), ('End', 2), ('Home', 0), ('ArrowLeft', 2)]:
            pagina.keyboard.press(tecla)
            expect(pagina.get_by_role('tab', name=ESCENAS[indice], exact=True)).to_have_attribute('aria-selected', 'true')
            expect(pagina.get_by_role('tab', name=ESCENAS[indice], exact=True)).to_be_focused()
            expect(pagina.get_by_role('tabpanel')).to_have_count(1)
            expect(pagina.get_by_role('tabpanel')).to_have_accessible_name(ESCENAS[indice])
        segunda = pagina.get_by_role('tab', name=ESCENAS[1], exact=True)
        segunda.click()
        abrir = pagina.get_by_role('button', name='Ampliar captura', exact=True)
        abrir.click()
        dialogo = pagina.get_by_role('dialog', name=ESCENAS[1], exact=True)
        expect(dialogo).to_be_visible()
        pagina.keyboard.press('Escape')
        expect(dialogo).not_to_be_visible()
        expect(abrir).to_be_focused()
        abrir.click()
        pagina.get_by_role('button', name='Cerrar', exact=True).click()
        expect(abrir).to_be_focused()
        comprobar_reapertura(pagina)
        resultados = []
        for ancho in [320, 390, 768, 1024, 1440]:
            pagina.set_viewport_size({'width': ancho, 'height': 900})
            pagina.get_by_role('tab', name=ESCENAS[0], exact=True).click()
            pagina.wait_for_function('document.querySelector("[data-escena]:not([hidden]) img").complete')
            assert pagina.evaluate('document.documentElement.scrollWidth') <= ancho
            imagen = pagina.get_by_role('tabpanel').locator('img')
            expect(imagen).to_be_visible()
            variante = 'movil' if ancho <= 600 else 'escritorio'
            expect(imagen).to_have_js_property('currentSrc', direccion.rstrip('/') + f'/capturas/venta-{variante}.png')
            pagina.get_by_role('button', name='Ampliar captura', exact=True).click()
            expect(pagina.get_by_role('dialog')).to_be_visible()
            cierre = pagina.get_by_role('button', name='Cerrar', exact=True)
            assert cierre.bounding_box()['x'] >= 0 and cierre.bounding_box()['x'] + cierre.bounding_box()['width'] <= ancho
            pagina.keyboard.press('Escape')
            if ancho < 1024:
                menu = pagina.locator('.menu-movil > summary')
                menu.focus()
                pagina.keyboard.press('Enter')
                expect(pagina.locator('.enlaces-movil')).to_be_visible()
                pagina.locator('.enlaces-movil a[href="#explorar"]').click()
                assert pagina.locator('.menu-movil').get_attribute('open') is None
            pagina.locator('h1').scroll_into_view_if_needed()
            pagina.locator('body').click(position={'x': 5, 'y': 100})
            pagina.screenshot(path=str(destino / f'landing-{ancho}.png'), full_page=True)
            resultados.append({'ancho': ancho, 'desbordamiento': False, 'variante': variante, 'dialogo': True})

        # La política debe impedir scripts inline y conexiones, aunque un archivo local esté permitido.
        pagina.evaluate('''() => {
          window.violacionesPrueba = [];
          document.addEventListener('securitypolicyviolation', e => window.violacionesPrueba.push(e.violatedDirective));
          const script = document.createElement('script');
          script.textContent = 'window.inlineNoPermitido = true';
          document.body.append(script);
          fetch('/sonda-no-permitida').catch(() => {});
        }''')
        pagina.wait_for_function('window.violacionesPrueba.includes("script-src-elem") && window.violacionesPrueba.includes("connect-src")')
        assert pagina.evaluate('window.inlineNoPermitido === undefined')
        assert not externas and not errores, {'externas': externas, 'errores': errores}
        print('CON ARREGLO CSP: PASA — scripts locales operativos; inline y conexiones rechazados')

        # Mutación aislada del estado seleccionado: el oráculo de accesibilidad debe detectar la incoherencia.
        primera.evaluate('e => e.setAttribute("aria-selected", "false")')
        try:
            assert primera.get_attribute('aria-selected') == 'true', 'La primera escena perdió su estado seleccionado'
            raise RuntimeError('La mutación no fue detectada')
        except AssertionError as fallo:
            print('MUTACIÓN AISLADA: DETECTADA —', fallo)
        pagina.reload(wait_until='networkidle')
        expect(pagina.get_by_role('tab', name=ESCENAS[0], exact=True)).to_have_attribute('aria-selected', 'true')
        print('RESTAURADO: PASA')
        contexto.close()

        sin_script = navegador.new_context(java_script_enabled=False, viewport={'width': 390, 'height': 844})
        pagina_estatica = sin_script.new_page()
        pagina_estatica.goto(direccion, wait_until='networkidle')
        assert pagina_estatica.locator('[data-escena]:visible').count() == 3
        assert pagina_estatica.locator('[data-pestanas]').is_hidden()
        assert pagina_estatica.locator('a[href="/capturas/venta-movil.png"]:visible').count() == 1
        assert pagina_estatica.locator('[data-ampliar]:visible').count() == 0
        sin_script.close()
        navegador.close()
        (destino / 'interactividad.json').write_text(json.dumps({'pantallas': resultados, 'reaperturaDesdeArriba': True, 'sinJavascript': True, 'csp': True, 'externas': externas, 'errores': errores}, ensure_ascii=False, indent=2))
        print(json.dumps(resultados, ensure_ascii=False))

if __name__ == '__main__':
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument('--url', default='http://127.0.0.1:4324/')
    argumentos.add_argument('--politica-anterior')
    argumentos.add_argument('--evidencia', type=Path, default=Path('/tmp/ondexia-landing-interactiva'))
    parametros = argumentos.parse_args()
    for direccion in [parametros.url, parametros.politica_anterior]:
        if direccion and urlparse(direccion).hostname not in {'127.0.0.1', 'localhost', '::1'}:
            argumentos.error('La comprobación solo admite servidores locales')
    comprobar(parametros.url, parametros.politica_anterior, parametros.evidencia)
