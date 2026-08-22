import { Component, inject } from '@angular/core';
import { AvisosService } from '../../../services/avisos.service';

/**
 * El aviso flotante. Se monta una sola vez, en el marco.
 *
 * <p>Va en el marco y no en cada pantalla por lo mismo que el menú: si cada
 * página montara el suyo, un aviso lanzado justo antes de navegar se destruiría
 * con la pantalla que lo pidió — y ese es precisamente el caso más frecuente,
 * porque guardar suele terminar en una navegación. De hecho es lo que hace que
 * al cambiar de empresa y perder el acceso a la configuración, el aviso siga en
 * pantalla para explicar por qué acabaste en «sin permisos».
 *
 * <p>Sobre la accesibilidad: la región es {@code aria-live} para que un lector
 * de pantalla anuncie el aviso sin que el usuario tenga que ir a buscarlo. Los
 * errores usan {@code assertive} y {@code role="alert"} —interrumpen— y el
 * resto {@code polite}, que espera a que el lector termine la frase en curso.
 * Poner todo en assertive haría que un «guardado» cortara la lectura de
 * cualquier otra cosa.
 */
@Component({
  selector: 'app-avisos',
  imports: [],
  templateUrl: './avisos.component.html',
})
export class AvisosComponent {
  private readonly servicio = inject(AvisosService);

  readonly aviso = this.servicio.aviso;

  cerrar(): void {
    this.servicio.cerrar();
  }
}
