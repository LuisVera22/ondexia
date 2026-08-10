import { Component, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { TemaService } from '../../services/tema.service';

/**
 * Marco de las pantallas de acceso: ingresar, recuperar contraseña, sin permisos.
 *
 * Dos columnas en escritorio —el formulario a la izquierda, la marca a la
 * derecha— y una sola en móvil, donde la columna de marca se oculta: en una
 * pantalla de teléfono lo único que importa es el campo de usuario.
 *
 * No lleva menú ni barra superior a propósito: quien no ha entrado no tiene
 * dónde navegar, y ofrecerle enlaces que llevan a una pantalla de acceso es
 * hacerle dar vueltas.
 */
@Component({
  selector: 'app-marco-acceso',
  imports: [RouterModule],
  templateUrl: './marco-acceso.component.html',
})
export class MarcoAccesoComponent {
  readonly tema = inject(TemaService);
}
