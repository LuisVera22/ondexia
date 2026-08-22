import { Component, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MenuLateralComponent } from '../menu-lateral/menu-lateral.component';
import { AvisoSuscripcionComponent } from '../../components/comunes/aviso-suscripcion/aviso-suscripcion.component';
import { AvisosComponent } from '../../components/comunes/avisos/avisos.component';
import { ConfirmacionGlobalComponent } from '../../components/comunes/confirmacion/confirmacion-global.component';
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
  imports: [
    AvisosComponent,
    RouterModule,
    MenuLateralComponent,
    BarraSuperiorComponent,
    AvisoSuscripcionComponent,
    ConfirmacionGlobalComponent,
  ],
  templateUrl: './marco-app.component.html',
})
export class MarcoAppComponent {
  readonly menu = inject(MenuLateralService);
}
