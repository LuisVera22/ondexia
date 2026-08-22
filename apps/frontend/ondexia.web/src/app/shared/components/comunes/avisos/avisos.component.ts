import { Component, computed, inject } from '@angular/core';
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

  /**
   * El aviso como lista de cero o un elemento, para poder recorrerlo con
   * {@code @for}.
   *
   * <p>Parece un rodeo y no lo es. Con {@code @if}, Angular reutiliza el nodo
   * cuando cambia el contenido, y una animación CSS no vuelve a correr sobre un
   * nodo que no se ha recreado: al reemplazar un aviso por otro con el mismo
   * texto, no se movía nada. Recorriendo con {@code track actual.id} el nodo se
   * destruye y se crea en cada aviso, y la entrada se anima siempre.
   */
  readonly avisoComoLista = computed(() => {
    const actual = this.servicio.aviso();
    return actual ? [actual] : [];
  });

  cerrar(): void {
    this.servicio.cerrar();
  }
}
