/**
 * `focusVisible` todavia no esta en la definicion de `FocusOptions` de
 * TypeScript, aunque los navegadores la implementan. Se declara aparte en vez
 * de castear a `any` en cada llamada.
 */
interface OpcionesDeFoco extends FocusOptions {
  focusVisible?: boolean;
}

/**
 * Enfoca «como si viniera del teclado».
 *
 * <p>Sin esa pista, enfocar un boton por programa no activa `:focus-visible` y
 * la prueba mediria el estado sin foco sin enterarse de nada.
 */
function enfocarConTeclado(elemento: HTMLElement): void {
  elemento.focus({ focusVisible: true } as OpcionesDeFoco);
}

/**
 * El indicador de foco, que es una regla global y por tanto afecta a todo.
 *
 * <p>Se prueba porque es de las cosas que se rompen sin que nadie lo note: un
 * `focus:outline-hidden` de más en un componente, o un cambio en el orden de
 * las capas de CSS, y quien navega con el tabulador deja de saber dónde está.
 * No hay error, no hay aviso, y el 99 % de quienes usan la aplicación con
 * ratón no ven ninguna diferencia.
 *
 * <p>Las dos mitades importan igual: que el contorno aparezca donde debe, y
 * que NO aparezca donde un componente ya resuelve su propio foco — si la regla
 * base ganara a las utilidades, los campos de formulario tendrían anillo y
 * contorno a la vez.
 */
describe('El foco visible', () => {
  let elemento: HTMLElement;

  afterEach(() => elemento?.remove());

  function ponerEnPagina(html: string): HTMLElement {
    const contenedor = document.createElement('div');
    contenedor.innerHTML = html;
    elemento = contenedor.firstElementChild as HTMLElement;
    document.body.appendChild(elemento);
    return elemento;
  }

  it('un botón corriente recibe el contorno de la marca', () => {
    const boton = ponerEnPagina('<button type="button">Guardar</button>');

    enfocarConTeclado(boton);

    expect(boton.matches(':focus-visible'))
      .withContext('el navegador debe considerarlo foco de teclado')
      .toBeTrue();

    const estilo = getComputedStyle(boton);
    expect(estilo.outlineStyle).toBe('solid');

    // No se compara con «2px» exactos: Karma abre el navegador con zoom y los
    // dos pixeles se calculan en 1,6. Lo que hay que asegurar es que el
    // contorno existe y tiene grosor, no el redondeo del navegador de turno.
    expect(Number.parseFloat(estilo.outlineWidth))
      .withContext(`grosor calculado: ${estilo.outlineWidth}`)
      .toBeGreaterThan(1);
    // brand-500
    expect(estilo.outlineColor).toBe('rgb(79, 70, 229)');
  });

  it('quien apaga el contorno se sale con la suya: las utilidades ganan a la base', () => {
    const boton = ponerEnPagina(
      '<button type="button" class="focus:outline-hidden">Buscar</button>'
    );

    enfocarConTeclado(boton);

    expect(boton.matches(':focus-visible')).toBeTrue();
    expect(getComputedStyle(boton).outlineStyle)
      .withContext('un componente con su propio anillo no debe llevar además contorno')
      .not.toBe('solid');
  });

  it('con el ratón no se pinta nada', () => {
    const boton = ponerEnPagina('<button type="button">Guardar</button>');

    // Foco sin la pista de teclado: es lo que ocurre al pulsar con el raton.
    boton.focus();

    if (boton.matches(':focus-visible')) {
      // Algunos navegadores marcan igualmente el foco por programa. No se
      // fuerza el caso: lo que no puede pasar es lo contrario —que con
      // teclado NO se pinte—, y eso lo cubre la primera prueba.
      return;
    }
    expect(getComputedStyle(boton).outlineStyle).not.toBe('solid');
  });

  it('en tema oscuro el contorno cambia de tono', () => {
    document.documentElement.classList.add('dark');
    const boton = ponerEnPagina('<button type="button">Guardar</button>');
    enfocarConTeclado(boton);

    // brand-400: el 500 da 2,69:1 sobre el fondo oscuro y WCAG pide 3:1 para
    // un indicador que no es texto.
    const color = getComputedStyle(boton).outlineColor;
    document.documentElement.classList.remove('dark');

    expect(color).toBe('rgb(129, 140, 248)');
  });
});
