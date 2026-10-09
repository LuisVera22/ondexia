import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

/**
 * Vista 404.
 *
 * Ofrece salidas concretas en lugar de un «volver atrás»: quien llega aquí lo
 * hace por un enlace roto o una dirección mal escrita, y el botón del navegador
 * lo devolvería al mismo enlace roto.
 */
@Component({
  selector: 'app-no-encontrado',
  imports: [RouterModule],
  templateUrl: './no-encontrado.component.html',
})
export class NoEncontradoComponent {}
