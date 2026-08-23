import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { EncabezadoPaginaComponent } from './encabezado-pagina.component';

/**
 * El orden de las dos filas del encabezado.
 *
 * <p>Es una prueba de maquetación, que no suele valer la pena, pero esta sí:
 * el orden entre la ruta de navegación y el título es una decisión de diseño
 * que se toma una vez y se aplica a las setenta vistas a la vez, porque todas
 * usan este componente. Si alguien reordena la plantilla sin querer —moviendo
 * el globo de la descripción, por ejemplo— no se rompe nada y no falla nada:
 * simplemente todas las pantallas del sistema cambian de aspecto y no se nota
 * hasta que alguien mira.
 *
 * <p>Se comprueba la posición en el documento, no las clases: lo que importa
 * es qué se lee primero, y eso lo decide el orden del DOM tanto para la vista
 * como para un lector de pantalla.
 */
describe('EncabezadoPaginaComponent · la ruta va antes que el título', () => {
  async function montar(titulo: string, descripcion = '') {
    await TestBed.configureTestingModule({
      imports: [EncabezadoPaginaComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    const fixture = TestBed.createComponent(EncabezadoPaginaComponent);
    fixture.componentRef.setInput('titulo', titulo);
    if (descripcion) {
      fixture.componentRef.setInput('descripcion', descripcion);
    }
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('pinta la ruta de navegación por delante del título', async () => {
    const raiz = await montar('Empresas');

    const ruta = raiz.querySelector('nav')!;
    const titulo = raiz.querySelector('h1')!;

    expect(ruta).withContext('falta la ruta de navegación').toBeTruthy();
    expect(titulo).withContext('falta el título').toBeTruthy();

    // DOCUMENT_POSITION_FOLLOWING: el título viene DESPUÉS de la ruta.
    const relacion = ruta.compareDocumentPosition(titulo);
    expect(relacion & Node.DOCUMENT_POSITION_FOLLOWING)
      .withContext('el título debe ir debajo de la ruta, no antes ni al lado')
      .toBeTruthy();
  });

  it('la ruta se arrima a la derecha y el título al margen izquierdo', async () => {
    const raiz = await montar('Empresas');
    document.body.appendChild(raiz);

    const marco = raiz.querySelector('div')!.getBoundingClientRect();
    const titulo = raiz.querySelector('h1')!.getBoundingClientRect();

    // Se miden las migas, no la <ol>: la lista es de bloque y ocupa el ancho
    // entero aunque su contenido este arrimado, asi que sus bordes coinciden
    // con los del marco tanto si `justify-end` esta puesto como si no — y la
    // prueba pasaria igual sin el cambio, que es justo lo que no puede pasar.
    const migas = [...raiz.querySelectorAll('nav ol > li')].map((li) =>
      li.getBoundingClientRect()
    );
    const primera = migas[0];
    const ultima = migas[migas.length - 1];

    // Apiladas, no en la misma linea.
    expect(titulo.top).toBeGreaterThanOrEqual(ultima.bottom);

    expect(Math.abs(ultima.right - marco.right))
      .withContext('la ruta termina pegada al borde derecho')
      .toBeLessThan(1);

    expect(primera.left)
      .withContext('la ruta no arranca en el margen izquierdo: está arrimada')
      .toBeGreaterThan(marco.left + 1);

    expect(Math.abs(titulo.left - marco.left))
      .withContext('el título arranca en el margen izquierdo')
      .toBeLessThan(1);

    raiz.remove();
  });

  it('la ruta termina en la página actual, marcada para el lector de pantalla', async () => {
    const raiz = await montar('Empresas');

    const actual = raiz.querySelector('[aria-current="page"]')!;
    expect(actual.textContent?.trim()).toBe('Empresas');
  });

  it('sin descripción no se pinta el botón que la abre', async () => {
    const raiz = await montar('Empresas');
    expect(raiz.querySelector('button')).toBeNull();
  });

  it('con descripción, el botón acompaña al título en su misma fila', async () => {
    const raiz = await montar('Empresas', 'Las empresas que administras.');

    const titulo = raiz.querySelector('h1')!;
    const boton = raiz.querySelector('button')!;

    expect(boton).withContext('falta el botón de la descripción').toBeTruthy();
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    expect(titulo.parentElement)
      .withContext('el botón va junto al título, no en la fila de la ruta')
      .toBe(boton.parentElement);
  });
});
