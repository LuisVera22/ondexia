import { Component, Input } from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { ERROR_DEL_SERVIDOR } from '../../../formularios/fallo-de-formulario';

/**
 * El mensaje de error de un campo, debajo del campo.
 *
 * <h2>Por qué es un componente y no tres líneas en cada plantilla</h2>
 *
 * <p>Porque ahora hay dos fuentes de error para el mismo campo: lo que sabe la
 * pantalla —obligatorio, formato— y lo que solo sabe el servidor —ese código ya
 * existe, ese RUC no está en el padrón—. Escrito a mano, cada campo tendría que
 * decidir cuál de los dos mostrar, y treinta campos es treinta oportunidades de
 * mostrar el genérico cuando había uno concreto.
 *
 * <p>La regla es una y vive aquí: <strong>si el servidor dijo algo, gana</strong>.
 * Lo dice sabiendo lo que la pantalla no puede saber, y suele ser más
 * específico que «revisa este campo».
 *
 * <p>Ejemplo de uso:
 *
 * <pre>
 *   &lt;app-error-campo [control]="controles.codigo"
 *                    mensaje="Cuatro dígitos, del 0000 al 9999." /&gt;
 * </pre>
 */
@Component({
  selector: 'app-error-campo',
  imports: [],
  template: `
    @if (visible) {
    <p class="mt-1.5 text-xs text-error-500">{{ texto }}</p>
    }
  `,
})
export class ErrorCampoComponent {
  /** El control al que pertenece el mensaje. */
  @Input() control: AbstractControl | null = null;

  /** Qué decir cuando el error lo detectó la pantalla. */
  @Input() mensaje = '';

  /**
   * Solo cuando se ha tocado el campo.
   *
   * <p>Un formulario recién abierto tiene todos los obligatorios inválidos, y
   * sin esta condición se abriría en rojo entero antes de que nadie escriba
   * nada.
   */
  get visible(): boolean {
    return !!this.control && this.control.touched && this.control.invalid && !!this.texto;
  }

  get texto(): string {
    const delServidor = this.control?.errors?.[ERROR_DEL_SERVIDOR];
    return typeof delServidor === 'string' ? delServidor : this.mensaje;
  }
}
