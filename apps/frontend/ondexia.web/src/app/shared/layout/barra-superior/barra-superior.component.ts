import { Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MenuLateralService } from '../../services/menu-lateral.service';
import { TemaService } from '../../services/tema.service';
import { ContextoService } from '../../services/contexto.service';
import { SelectorContextoComponent } from '../../components/comunes/selector-contexto/selector-contexto.component';

/**
 * Barra superior.
 *
 * Reúne lo que no pertenece a ninguna vista concreta: plegar el menú, buscar,
 * el contexto de trabajo —empresa y establecimiento activos, que determinan la
 * serie del comprobante— y el menú del usuario.
 *
 * No hay campana de notificaciones. La tenía la plantilla con avisos
 * inventados, y un control que no notifica nada real enseña al usuario a
 * ignorarlo. Volverá cuando haya algo que avisar: un comprobante rechazado por
 * SUNAT, una suscripción por vencer.
 */
@Component({
  selector: 'app-barra-superior',
  imports: [RouterModule, SelectorContextoComponent],
  templateUrl: './barra-superior.component.html',
})
export class BarraSuperiorComponent {
  private readonly anfitrion = inject(ElementRef<HTMLElement>);

  readonly menu = inject(MenuLateralService);
  readonly tema = inject(TemaService);
  readonly contexto = inject(ContextoService);

  readonly menuUsuarioAbierto = signal(false);

  /**
   * Datos de ejemplo mientras no exista sesión real. Al conectar el backend se
   * reemplaza por lo que devuelva la autenticación y no cambia nada más.
   */
  readonly usuario = {
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

  /**
   * Iniciales en lugar de fotografía: en un sistema de gestión casi nadie sube
   * una, y el avatar genérico repetido en toda la cuenta no distingue a nadie.
   */
  get iniciales(): string {
    return (this.usuario.nombres.charAt(0) + this.usuario.apellidos.charAt(0)).toUpperCase();
  }

  alternarMenuUsuario(): void {
    this.menuUsuarioAbierto.update((abierto) => !abierto);
  }

  cerrarMenuUsuario(): void {
    this.menuUsuarioAbierto.set(false);
  }

  /** El botón de plegado alterna el cajón en móvil y el ancho en escritorio. */
  alternarMenu(): void {
    if (window.innerWidth < 1280) {
      this.menu.alternarEnMovil();
    } else {
      this.menu.alternarDesplegado();
    }
  }

  @HostListener('document:pointerdown', ['$event'])
  alPulsarFuera(evento: PointerEvent): void {
    if (
      this.menuUsuarioAbierto() &&
      !this.anfitrion.nativeElement.contains(evento.target as Node)
    ) {
      this.cerrarMenuUsuario();
    }
  }

  @HostListener('document:keydown.escape')
  alPulsarEscape(): void {
    this.cerrarMenuUsuario();
  }
}
