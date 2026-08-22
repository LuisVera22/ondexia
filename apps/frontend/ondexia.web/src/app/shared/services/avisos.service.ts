import { Injectable, signal } from '@angular/core';

export type TipoAviso = 'exito' | 'error' | 'info';

export interface Aviso {
  readonly id: number;
  readonly tipo: TipoAviso;
  readonly mensaje: string;
  /** Opcional. Sin él, el mensaje va solo, que es lo normal. */
  readonly titulo?: string;
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
    const temporizador = this.temporizadores.get(id);
    if (temporizador) {
      clearTimeout(temporizador);
      this.temporizadores.delete(id);
    }
    this._avisos.update((avisos) => avisos.filter((aviso) => aviso.id !== id));
  }

  /** Al cambiar de pantalla, lo que quedaba en cola ya no viene al caso. */
  limpiar(): void {
    this.temporizadores.forEach((temporizador) => clearTimeout(temporizador));
    this.temporizadores.clear();
    this._avisos.set([]);
  }

  private mostrar(tipo: TipoAviso, mensaje: string, titulo?: string): void {
    const id = this.siguienteId++;

    // El nuevo va primero: aparece pegado a la barra superior, donde está
    // mirando quien acaba de pulsar. Añadiéndolo al final, el aviso recién
    // creado saldría debajo de los que ya estaban.
    this._avisos.update((avisos) => [{ id, tipo, mensaje, titulo }, ...avisos]);

    this.temporizadores.set(
      id,
      setTimeout(() => this.cerrar(id), DURACION_MS[tipo])
    );
  }
}
