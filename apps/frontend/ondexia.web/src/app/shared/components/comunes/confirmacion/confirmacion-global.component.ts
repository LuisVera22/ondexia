import { Component, inject } from '@angular/core';
import { ConfirmacionComponent } from './confirmacion.component';
import { ConfirmacionesService } from '../../../services/confirmaciones.service';

/**
 * Pinta la confirmación que pidió alguien sin plantilla.
 *
 * <p>Se monta una sola vez, en el marco de la aplicación, junto a los avisos y
 * por el mismo motivo: quien pregunta puede ser una guarda de ruta que está
 * precisamente destruyendo la pantalla, y un diálogo montado en ella
 * desaparecería con la navegación que intenta detener.
 *
 * <p>No sustituye a {@link ConfirmacionComponent} en las pantallas. Una pantalla
 * que confirma una acción propia lo monta ella, que es más directo y no necesita
 * pasar por un servicio.
 */
@Component({
  selector: 'app-confirmacion-global',
  imports: [ConfirmacionComponent],
  template: `
    @if (peticion(); as actual) {
    <app-confirmacion
      [abierto]="true"
      [titulo]="actual.titulo"
      [mensaje]="actual.mensaje"
      [textoConfirmar]="actual.textoConfirmar || 'Confirmar'"
      [textoCancelar]="actual.textoCancelar || 'Cancelar'"
      [peligrosa]="actual.peligrosa || false"
      (confirmar)="confirmaciones.responder(true)"
      (cancelar)="confirmaciones.responder(false)"
    />
    }
  `,
})
export class ConfirmacionGlobalComponent {
  protected readonly confirmaciones = inject(ConfirmacionesService);
  protected readonly peticion = this.confirmaciones.peticion;
}
