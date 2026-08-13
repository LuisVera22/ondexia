import { Component, inject, signal } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { MenuLateralService } from '../../services/menu-lateral.service';
import { HtmlSeguroPipe } from '../../pipe/html-seguro.pipe';

export interface EntradaMenu {
  nombre: string;
  icono: string;
  /** Ruta directa, para entradas sin submenú. */
  ruta?: string;
  submenu?: { nombre: string; ruta: string }[];
}

export interface GrupoMenu {
  titulo: string;
  entradas: EntradaMenu[];
}

/**
 * Iconos del menú, en trazo de 1.6 y con la misma caja de 24.
 *
 * Van como cadena y no como componentes porque son datos del menú, no piezas
 * reutilizables: mezclar iconos rellenos con iconos de trazo, o grosores
 * distintos, es lo que hace que una barra lateral se vea desordenada.
 */
const ICONO = {
  panel: `<svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg"><rect x="3" y="3" width="7.5" height="7.5" rx="2"/><rect x="13.5" y="3" width="7.5" height="7.5" rx="2"/><rect x="3" y="13.5" width="7.5" height="7.5" rx="2"/><rect x="13.5" y="13.5" width="7.5" height="7.5" rx="2"/></svg>`,
  almacen: `<svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg"><path d="M21 8.5v7a2 2 0 0 1-1.1 1.79l-7 3.5a2 2 0 0 1-1.8 0l-7-3.5A2 2 0 0 1 3 15.5v-7a2 2 0 0 1 1.1-1.79l7-3.5a2 2 0 0 1 1.8 0l7 3.5A2 2 0 0 1 21 8.5Z"/><path d="m3.3 7.5 8.7 4.35 8.7-4.35M12 21v-9.15"/></svg>`,
  compras: `<svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg"><path d="M2.5 3.5h1.6a1 1 0 0 1 .98.8L5.4 7m0 0 1.85 7.4a2 2 0 0 0 1.94 1.52h7.24a2 2 0 0 0 1.94-1.5L20.1 8.25A1 1 0 0 0 19.13 7H5.4Z"/><circle cx="9.5" cy="19.5" r="1.5"/><circle cx="17" cy="19.5" r="1.5"/></svg>`,
  ventas: `<svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg"><path d="M6 2.5h12a1 1 0 0 1 1 1v18l-2.6-1.7-2.6 1.7-2.6-1.7-2.6 1.7L5 21.5v-18a1 1 0 0 1 1-1Z"/><path d="M8.5 8h7M8.5 12h7M8.5 16h4"/></svg>`,
  configuracion: `<svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="3"/><path d="M19.14 12.94a1.5 1.5 0 0 1 0-1.88l1.2-1.5-1.74-3-1.83.62a1.5 1.5 0 0 1-1.63-.94L14.5 4.4h-5l-.64 1.84a1.5 1.5 0 0 1-1.63.94l-1.83-.62-1.74 3 1.2 1.5a1.5 1.5 0 0 1 0 1.88l-1.2 1.5 1.74 3 1.83-.62a1.5 1.5 0 0 1 1.63.94l.64 1.84h5l.64-1.84a1.5 1.5 0 0 1 1.63-.94l1.83.62 1.74-3Z"/></svg>`,
  componentes: `<svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg"><path d="M12 2.5l4 2.5-4 2.5-4-2.5 4-2.5Z"/><path d="M12 16.5l4 2.5-4 2.5-4-2.5 4-2.5Z"/><path d="M5 9.5l4 2.5-4 2.5-4-2.5 4-2.5Z" transform="translate(-0.5 0)"/><path d="M19 9.5l4 2.5-4 2.5-4-2.5 4-2.5Z" transform="translate(-0.5 0)"/></svg>`,
};

/**
 * Menú lateral.
 *
 * El submenú se anima con `grid-template-rows` de `0fr` a `1fr`, no midiendo
 * la altura del contenido con JavaScript. Es lo que permite que un submenú de
 * nueve entradas y otro de dos se abran igual de bien sin que nadie calcule
 * píxeles, y que añadir una entrada no requiera tocar nada más.
 *
 * Solo un submenú permanece abierto a la vez: con cuatro módulos de hasta diez
 * submódulos, permitir varios abiertos obliga a desplazarse para encontrar lo
 * que se busca.
 */
@Component({
  selector: 'app-menu-lateral',
  imports: [RouterModule, HtmlSeguroPipe],
  templateUrl: './menu-lateral.component.html',
})
export class MenuLateralComponent {
  readonly menu = inject(MenuLateralService);
  private readonly router = inject(Router);

  /** Nombre del grupo desplegado, o `null` si están todos cerrados. */
  readonly abierto = signal<string | null>(null);

  readonly grupos: GrupoMenu[] = [
    {
      titulo: 'Operación',
      entradas: [
        { icono: ICONO.panel, nombre: 'Panel', ruta: '/' },
        {
          icono: ICONO.almacen,
          nombre: 'Almacén',
          submenu: [
            { nombre: 'Productos', ruta: '/almacen/productos' },
            { nombre: 'Presentaciones', ruta: '/almacen/presentaciones' },
            { nombre: 'Productos por agotarse', ruta: '/almacen/por-agotarse' },
            { nombre: 'Guías de remisión', ruta: '/almacen/guias-remision' },
            { nombre: 'Guías de ingreso', ruta: '/almacen/guias-ingreso' },
            { nombre: 'Tipos de precio', ruta: '/almacen/tipos-precio' },
            { nombre: 'Marcas', ruta: '/almacen/marcas' },
            { nombre: 'Modelos', ruta: '/almacen/modelos' },
            { nombre: 'Unidades', ruta: '/almacen/unidades' },
            { nombre: 'Almacenes', ruta: '/almacen/almacenes' },
          ],
        },
        {
          icono: ICONO.compras,
          nombre: 'Compras',
          submenu: [
            { nombre: 'Facturas', ruta: '/compras/facturas' },
            { nombre: 'Notas de pedido', ruta: '/compras/notas-pedido' },
            { nombre: 'Liquidación de compra', ruta: '/compras/liquidaciones' },
            { nombre: 'Notas de compra', ruta: '/compras/notas-compra' },
            { nombre: 'Órdenes de compra', ruta: '/compras/ordenes-compra' },
            { nombre: 'Órdenes de servicio', ruta: '/compras/ordenes-servicio' },
            { nombre: 'Proveedores', ruta: '/compras/proveedores' },
          ],
        },
        {
          icono: ICONO.ventas,
          nombre: 'Ventas',
          submenu: [
            { nombre: 'Clientes', ruta: '/ventas/clientes' },
            { nombre: 'Cotizaciones', ruta: '/ventas/cotizaciones' },
            { nombre: 'Facturas', ruta: '/ventas/facturas' },
            { nombre: 'Boletas', ruta: '/ventas/boletas' },
            { nombre: 'Notas de crédito', ruta: '/ventas/notas-credito' },
            { nombre: 'Notas de preventa', ruta: '/ventas/preventas' },
            { nombre: 'Comunicación de baja', ruta: '/ventas/comunicacion-baja' },
            { nombre: 'Resumen diario', ruta: '/ventas/resumen-diario' },
            { nombre: 'Formas de pago', ruta: '/ventas/formas-pago' },
          ],
        },
      ],
    },
    {
      titulo: 'Sistema',
      entradas: [
        {
          icono: ICONO.configuracion,
          nombre: 'Configuración',
          submenu: [
            { nombre: 'Empresa', ruta: '/configuracion/empresa' },
            { nombre: 'Identidad visual', ruta: '/configuracion/identidad' },
            { nombre: 'Establecimientos', ruta: '/configuracion/establecimientos' },
            { nombre: 'Series y correlativos', ruta: '/configuracion/series' },
            { nombre: 'Usuarios', ruta: '/configuracion/usuarios' },
            { nombre: 'Roles y permisos', ruta: '/configuracion/roles' },
            { nombre: 'Comprobantes', ruta: '/configuracion/comprobantes' },
            { nombre: 'Suscripción', ruta: '/configuracion/suscripcion' },
          ],
        },
        { icono: ICONO.componentes, nombre: 'Componentes', ruta: '/componentes' },
      ],
    },
  ];

  /** Las etiquetas solo se leen si el menú está a su ancho completo. */
  get muestraTexto(): boolean {
    return this.menu.anchoCompleto() || this.menu.abiertoEnMovil();
  }

  alternar(nombre: string): void {
    this.abierto.update((actual) => (actual === nombre ? null : nombre));
  }

  estaAbierto(entrada: EntradaMenu): boolean {
    // Un grupo con la ruta activa dentro se muestra abierto aunque nadie lo
    // haya pulsado: al entrar por un enlace directo, el menú debe explicar
    // dónde estás.
    return this.abierto() === entrada.nombre || (this.abierto() === null && this.contieneRutaActiva(entrada));
  }

  /**
   * Ruta actual sin parámetros ni fragmento.
   *
   * Se compara a mano en lugar de usar `routerLinkActive` porque la ruta del
   * panel es `/`, y con coincidencia por prefijo `/` marcaría como activa
   * cualquier ruta de la aplicación.
   */
  private get rutaActual(): string {
    return this.router.url.split('?')[0].split('#')[0];
  }

  esActiva(ruta: string): boolean {
    const actual = this.rutaActual;
    if (ruta === '/') {
      return actual === '/';
    }
    return actual === ruta || actual.startsWith(ruta + '/');
  }

  contieneRutaActiva(entrada: EntradaMenu): boolean {
    if (entrada.ruta) {
      return this.esActiva(entrada.ruta);
    }
    return (entrada.submenu ?? []).some((sub) => this.esActiva(sub.ruta));
  }

  /** En móvil el menú se superpone: al navegar hay que retirarlo. */
  alNavegar(): void {
    this.menu.cerrarEnMovil();
  }
}
