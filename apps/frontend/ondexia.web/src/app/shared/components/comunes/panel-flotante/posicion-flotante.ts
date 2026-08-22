/** Estilos listos para aplicar con `[ngStyle]` sobre el panel. */
export type PosicionFlotante = Record<string, string>;

export interface OpcionesDePosicion {
  /** Cuánto puede crecer el panel antes de desplazarse por dentro. */
  altoMaximo: number;

  /**
   * Con qué borde del disparador se alinea.
   *
   * <p>A la izquierda para un campo de formulario, que continúa la columna del
   * texto. A la derecha para un menú que cuelga de un botón pegado al borde de
   * la tabla: alineado a la izquierda se saldría de la pantalla.
   */
  alineacion?: 'izquierda' | 'derecha';

  /**
   * `disparador` iguala el ancho del botón —es lo que hace que un desplegable
   * de formulario parezca el mismo control abierto y cerrado—. `contenido` deja
   * que lo decida el texto, con el ancho del botón como mínimo.
   */
  ancho?: 'disparador' | 'contenido';

  /** Separación entre el disparador y el panel. */
  separacion?: number;

  /**
   * Ancho real del panel, si ya está en el DOM.
   *
   * <p>Solo lo usa la alineación a la derecha, y es la diferencia entre que
   * cuadre y que no. Con `right` el navegador mide desde el borde del bloque
   * contenedor, que `scrollbar-gutter: stable` encoge —1270 px de 1280— sin que
   * ni `innerWidth` ni `clientWidth` lo digan: el panel quedaba diez píxeles
   * desplazado sin motivo aparente. Sabiendo el ancho se coloca con `left`, que
   * parte del cero y no tiene esa ambigüedad.
   *
   * <p>Sin él se hace lo que se puede con `right`, para el primer pintado en el
   * que el panel todavía no existe y no se puede medir.
   */
  anchoPanel?: number;
}

/**
 * Coloca un panel flotante respecto del control que lo abre.
 *
 * <h2>Por qué `fixed` y no `absolute`</h2>
 *
 * <p>Porque `absolute` se mide contra el ascendiente posicionado más cercano, y
 * el panel se recorta contra cualquier ascendiente con `overflow` distinto de
 * `visible`. Las tablas de la aplicación viven dentro de un contenedor con
 * `overflow-x-auto` para poder desplazarse en pantallas estrechas: un panel
 * `absolute` abierto en la última fila se corta por el borde de la tarjeta.
 * Con `fixed` se mide contra la ventana y no lo recorta nada.
 *
 * <p>El precio es que hay que recalcular al desplazar y al cambiar de tamaño
 * —un elemento `fixed` no acompaña a su disparador—, y por eso quien lo use
 * tiene que escuchar esos dos eventos mientras el panel esté abierto. En fase
 * de captura, además, o no se entera del desplazamiento de los contenedores
 * internos, que no burbujea hasta `window`.
 *
 * <h2>Hacia dónde se abre</h2>
 *
 * <p>Hacia abajo, salvo que no quepa y arriba haya más sitio. No se fuerza
 * hacia arriba en cuanto falta un poco: cambiar de lado es desorientador, y con
 * el panel desplazándose por dentro caben igualmente todas las opciones.
 */
export function posicionFlotante(
  disparador: HTMLElement,
  opciones: OpcionesDePosicion
): PosicionFlotante {
  const { altoMaximo, alineacion = 'izquierda', ancho = 'disparador' } = opciones;
  const separacion = opciones.separacion ?? 4;

  const marco = disparador.getBoundingClientRect();
  const debajo = window.innerHeight - marco.bottom;
  const haciaArriba = debajo < altoMaximo && marco.top > debajo;

  const estilos: PosicionFlotante = {
    position: 'fixed',
    [haciaArriba ? 'bottom' : 'top']: haciaArriba
      ? `${window.innerHeight - marco.top + separacion}px`
      : `${marco.bottom + separacion}px`,
    'max-height': `${Math.max(
      160,
      Math.min(altoMaximo, (haciaArriba ? marco.top : debajo) - 12)
    )}px`,
  };

  if (alineacion === 'derecha') {
    if (opciones.anchoPanel) {
      estilos['left'] = `${Math.max(8, marco.right - opciones.anchoPanel)}px`;
    } else {
      estilos['right'] = `${window.innerWidth - marco.right}px`;
    }
  } else {
    estilos['left'] = `${marco.left}px`;
  }

  estilos[ancho === 'contenido' ? 'min-width' : 'width'] = `${marco.width}px`;

  return estilos;
}
