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
 *
 * <h2>Y sin salirse por los lados</h2>
 *
 * <p>La posición horizontal se recorta contra la ventana. Alinear con el borde
 * del disparador funciona en un monitor y falla en un teléfono: un globo de
 * 384 px abierto desde un icono que está en el píxel 165 de una pantalla de 375
 * se sale por la derecha y se lee la mitad. Al ser `fixed` no genera barra de
 * desplazamiento, así que el texto sencillamente no existe.
 *
 * <p>Para recortar hace falta saber cuánto mide el panel, y eso solo se sabe
 * cuando ya está en el DOM: quien lo use debe medirlo y volver a llamar con
 * {@code anchoPanel}. Sin ese dato se hace lo único que se puede sin medir
 * —no dejar que empiece antes del margen izquierdo— y el resto lo sostiene el
 * `max-width` del propio panel, que debe estar atado a la ventana.
 */
/** Aire entre el panel y el borde de la ventana. */
const MARGEN = 8;

/**
 * La ventana que de verdad se ve.
 *
 * <p>`window.innerWidth` no sirve para recortar. Incluye el hueco de la barra
 * de desplazamiento, asi que siempre sobrestima el sitio disponible; y en el
 * emulador de dispositivo de Chrome llega a mentir de largo — medido en una
 * pantalla de 440: `innerWidth` decia 665, doscientos veinticinco de mas.
 * Recortando contra ese numero, un panel se coloca «dentro» de una ventana que
 * no existe y aparece cortado igual.
 *
 * <p>`documentElement.clientWidth` es el area de maquetacion: lo mismo que
 * midieron `visualViewport.width` y el ancho del `body` en aquella pantalla.
 *
 * <h3>Y por que el alto NO sigue la misma regla</h3>
 *
 * <p>Porque de la altura no hay medida. El cambio a `clientHeight` se hizo por
 * simetria con el ancho, sin comprobarlo, y el menu de acciones empezo a
 * abrirse hacia arriba anclado a un borde que no era: la decision «cabe
 * debajo» le salia que no. El alto se queda en `innerHeight` —el numero con el
 * que funcionaba— hasta tener las tres medidas al lado.
 *
 * <p>Si `innerHeight` exagera, el fallo es que el panel se abre hacia abajo
 * cuando cabria mejor arriba. Molesto y visible. Si se queda corto, el panel
 * salta a otro sitio de la pantalla, que es lo que se veia.
 */
const ventana = () => ({
  ancho: document.documentElement.clientWidth,
  alto: window.innerHeight,
});

const entre = (minimo: number, valor: number, maximo: number): number =>
  Math.max(minimo, Math.min(valor, maximo));

export function posicionFlotante(
  disparador: HTMLElement,
  opciones: OpcionesDePosicion
): PosicionFlotante {
  const { altoMaximo, alineacion = 'izquierda', ancho = 'disparador' } = opciones;
  const separacion = opciones.separacion ?? 4;

  const marco = disparador.getBoundingClientRect();
  const vista = ventana();
  const debajo = vista.alto - marco.bottom;
  const haciaArriba = debajo < altoMaximo && marco.top > debajo;

  const estilos: PosicionFlotante = {
    position: 'fixed',
    [haciaArriba ? 'bottom' : 'top']: haciaArriba
      ? `${vista.alto - marco.top + separacion}px`
      : `${marco.bottom + separacion}px`,
    'max-height': `${Math.max(
      160,
      Math.min(altoMaximo, (haciaArriba ? marco.top : debajo) - 12)
    )}px`,
  };

  const anchoPanel = opciones.anchoPanel;

  if (anchoPanel) {
    // Con la medida en la mano, los dos casos son el mismo: se calcula donde
    // querria empezar el panel y se recorta a lo que cabe. Y se coloca con
    // `left` tambien en la alineacion a la derecha, porque `right` mide desde
    // el borde del bloque contenedor, que `scrollbar-gutter: stable` encoge sin
    // que ni `innerWidth` ni `clientWidth` lo digan.
    const deseado = alineacion === 'derecha' ? marco.right - anchoPanel : marco.left;
    const ultimo = vista.ancho - anchoPanel - MARGEN;
    estilos['left'] = `${entre(MARGEN, deseado, Math.max(MARGEN, ultimo))}px`;
  } else if (alineacion === 'derecha') {
    estilos['right'] = `${Math.max(MARGEN, vista.ancho - marco.right)}px`;
  } else {
    estilos['left'] = `${Math.max(MARGEN, marco.left)}px`;
  }

  estilos[ancho === 'contenido' ? 'min-width' : 'width'] = `${marco.width}px`;

  return estilos;
}
