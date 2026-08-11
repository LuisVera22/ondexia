import { Component, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MenuLateralComponent } from '../menu-lateral/menu-lateral.component';
import { BarraSuperiorComponent } from '../barra-superior/barra-superior.component';
import { MenuLateralService } from '../../services/menu-lateral.service';

/**
 * Marco de la aplicación: menú lateral, barra superior y la vista activa.
 *
 * El contenido se desplaza a la derecha con margen en lugar de con una rejilla
 * porque el menú es `fixed` —debe quedarse quieto mientras la tabla de un
 * comprobante se desplaza— y un elemento fijo no ocupa espacio en el flujo.
 */
@Component({
  selector: 'app-marco-app',
  imports: [RouterModule, MenuLateralComponent, BarraSuperiorComponent],
  templateUrl: './marco-app.component.html',
})
export class MarcoAppComponent {
  readonly menu = inject(MenuLateralService);
}
