import { NgTemplateOutlet } from '@angular/common';
import { Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MenuLateralService } from '../../services/menu-lateral.service';
import { TemaService } from '../../services/tema.service';
import { ContextoService } from '../../services/contexto.service';
import { SesionService } from '../../../nucleo/sesion.service';
import {
  OpcionContexto,
  SelectorContextoComponent,
} from '../../components/comunes/selector-contexto/selector-contexto.component';

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
 *
 * <h2>Por qué el `sticky` va en el host y no en el `<header>`</h2>
 *
 * <p>Un elemento pegajoso se desplaza dentro de su bloque contenedor, que es su
 * padre. Puesto en el `<header>`, ese padre es el host del componente, que mide
 * exactamente lo que mide el `<header>`: cero holgura donde desplazarse, así
 * que `sticky` se comportaba igual que `static`. Y lo hacía en silencio —la
 * propiedad se aplica, el navegador no avisa de nada— por lo que la barra
 * simplemente se iba con el desplazamiento.
 *
 * <p>En el host, el bloque contenedor pasa a ser `.marco-contenido`, que ocupa
 * la página entera. Ahí sí hay recorrido.
 */
@Component({
  selector: 'app-barra-superior',
  imports: [NgTemplateOutlet, RouterModule, SelectorContextoComponent],
  templateUrl: './barra-superior.component.html',
  host: { class: 'sticky top-0 z-30' },
})
export class BarraSuperiorComponent {
  private readonly anfitrion = inject(ElementRef<HTMLElement>);

  readonly menu = inject(MenuLateralService);
  readonly tema = inject(TemaService);
  readonly contexto = inject(ContextoService);

  readonly sesion = inject(SesionService);

  readonly menuUsuarioAbierto = signal(false);

  /**
   * El menu compacto del movil: contexto, tema y cuenta en uno.
   *
   * <p>Estado propio y no el mismo que el del menu de usuario: son dos
   * disparadores distintos y solo uno esta a la vista en cada ancho, pero
   * compartir la senal significaria que abrir uno deja el otro marcado como
   * expandido, y `aria-expanded` acabaria mintiendo en el que no se ve.
   */
  readonly menuCompactoAbierto = signal(false);

  /**
   * Cambiar de empresa recarga el contexto entero, permisos incluidos.
   *
   * Se envuelve aquí en vez de llamar al servicio desde la plantilla porque
   * devuelve una promesa: dejarla suelta en un enlace de evento esconde
   * cualquier fallo de la recarga, que es justo el que no conviene perder —el
   * usuario se quedaría viendo el menú de la empresa anterior.
   */
  async elegirEmpresa(empresa: OpcionContexto): Promise<void> {
    await this.contexto.cambiarEmpresa(empresa);
  }

  /**
   * El usuario sale del contexto que devuelve la API, no del token.
   *
   * Los dos traen el nombre, pero el del contexto es el que está en NUESTRA
   * base: si alguien corrige su nombre en el perfil, ahí se ve al instante,
   * mientras que el del token no cambia hasta la siguiente renovación.
   *
   * Se recurre al token solo mientras el contexto aún no ha llegado, para que
   * la barra no aparezca vacía durante la primera carga.
   */
  readonly nombreCompleto = computed(
    () => this.contexto.usuario()?.nombre ?? this.sesion.usuario()?.nombre ?? ''
  );

  readonly correo = computed(
    () => this.contexto.usuario()?.email ?? this.sesion.usuario()?.correo ?? ''
  );

  readonly nombreCorto = computed(() => {
    const partes = this.nombreCompleto().trim().split(/\s+/).filter(Boolean);
    // Nombre y primer apellido. Los nombres peruanos suelen traer dos
    // apellidos, y los cuatro juntos no caben en la barra.
    return partes.slice(0, 2).join(' ');
  });

  /**
   * Iniciales en lugar de fotografía: en un sistema de gestión casi nadie sube
   * una, y el avatar genérico repetido en toda la cuenta no distingue a nadie.
   */
  readonly iniciales = computed(() => {
    const partes = this.nombreCompleto().trim().split(/\s+/).filter(Boolean);
    if (partes.length === 0) {
      return '·';
    }
    const primera = partes[0].charAt(0);
    const segunda = partes.length > 1 ? partes[1].charAt(0) : '';
    return (primera + segunda).toUpperCase();
  });

  /** Cierra la sesión aquí y en Cognito. Sin lo segundo, «entrar» volvería a entrar solo. */
  cerrarSesion(): void {
    this.contexto.limpiar();
    this.sesion.cerrar();
  }

  alternarMenuUsuario(): void {
    this.menuUsuarioAbierto.update((abierto) => !abierto);
  }

  alternarMenuCompacto(): void {
    this.menuCompactoAbierto.update((abierto) => !abierto);
  }

  /**
   * Cierra los dos.
   *
   * <p>Lo llaman las entradas de la cuenta, que viven en una plantilla
   * compartida y por tanto no saben desde que menu se las esta pulsando.
   * Cerrar el que ya estaba cerrado no cuesta nada.
   */
  cerrarMenus(): void {
    this.menuUsuarioAbierto.set(false);
    this.menuCompactoAbierto.set(false);
  }

  /** El botón de plegado alterna el cajón en móvil y el ancho en escritorio. */
  alternarMenu(): void {
    if (window.innerWidth < 1280) {
      this.menu.alternarEnMovil();
    } else {
      this.menu.alternarDesplegado();
    }
  }

  async elegirEmpresaYCerrar(empresa: OpcionContexto): Promise<void> {
    this.cerrarMenus();
    await this.elegirEmpresa(empresa);
  }

  elegirEstablecimientoYCerrar(establecimiento: OpcionContexto): void {
    this.cerrarMenus();
    this.contexto.cambiarEstablecimiento(establecimiento);
  }

  @HostListener('document:pointerdown', ['$event'])
  alPulsarFuera(evento: PointerEvent): void {
    const hayAlgunoAbierto = this.menuUsuarioAbierto() || this.menuCompactoAbierto();
    if (hayAlgunoAbierto && !this.anfitrion.nativeElement.contains(evento.target as Node)) {
      this.cerrarMenus();
    }
  }

  @HostListener('document:keydown.escape')
  alPulsarEscape(): void {
    this.cerrarMenus();
  }
}
