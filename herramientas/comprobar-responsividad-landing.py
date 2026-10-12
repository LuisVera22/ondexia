"""Revisa la landing compilada y compara la legibilidad con una versión anterior.

Requiere Playwright para Python, Chromium y dos builds servidos localmente.
No verifica AWS, SUNAT, recepción de correos ni una conformidad WCAG completa.
"""
import json
import re
from pathlib import Path
from playwright.sync_api import sync_playwright

import os

DIRECTORIO = Path(os.environ.get('EVIDENCIA_LANDING', '/tmp/ondexia-landing'))
DIRECTORIO.mkdir(parents=True, exist_ok=True)
URL_ACTUAL = os.environ.get('URL_LANDING', 'http://127.0.0.1:4321/')
URL_ANTERIOR = os.environ.get('URL_LANDING_ANTERIOR', 'http://127.0.0.1:4322/')

def luminancia(color):
    componentes = [float(c) / 255 for c in re.findall(r'[\d.]+', color)[:3]]
    lineales = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in componentes]
    return sum(c * peso for c, peso in zip(lineales, [0.2126, 0.7152, 0.0722]))

def contraste(pagina):
    dato = pagina.locator('#acceso > div > div > p:not(.rotulo)').first.evaluate('''e => {
      let padre = e;
      while (padre && getComputedStyle(padre).backgroundColor === 'rgba(0, 0, 0, 0)') padre = padre.parentElement;
      return {texto: getComputedStyle(e).color, fondo: getComputedStyle(padre).backgroundColor};
    }''')
    luces = sorted([luminancia(dato['texto']), luminancia(dato['fondo'])])
    razon = (luces[1] + 0.05) / (luces[0] + 0.05)
    # Umbral WCAG 2.2, 1.4.3: el párrafo tiene tamaño normal, no texto grande.
    assert razon >= 4.5, f'Contraste {razon:.2f}:1 menor que 4.5:1'
    return razon

with sync_playwright() as p:
    navegador = p.chromium.launch(executable_path='/usr/bin/chromium', args=['--no-sandbox'])
    pagina = navegador.new_page(viewport={'width': 1440, 'height': 1000}, reduced_motion='reduce')
    pagina.goto(URL_ANTERIOR, wait_until='networkidle')
    try:
        contraste(pagina)
        raise RuntimeError('La versión anterior no reproduce el fallo esperado')
    except AssertionError as fallo:
        print('SIN ARREGLO: FALLA —', str(fallo))
    pagina.goto(URL_ACTUAL, wait_until='networkidle')
    print(f'CON ARREGLO: PASA — contraste {contraste(pagina):.2f}:1')
    assert pagina.locator('h1').count() == 1
    assert pagina.locator('script:not([type="application/ld+json"])').count() == 0
    assert pagina.locator('a[href="https://app.ondexia.com"]').count() >= 1
    assert pagina.get_by_text('Vista ilustrativa · datos de ejemplo', exact=True).count() == 1
    for enlace in pagina.locator('a[href^="#"]').all():
        destino = enlace.get_attribute('href')
        assert pagina.locator(destino).count() == 1, destino
    resultados = []
    for ancho in [320, 390, 768, 1024, 1440]:
        pagina.set_viewport_size({'width': ancho, 'height': 900})
        pagina.evaluate('document.fonts.ready')
        documento = pagina.evaluate('document.documentElement.scrollWidth')
        assert documento <= ancho, f'Desbordamiento horizontal: {ancho} → {documento}'
        if ancho < 1024:
            menu = pagina.locator('.menu-movil > summary')
            menu.focus()
            pagina.keyboard.press('Enter')
            assert pagina.locator('.menu-movil').get_attribute('open') is not None
            assert pagina.locator('.enlaces-movil').is_visible()
            pagina.keyboard.press('Enter')
            assert pagina.locator('.menu-movil').get_attribute('open') is None
        pregunta = pagina.locator('#preguntas summary').first
        pregunta.focus()
        pagina.keyboard.press('Enter')
        assert pagina.locator('#preguntas details').first.get_attribute('open') is not None
        pagina.keyboard.press('Enter')
        assert pagina.locator('#preguntas details').first.get_attribute('open') is None
        assert contraste(pagina) >= 4.5
        pagina.screenshot(path=str(DIRECTORIO / f'despues-{ancho}.png'), full_page=True)
        resultados.append({'ancho': ancho, 'desbordamiento': False, 'menu_teclado': ancho < 1024, 'preguntas_teclado': True})
    # Mutación aislada en el DOM: no modifica archivos ni configuración publicada.
    pagina.locator('#acceso').evaluate("e => e.style.backgroundColor = '#4f46e5'")
    pagina.locator('#acceso > div > div > p:not(.rotulo)').first.evaluate("e => e.style.color = '#26235e'")
    pagina.wait_for_function('''() => {
      const parrafo = document.querySelector('#acceso > div > div > p:not(.rotulo)');
      return getComputedStyle(parrafo).color === 'rgb(38, 35, 94)'
        && getComputedStyle(document.querySelector('#acceso')).backgroundColor === 'rgb(79, 70, 229)';
    }''')
    try:
        contraste(pagina)
        raise RuntimeError('La mutación no fue detectada')
    except AssertionError as fallo:
        print('MUTACIÓN AISLADA: DETECTADA —', str(fallo))
    pagina.reload(wait_until='networkidle')
    assert contraste(pagina) >= 4.5
    print('RESTAURADO: PASA')
    (DIRECTORIO / 'verificacion.json').write_text(json.dumps(resultados, ensure_ascii=False, indent=2))
    print(json.dumps(resultados, ensure_ascii=False))
    navegador.close()
