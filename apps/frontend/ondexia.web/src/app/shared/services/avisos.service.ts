import { Injectable, signal } from '@angular/core';

export type TipoAviso = 'exito' | 'error' | 'info';

export interface Aviso {
  readonly id: number;
  readonly tipo: TipoAviso;
  readonly mensaje: string;
  /** Opcional. Sin él, el mensaje va solo, que es lo normal. */
  readonly titulo?: string;
  /** Cuántas veces se ha pedido este mismo aviso. Se pinta desde 2. */
  readonly repeticiones: number;
}

/**
 * Cuánto vive cada aviso en pantalla.
 *
 * <p>El error dura más del doble, y no es arbitrario: un acierto solo confirma
 * lo que el usuario esperaba y se lee de un vistazo, mientras que un error trae
 * el mensaje del servidor —a veces largo, a veces con un dato que hay que
 * copiar— y desaparecer antes de que se termine de leer obliga a repetir la
 * acción para volver a verlo.
 */
const DURACION_MS: Record<TipoAviso, number> = {
  exito: 4000,
  info: 5000,
  error: 9000,
};

/**
 * Cuántos avisos se muestran a la vez.
 *
 * <p>Tres. Es un tope de presentación, no de cortesía: la pila crece hacia
 * abajo desde la esquina y a partir del cuarto empieza a tapar el contenido de
 * la pantalla — y nadie lee siete tarjetas, así que las de abajo solo estorban.
 * Al llegar al límite se retira la más antigua, que es la que ya ha tenido su
 * tiempo en pantalla.
 */
const MAXIMO_VISIBLES = 3;

/**
 * Avisos flotantes de la esquina superior derecha.
 *
 * <h2>Cuándo un aviso y cuándo no</h2>
 *
 * <p>El criterio no es «toda petición lleva aviso», sino <strong>si el efecto
 * de la acción se ve en pantalla</strong>:
 *
 * <ul>
 *   <li>Si el efecto es visible y el control sigue ahí —consultar un RUC y ver
 *       los campos rellenarse— basta el estado del propio botón. Un aviso
 *       encima repetiría lo que ya se ve.</li>
 *   <li>Si la acción cierra un modal o cambia de pantalla, el aviso es
 *       obligatorio: el botón que lo diría desaparece con el modal, y sin aviso
 *       el usuario no tiene forma de saber si se guardó.</li>
 * </ul>
 *
 * <p>Los errores de validación de un campo <strong>no</strong> van aquí: van
 * junto al campo. Un aviso en la esquina aleja el mensaje de lo que hay que
 * corregir, y en un formulario largo obliga a buscar cuál de los diez campos
 * era.
 */
@Injectable({ providedIn: 'root' })
export class AvisosService {
  private readonly _avisos = signal<Aviso[]>([]);
  readonly avisos = this._avisos.asReadonly();

  private siguienteId = 1;

  /** Los temporizadores se guardan para poder cancelarlos al cerrar a mano. */
  private readonly temporizadores = new Map<number, ReturnType<typeof setTimeout>>();

  exito(mensaje: string, titulo?: string): void {
    this.mostrar('exito', mensaje, titulo);
  }

  error(mensaje: string, titulo?: string): void {
    this.mostrar('error', mensaje, titulo);
  }

  info(mensaje: string, titulo?: string): void {
    this.mostrar('info', mensaje, titulo);
  }

  cerrar(id: number): void {
    this.cancelarTemporizador(id);
    this._avisos.update((avisos) => avisos.filter((aviso) => aviso.id !== id));
  }

  /** Al cambiar de pantalla, lo que quedaba en cola ya no viene al caso. */
  limpiar(): void {
    this.temporizadores.forEach((temporizador) => clearTimeout(temporizador));
    this.temporizadores.clear();
    this._avisos.set([]);
  }

  private mostrar(tipo: TipoAviso, mensaje: string, titulo?: string): void {
    // Guardar dos veces seguidas produce el mismo aviso dos veces, y la segunda
    // tarjeta no dice nada que no dijera la primera. En vez de apilar un
    // duplicado se cuenta la repetición y se le devuelve el tiempo completo en
    // pantalla: lo único nuevo es que ha vuelto a pasar, y eso cabe en un
    // número. Sin esto, pulsar «Guardar» siete veces llenaba la pantalla de
    // tarjetas idénticas.
    const repetido = this._avisos().find(
      (aviso) => aviso.tipo === tipo && aviso.mensaje === mensaje && aviso.titulo === titulo
    );

    if (repetido) {
      this._avisos.update((avisos) =>
        avisos.map((aviso) =>
          aviso.id === repetido.id
            ? { ...aviso, repeticiones: aviso.repeticiones + 1 }
            : aviso
        )
      );
      this.programarCierre(repetido.id, tipo);
      return;
    }

    const id = this.siguienteId++;

    // El nuevo va primero: aparece pegado a la barra superior, donde está
    // mirando quien acaba de pulsar. Añadiéndolo al final, el aviso recién
    // creado saldría debajo de los que ya estaban.
    this._avisos.update((avisos) =>
      this.recortarAlMaximo([{ id, tipo, mensaje, titulo, repeticiones: 1 }, ...avisos])
    );

    this.programarCierre(id, tipo);
  }

  /**
   * Deja la pila en {@link MAXIMO_VISIBLES}, sacrificando primero lo prescindible.
   *
   * <h2>Los errores se descartan últimos</h2>
   *
   * <p>El orden de llegada no es el criterio. Con un simple «fuera el más
   * antiguo», un «guardado» podía expulsar de la pantalla un error que el
   * usuario todavía no había leído — y perder un aviso de éxito solo cuesta una
   * confirmación de algo que ya se esperaba, mientras que perder un error deja a
   * alguien sin saber que su operación no se hizo.
   *
   * <p>Así que se retira el más antiguo de entre los que no son errores, y solo
   * cuando los tres son errores se retira el error más antiguo.
   */
  private recortarAlMaximo(avisos: Aviso[]): Aviso[] {
    const restantes = [...avisos];

    while (restantes.length > MAXIMO_VISIBLES) {
      // La lista va del más nuevo al más viejo, así que se busca desde el final.
      let indice = restantes.length - 1;
      for (let i = restantes.length - 1; i >= 0; i--) {
        if (restantes[i].tipo !== 'error') {
          indice = i;
          break;
        }
      }
      this.cancelarTemporizador(restantes[indice].id);
      restantes.splice(indice, 1);
    }

    return restantes;
  }

  /** Reinicia la cuenta atrás de un aviso, o la arranca si no la tenía. */
  private programarCierre(id: number, tipo: TipoAviso): void {
    this.cancelarTemporizador(id);
    this.temporizadores.set(
      id,
      setTimeout(() => this.cerrar(id), DURACION_MS[tipo])
    );
  }

  private cancelarTemporizador(id: number): void {
    const temporizador = this.temporizadores.get(id);
    if (temporizador) {
      clearTimeout(temporizador);
      this.temporizadores.delete(id);
    }
  }
}
