import { AbstractControl, FormGroup } from '@angular/forms';
import { take } from 'rxjs';
import { ErrorDeApi, interpretarError } from '../../nucleo/errores';
import { AvisosService } from '../services/avisos.service';

/** La clave con la que se guarda un mensaje que vino del servidor. */
export const ERROR_DEL_SERVIDOR = 'servidor';

/** Lo que queda de un fallo después de repartir lo que tenía campo. */
export interface FalloColocado {
  readonly error: ErrorDeApi;
  /**
   * El mensaje que todavía hay que mostrar en alguna parte, o {@code null} si
   * todo encontró su campo y ya está dicho donde hay que corregirlo.
   */
  readonly mensajeGeneral: string | null;
  /** Título sugerido para ese mensaje, según de qué tipo de fallo se trate. */
  readonly titulo: string;
}

/**
 * Coloca junto a su campo cada mensaje que el servidor asoció a un campo.
 *
 * <h2>Por qué se reparte en lugar de mandarlo todo a un solo sitio</h2>
 *
 * <p>El backend distingue dos cosas que antes acababan juntas. Su respuesta de
 * validación trae un mapa <em>campo → qué le pasa</em> con <strong>todos</strong>
 * los campos inválidos —está escrito así a propósito, con el argumento de que
 * corregirlos de uno en uno con un viaje al servidor por cada uno no lo tolera
 * nadie—, mientras que un conflicto o una regla de negocio violada es un
 * mensaje único que no pertenece a ningún campo.
 *
 * <p>Volcarlo todo en un mensaje general desperdiciaba la mitad: en un
 * formulario de diez campos, «Revisa los campos señalados» no señala ninguno.
 *
 * <h2>Qué pasa con los campos que la pantalla no tiene</h2>
 *
 * <p>Su mensaje sale en el general. Es el caso del formulario que no muestra un
 * campo que el backend sí valida, y perderlo en silencio dejaría al usuario con
 * un botón en rojo y ninguna explicación — el peor de los dos males.
 *
 * <p>No muestra nada por su cuenta: quien llama decide si el mensaje general va
 * a un aviso flotante o a un banner de la pantalla.
 */
export function colocarEnCampos(
  fallo: unknown,
  formulario: FormGroup,
  porDefecto: string
): FalloColocado {
  const error = interpretarError(fallo, porDefecto);

  limpiarErroresDelServidor(formulario);

  const huerfanos: string[] = [];
  let primero: AbstractControl | null = null;

  for (const [campo, mensaje] of Object.entries(error.campos)) {
    const control = localizar(formulario, campo);
    if (!control) {
      huerfanos.push(mensaje);
      continue;
    }

    control.setErrors({ ...(control.errors ?? {}), [ERROR_DEL_SERVIDOR]: mensaje });
    control.markAsTouched();
    primero ??= control;

    // El mensaje caduca en cuanto se toca el campo: describe el valor que se
    // envió, no el que hay ahora. Se escucha solo este control y solo una vez,
    // que es lo que hace que corregir un campo no borre el aviso de otro.
    control.valueChanges.pipe(take(1)).subscribe(() => {
      const errores = { ...(control.errors ?? {}) };
      delete errores[ERROR_DEL_SERVIDOR];
      control.setErrors(Object.keys(errores).length ? errores : null);
    });
  }

  if (primero) {
    // Sin esto, en un formulario largo el campo señalado puede quedar fuera de
    // la vista: el botón se pone en rojo y no se ve ningún motivo.
    enfocar(formulario, primero);
  }

  return {
    error,
    mensajeGeneral: general(error, huerfanos, primero !== null),
    titulo: error.estado >= 500 ? 'Error del servidor' : 'No se pudo guardar',
  };
}

/**
 * Lo mismo, y el mensaje general al aviso de la esquina.
 *
 * <p>Es lo que quiere casi cualquier pantalla. Las que muestran el fallo en un
 * banner propio usan {@link colocarEnCampos} y deciden ellas dónde ponerlo.
 */
export function repartirFallo(
  fallo: unknown,
  formulario: FormGroup,
  avisos: AvisosService,
  porDefecto: string
): ErrorDeApi {
  const colocado = colocarEnCampos(fallo, formulario, porDefecto);

  if (colocado.mensajeGeneral) {
    avisos.error(colocado.mensajeGeneral, colocado.titulo);
  }

  return colocado.error;
}

/**
 * Quita los mensajes del servidor sin tocar los de la propia pantalla.
 *
 * <p>Se llama antes de cada envío: los del intento anterior ya no describen lo
 * que se acaba de mandar. Los validadores del formulario no se pierden porque
 * se vuelven a evaluar solos; el mensaje del servidor, en cambio, nadie lo
 * vuelve a poner si no lo manda otra vez.
 */
export function limpiarErroresDelServidor(formulario: FormGroup): void {
  for (const control of Object.values(formulario.controls)) {
    if (!control.errors?.[ERROR_DEL_SERVIDOR]) {
      continue;
    }
    const errores = { ...control.errors };
    delete errores[ERROR_DEL_SERVIDOR];
    control.setErrors(Object.keys(errores).length ? errores : null);
  }
}

/**
 * @param algunoColocado si al menos un mensaje llegó a su campo, en cuyo caso
 *     el general se calla: ya está dicho dónde hay que corregir, y repetirlo
 *     aleja el texto de lo que hay que arreglar
 */
function general(error: ErrorDeApi, huerfanos: string[], algunoColocado: boolean): string | null {
  if (huerfanos.length > 0) {
    return huerfanos.join(' ');
  }
  if (algunoColocado) {
    return null;
  }

  // La incidencia es el único dato con el que podemos encontrar su caso en el
  // log, así que no se pierde aunque el mensaje venga por otro lado.
  return error.incidencia && !error.mensaje.includes(error.incidencia)
    ? `${error.mensaje} (incidencia ${error.incidencia})`
    : error.mensaje;
}

/**
 * Busca el control por el nombre que usa el backend.
 *
 * <p>Se prueba la ruta completa y luego el último tramo. Los nombres coinciden
 * porque el DTO y el formulario describen lo mismo, pero un campo anidado llega
 * como {@code direccion.ubigeo} y en la pantalla puede ser un control plano
 * llamado {@code ubigeo}.
 */
function localizar(formulario: FormGroup, campo: string): AbstractControl | null {
  const directo = formulario.get(campo);
  if (directo) {
    return directo;
  }

  const ultimo = campo.split('.').pop();
  return ultimo && ultimo !== campo ? formulario.get(ultimo) : null;
}

/**
 * Lleva la vista al campo señalado.
 *
 * <p>Se localiza por el {@code id} del elemento, que en nuestras plantillas
 * coincide con el nombre del control porque es lo que necesita la
 * {@code <label for>}. Si no coincidiera, no pasa nada: no se desplaza.
 */
function enfocar(formulario: FormGroup, control: AbstractControl): void {
  const nombre = Object.keys(formulario.controls).find(
    (clave) => formulario.controls[clave] === control
  );
  if (!nombre) {
    return;
  }

  const elemento = document.getElementById(nombre);

  // La preferencia se consulta aquí y no en CSS: el `behavior` que se pasa a
  // `scrollIntoView` gana sobre `scroll-behavior`, así que la regla global de
  // `prefers-reduced-motion` no alcanza a este desplazamiento.
  const sinMovimiento = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  elemento?.scrollIntoView({ block: 'center', behavior: sinMovimiento ? 'auto' : 'smooth' });
  elemento?.focus?.({ preventScroll: true });
}
