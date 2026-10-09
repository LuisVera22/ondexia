import { Component, Input, Output, EventEmitter } from '@angular/core';
import { BotonComponent } from '../boton/boton.component';
import { ModalComponent } from '../modal/modal.component';

/**
 * Diálogo de confirmación para acciones irreversibles.
 *
 * Se construye sobre {@link ModalComponent}, que es el único diálogo del
 * sistema. Antes tenía su propia implementación: el cierre con `Escape`, la
 * retención del foco y el bloqueo del desplazamiento estaban escritos dos
 * veces, y cualquier arreglo había que hacerlo en los dos sitios.
 *
 * Criterio del plan de vistas §9.4: se confirma solo lo irreversible.
 * Anular un comprobante lo exige; guardar un borrador no. Usarlo en todo
 * lo demás entrena al usuario a aceptar sin leer, que es justamente lo que
 * se busca evitar en la acción que sí importa.
 */
@Component({
  selector: 'app-confirmacion',
  imports: [ModalComponent, BotonComponent],
  templateUrl: './confirmacion.component.html',
})
export class ConfirmacionComponent {
  @Input() abierto = false;
  @Input() titulo = '¿Confirma la acción?';

  /** Qué va a pasar exactamente. Debe ser específico, no genérico. */
  @Input() mensaje = '';

  @Input() textoConfirmar = 'Confirmar';
  @Input() textoCancelar = 'Cancelar';

  /** Aplica el color de peligro cuando la acción destruye o anula algo. */
  @Input() peligrosa = false;

  /** Deshabilita el botón mientras la acción está en curso. */
  @Input() procesando = false;

  @Output() confirmar = new EventEmitter<void>();
  @Output() cancelar = new EventEmitter<void>();
}
