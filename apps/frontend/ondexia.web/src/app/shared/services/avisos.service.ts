import { Injectable, signal } from '@angular/core';

export type TipoAviso = 'exito' | 'error' | 'info';

export interface Aviso {
  /** Identifica esta aparición concreta. Útil para depurar y para pruebas. */
  readonly id: number;
  readonly tipo: TipoAviso;
  readonly mensaje: string;
  /** Opcional. Sin él, el mensaje va solo, que es lo normal. */
  readonly titulo?: string;
}

/**
 * Cuánto vive el aviso en pantalla.
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
 * El aviso flotante de la esquina superior derecha. Uno, y solo uno.
 *
 * <h2>Uno a la vez, y el nuevo desplaza al anterior</h2>
 *
 * <p>La primera versión apilaba. Siete pulsaciones de «Guardar» producían siete
 * tarjetas idénticas que desbordaban la ventana y tapaban el formulario. Se
 * intentó arreglar con un tope de tres, un contador de repeticiones y una regla
 * para no descartar errores sin leer — tres mecanismos para un problema que no
 * existe si solo hay un aviso.
 *
 * <p>Con uno, el más reciente es el que vale. Y en la práctica eso es también lo
 * correcto: la secuencia habitual es «falla el guardado, corrijo, vuelvo a
 * guardar», donde el acierto que desplaza al error lo hace porque el error ya
 * no describe la situación.
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
  private readonly _aviso = signal<Aviso | null>(null);
  readonly aviso = this._aviso.asReadonly();

  private siguienteId = 1;
  private temporizador: ReturnType<typeof setTimeout> | null = null;

  exito(mensaje: string, titulo?: string): void {
    this.mostrar('exito', mensaje, titulo);
  }

  error(mensaje: string, titulo?: string): void {
    this.mostrar('error', mensaje, titulo);
  }

  info(mensaje: string, titulo?: string): void {
    this.mostrar('info', mensaje, titulo);
  }

  cerrar(): void {
    this.detenerCuentaAtras();
    this._aviso.set(null);
  }

  private mostrar(tipo: TipoAviso, mensaje: string, titulo?: string): void {
    // Reemplaza sin preguntar: el más reciente es el que vale. Repetir el mismo
    // mensaje no cambia nada visible, y la señal de que la acción se ha vuelto a
    // ejecutar la da el estado del botón, no esta tarjeta.
    this._aviso.set({ id: this.siguienteId++, tipo, mensaje, titulo });

    this.detenerCuentaAtras();
    this.temporizador = setTimeout(() => this.cerrar(), DURACION_MS[tipo]);
  }

  private detenerCuentaAtras(): void {
    if (this.temporizador) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
  }
}
