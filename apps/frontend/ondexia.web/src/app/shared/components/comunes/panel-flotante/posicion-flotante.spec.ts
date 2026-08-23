import { posicionFlotante } from './posicion-flotante';

/**
 * Que un panel flotante no se salga de la pantalla por los lados.
 *
 * <h2>Por qué falla en silencio</h2>
 *
 * <p>El panel es `fixed`. Un elemento `fixed` que se sale de la ventana no
 * genera barra de desplazamiento ni ensancha el documento: el texto
 * sencillamente queda fuera y no hay forma de llegar a él. En un monitor no
 * ocurre nunca —sobra sitio a la derecha de cualquier disparador—, así que el
 * fallo solo existe en pantallas estrechas y no deja rastro en ninguna parte.
 *
 * <h2>Por qué se prueba la función y no el componente</h2>
 *
 * <p>Porque la función lee `window.innerWidth` del contexto en el que se
 * ejecuta. Montando el componente dentro de un iframe estrecho —que es como se
 * simula un teléfono— el código sigue leyendo el ancho de la ventana de
 * Karma, no el del iframe, y la prueba mediría un recorte contra la ventana
 * equivocada. Aquí no hace falta fingir nada: basta colocar el disparador
 * pegado al borde derecho de la ventana real, que es la misma situación.
 */
describe('posicionFlotante · no se sale por los lados', () => {
  /** Un disparador de mentira, del que solo importa dónde está. */
  function disparadorEn(izquierda: number, ancho = 16): HTMLElement {
    return {
      getBoundingClientRect: () => ({
        left: izquierda,
        right: izquierda + ancho,
        top: 100,
        bottom: 120,
        width: ancho,
        height: 20,
      }),
    } as unknown as HTMLElement;
  }

  const ANCHO_PANEL = 384;
  const MARGEN = 8;

  /** Lo que la ventana de Karma mida hoy; la prueba se adapta a ella. */
  const ventana = () => window.innerWidth;

  function izquierdaDe(estilos: Record<string, string>): number {
    return Number.parseFloat(estilos['left']);
  }

  it('alineado a la izquierda, un disparador pegado al borde derecho no lo empuja fuera', () => {
    const boton = disparadorEn(ventana() - 20);

    const estilos = posicionFlotante(boton, {
      altoMaximo: 280,
      ancho: 'contenido',
      anchoPanel: ANCHO_PANEL,
    });

    const izquierda = izquierdaDe(estilos);
    expect(izquierda + ANCHO_PANEL)
      .withContext(`terminaría en ${izquierda + ANCHO_PANEL} y la ventana mide ${ventana()}`)
      .toBeLessThanOrEqual(ventana() - MARGEN + 0.5);
    expect(izquierda).toBeGreaterThanOrEqual(MARGEN);
  });

  it('alineado a la derecha, un disparador pegado al borde izquierdo no lo saca por ahí', () => {
    const boton = disparadorEn(4);

    const estilos = posicionFlotante(boton, {
      altoMaximo: 280,
      alineacion: 'derecha',
      anchoPanel: ANCHO_PANEL,
    });

    expect(izquierdaDe(estilos))
      .withContext('el panel no puede empezar antes del margen')
      .toBeGreaterThanOrEqual(MARGEN);
  });

  it('cuando hay sitio de sobra, respeta el borde del disparador', () => {
    // Lo que no puede pasar es que el recorte mueva paneles que estaban bien.
    const boton = disparadorEn(100);

    const estilos = posicionFlotante(boton, {
      altoMaximo: 280,
      ancho: 'contenido',
      anchoPanel: ANCHO_PANEL,
    });

    expect(izquierdaDe(estilos))
      .withContext('sin conflicto, se alinea con el disparador y punto')
      .toBe(100);
  });

  it('un panel más ancho que la ventana entera se pega al margen izquierdo', () => {
    const boton = disparadorEn(200);

    const estilos = posicionFlotante(boton, {
      altoMaximo: 280,
      ancho: 'contenido',
      anchoPanel: ventana() + 200,
    });

    // No cabe de ninguna manera; lo unico sensato es empezar por el principio
    // y dejar que el `max-width` del panel haga el resto. Lo que no vale es
    // devolver un `left` negativo, que esconde justo el comienzo del texto.
    expect(izquierdaDe(estilos)).toBe(MARGEN);
  });

  it('sin la medida del panel, al menos no empieza fuera por la izquierda', () => {
    const boton = disparadorEn(-50);

    const estilos = posicionFlotante(boton, { altoMaximo: 280, ancho: 'contenido' });

    expect(izquierdaDe(estilos)).toBe(MARGEN);
  });
});
