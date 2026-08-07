import { Component } from '@angular/core';
import { DropdownComponent } from '../../ui/dropdown/dropdown.component';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { DropdownItemTwoComponent } from '../../ui/dropdown/dropdown-item/dropdown-item.component-two';

/**
 * Menú del usuario en el encabezado.
 *
 * Los datos son de ejemplo mientras no exista sesión real: al conectar el
 * backend se reemplaza `usuario` por lo que devuelva la autenticación, y
 * ninguna otra parte de la plantilla cambia.
 *
 * Se muestran las iniciales en lugar de una fotografía porque en un ERP la
 * mayoría de usuarios nunca sube una, y un avatar genérico repetido en toda
 * la cuenta no distingue a nadie.
 */
@Component({
  selector: 'app-user-dropdown',
  templateUrl: './user-dropdown.component.html',
  imports: [CommonModule, RouterModule, DropdownComponent, DropdownItemTwoComponent],
})
export class UserDropdownComponent {
  isOpen = false;

  usuario = {
    nombres: 'Luis David',
    apellidos: 'Vera Vilchez',
    correo: 'luis.vera@wirbi.com',
  };

  get nombreCorto(): string {
    return `${this.usuario.nombres.split(' ')[0]} ${this.usuario.apellidos.split(' ')[0]}`;
  }

  get nombreCompleto(): string {
    return `${this.usuario.nombres} ${this.usuario.apellidos}`;
  }

  get iniciales(): string {
    return (this.usuario.nombres.charAt(0) + this.usuario.apellidos.charAt(0)).toUpperCase();
  }

  toggleDropdown() {
    this.isOpen = !this.isOpen;
  }

  closeDropdown() {
    this.isOpen = false;
  }
}
