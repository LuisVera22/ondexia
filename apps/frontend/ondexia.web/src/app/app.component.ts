import { Component, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { TemaService } from './shared/services/tema.service';

/**
 * Raíz de la aplicación.
 *
 * Solo aloja el `router-outlet`: cada ruta decide su propio marco —el de la
 * aplicación con menú, o el de acceso sin él—, porque las pantallas de ingreso
 * no deben heredar una navegación que todavía no se puede usar.
 *
 * Se inyecta el tema aquí para que se aplique antes de pintar la primera
 * vista: hacerlo dentro de un componente de página provocaría un destello
 * claro al abrir la aplicación con el tema oscuro activo.
 */
@Component({
  selector: 'app-root',
  imports: [RouterModule],
  templateUrl: './app.component.html',
})
export class AppComponent {
  private readonly tema = inject(TemaService);
}
