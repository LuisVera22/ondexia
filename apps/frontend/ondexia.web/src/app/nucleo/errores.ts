import { HttpErrorResponse } from '@angular/common/http';

/**
 * El cuerpo que manda nuestro backend: `application/problem+json` (RFC 9457)
 * con dos añadidos propios, `codigo` y —según el caso— `campos` o `incidencia`.
 */
interface ProblemaDeApi {
  readonly detail?: string;
  readonly title?: string;
  /** Código estable del error. Su presencia es lo que acredita el origen. */
  readonly codigo?: string;
  /** Solo en validación: nombre del campo → qué le pasa. */
  readonly campos?: Record<string, string>;
  /** Solo en los 500: el identificador que hay que citar al reportar. */
  readonly incidencia?: string;
}

/** Un error de la API ya interpretado, listo para pintar. */
export interface ErrorDeApi {
  /** El código HTTP. 0 cuando la petición no llegó a salir. */
  readonly estado: number;
  /** El `codigo` del backend, o `desconocido` si la respuesta no es nuestra. */
  readonly codigo: string;
  /** Texto para el usuario. Siempre presente y siempre seguro de mostrar. */
  readonly mensaje: string;
  /** Campo → mensaje. Vacío salvo en validación. */
  readonly campos: Record<string, string>;
  /** El identificador de incidencia de un 500 nuestro, si vino. */
  readonly incidencia: string | null;
}

/**
 * Mensajes para cuando la respuesta no es de nuestro manejador de errores.
 *
 * <p>Son deliberadamente vagos. Aquí caen las respuestas de la infraestructura
 * —el balanceador, la pasarela, un tiempo de espera agotado— y esas no están
 * escritas para nadie que use la aplicación.
 */
const POR_ESTADO: Record<number, string> = {
  0: 'No se pudo contactar con el servidor. Revisa tu conexión y vuelve a intentarlo.',
  400: 'La solicitud no es válida. Revisa los datos e inténtalo de nuevo.',
  401: 'Tu sesión ya no es válida. Vuelve a ingresar.',
  403: 'No tienes permiso para realizar esta acción.',
  404: 'No encontramos lo que buscabas.',
  409: 'La operación choca con un dato existente.',
  413: 'El archivo es demasiado grande.',
  429: 'Demasiadas solicitudes seguidas. Espera un momento y vuelve a intentarlo.',
  502: 'El servicio no está respondiendo. Vuelve a intentarlo en unos segundos.',
  503: 'El servicio no está disponible en este momento.',
  504: 'El servidor tardó demasiado en responder. Vuelve a intentarlo.',
};

/**
 * Convierte lo que sea que falló en algo que se puede mostrar.
 *
 * <h2>Por qué no se muestra el error tal cual viene</h2>
 *
 * <p>Porque no todo lo que responde con un código de error lo escribió nuestro
 * backend. Delante hay una pasarela y un balanceador que responden por su
 * cuenta cuando la función no arranca, tarda demasiado o se pasa de cuota, y
 * esos cuerpos —a veces HTML, a veces JSON con la traza— no son para el
 * usuario: describen la infraestructura.
 *
 * <p>Se distingue por la presencia de {@code codigo}. Nuestro manejador lo pone
 * en <strong>todas</strong> las respuestas de error sin excepción, y nada de lo
 * que hay delante lo pone. Con él, se muestra el {@code detail}, que está
 * escrito para leerse. Sin él, se usa el mensaje genérico del código HTTP y el
 * cuerpo original se descarta sin mirarlo.
 *
 * <p>Se ignora también el {@code title}: es la frase estándar del código
 * —«Conflict», «Bad Request»— y en la respuesta de una pasarela puede traer el
 * nombre del servicio que falló.
 */
export function interpretarError(fallo: unknown, porDefecto: string): ErrorDeApi {
  const estado = fallo instanceof HttpErrorResponse ? fallo.status : 0;
  const cuerpo = cuerpoDe(fallo);

  // Sin código no es nuestro, y punto. Nada del cuerpo se usa.
  if (!cuerpo?.codigo) {
    return {
      estado,
      codigo: 'desconocido',
      mensaje: POR_ESTADO[estado] ?? porDefecto,
      campos: {},
      incidencia: null,
    };
  }

  return {
    estado,
    codigo: cuerpo.codigo,
    mensaje: cuerpo.detail?.trim() || porDefecto,
    campos: camposLegibles(cuerpo.campos),
    incidencia: typeof cuerpo.incidencia === 'string' ? cuerpo.incidencia : null,
  };
}

/**
 * El mensaje, para quien solo necesita eso.
 *
 * <p>Es lo que usan las pantallas al cargar, donde no hay formulario en el que
 * colocar nada. Al enviar un formulario conviene {@link interpretarError}, para
 * que los mensajes de campo lleguen a su campo.
 */
export function mensajeDeError(fallo: unknown, porDefecto: string): string {
  const error = interpretarError(fallo, porDefecto);

  // La incidencia se añade al mensaje porque es el único dato que el usuario
  // puede darnos para encontrar su caso en el log. El backend ya la mete en el
  // detalle, pero solo cuando el detalle es el genérico; si algún día deja de
  // hacerlo, esto lo cubre sin repetirla.
  if (error.incidencia && !error.mensaje.includes(error.incidencia)) {
    return `${error.mensaje} (incidencia ${error.incidencia})`;
  }
  return error.mensaje;
}

function cuerpoDe(fallo: unknown): ProblemaDeApi | null {
  const cuerpo = (fallo as { error?: unknown })?.error;

  // Un cuerpo que no es objeto —HTML de una pasarela, texto suelto— no se
  // inspecciona: no hay nada que sacar de ahí que se pueda mostrar.
  if (!cuerpo || typeof cuerpo !== 'object' || Array.isArray(cuerpo)) {
    return null;
  }
  return cuerpo as ProblemaDeApi;
}

/**
 * Se queda solo con las entradas que son texto.
 *
 * <p>El mapa viene del servidor y se pinta junto a un campo. Un valor que no
 * sea cadena acabaría en pantalla como `[object Object]`, y una clave vacía no
 * tiene campo al que ir.
 */
function camposLegibles(campos: unknown): Record<string, string> {
  if (!campos || typeof campos !== 'object' || Array.isArray(campos)) {
    return {};
  }

  const limpios: Record<string, string> = {};
  for (const [campo, mensaje] of Object.entries(campos as Record<string, unknown>)) {
    if (campo && typeof mensaje === 'string' && mensaje.trim()) {
      limpios[campo] = mensaje.trim();
    }
  }
  return limpios;
}
