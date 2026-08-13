import { Component, Input, Output, EventEmitter } from '@angular/core';

/**
 * Diálogo de confirmación para acciones irreversibles.
 *
 * Criterio del plan de vistas §9.4: se confirma solo lo irreversible.
 * Anular un comprobante lo exige; guardar un borrador no. Usarlo en todo
 * lo demás entrena al usuario a aceptar sin leer, que es justamente lo que
 * se busca evitar en la acción que sí importa.
 */
@Component({
  selector: 'app-confirmacion',
  imports: [],
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
