import { CommonModule } from '@angular/common';
import { Component, ElementRef, QueryList, ViewChildren, ChangeDetectorRef } from '@angular/core';
import { SidebarService } from '../../services/sidebar.service';
import { NavigationEnd, Router, RouterModule } from '@angular/router';
import { SafeHtmlPipe } from '../../pipe/safe-html.pipe';
import { combineLatest, Subscription } from 'rxjs';

type NavItem = {
  name: string;
  icon: string;
  path?: string;
  new?: boolean;
  subItems?: { name: string; path: string; pro?: boolean; new?: boolean }[];
};

/**
 * Iconos del menú. Trazo de 1.6 para que mantengan peso visual uniforme
 * entre ellos, en lugar de mezclar iconos rellenos y de trazo.
 */
const ICONO = {
  panel: `<svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg"><rect x="3" y="3" width="7.5" height="7.5" rx="2"/><rect x="13.5" y="3" width="7.5" height="7.5" rx="2"/><rect x="3" y="13.5" width="7.5" height="7.5" rx="2"/><rect x="13.5" y="13.5" width="7.5" height="7.5" rx="2"/></svg>`,
  almacen: `<svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg"><path d="M21 8.5v7a2 2 0 0 1-1.1 1.79l-7 3.5a2 2 0 0 1-1.8 0l-7-3.5A2 2 0 0 1 3 15.5v-7a2 2 0 0 1 1.1-1.79l7-3.5a2 2 0 0 1 1.8 0l7 3.5A2 2 0 0 1 21 8.5Z"/><path d="m3.3 7.5 8.7 4.35 8.7-4.35M12 21v-9.15"/></svg>`,
  compras: `<svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg"><path d="M2.5 3.5h1.6a1 1 0 0 1 .98.8L5.4 7m0 0 1.85 7.4a2 2 0 0 0 1.94 1.52h7.24a2 2 0 0 0 1.94-1.5L20.1 8.25A1 1 0 0 0 19.13 7H5.4Z"/><circle cx="9.5" cy="19.5" r="1.5"/><circle cx="17" cy="19.5" r="1.5"/></svg>`,
  ventas: `<svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg"><path d="M6 2.5h12a1 1 0 0 1 1 1v18l-2.6-1.7-2.6 1.7-2.6-1.7-2.6 1.7L5 21.5v-18a1 1 0 0 1 1-1Z"/><path d="M8.5 8h7M8.5 12h7M8.5 16h4"/></svg>`,
  configuracion: `<svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="3"/><path d="M19.14 12.94a1.5 1.5 0 0 1 0-1.88l1.2-1.5-1.74-3-1.83.62a1.5 1.5 0 0 1-1.63-.94L14.5 4.4h-5l-.64 1.84a1.5 1.5 0 0 1-1.63.94l-1.83-.62-1.74 3 1.2 1.5a1.5 1.5 0 0 1 0 1.88l-1.2 1.5 1.74 3 1.83-.62a1.5 1.5 0 0 1 1.63.94l.64 1.84h5l.64-1.84a1.5 1.5 0 0 1 1.63-.94l1.83.62 1.74-3Z"/></svg>`,
  kit: `<svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg"><rect x="3" y="3" width="18" height="18" rx="3"/><path d="M3 15.5 8 10.5l4.5 4.5M13.5 13l2.5-2.5 5 5"/><circle cx="15.5" cy="7.5" r="1.3"/></svg>`,
};

@Component({
  selector: 'app-sidebar',
  imports: [
    CommonModule,
    RouterModule,
    SafeHtmlPipe
  ],
  templateUrl: './app-sidebar.component.html',
})
export class AppSidebarComponent {

  // Módulos operativos
  navItems: NavItem[] = [
    {
      icon: ICONO.panel,
      name: "Panel",
      path: "/",
    },
    {
      icon: ICONO.almacen,
      name: "Almacén",
      subItems: [
        { name: "Productos", path: "/almacen/productos" },
        { name: "Presentaciones", path: "/almacen/presentaciones" },
        { name: "Productos por agotarse", path: "/almacen/por-agotarse" },
        { name: "Guías de remisión", path: "/almacen/guias-remision" },
        { name: "Guías de ingreso", path: "/almacen/guias-ingreso" },
        { name: "Tipos de precio", path: "/almacen/tipos-precio" },
        { name: "Marcas", path: "/almacen/marcas" },
        { name: "Modelos", path: "/almacen/modelos" },
        { name: "Unidades", path: "/almacen/unidades" },
        { name: "Almacenes", path: "/almacen/almacenes" },
      ],
    },
    {
      icon: ICONO.compras,
      name: "Compras",
      subItems: [
        { name: "Facturas", path: "/compras/facturas" },
        { name: "Notas de pedido", path: "/compras/notas-pedido" },
        { name: "Liquidación de compra", path: "/compras/liquidaciones" },
        { name: "Notas de compra", path: "/compras/notas-compra" },
        { name: "Órdenes de compra", path: "/compras/ordenes-compra" },
        { name: "Órdenes de servicio", path: "/compras/ordenes-servicio" },
        { name: "Proveedores", path: "/compras/proveedores" },
      ],
    },
    {
      icon: ICONO.ventas,
      name: "Ventas",
      subItems: [
        { name: "Clientes", path: "/ventas/clientes" },
        { name: "Cotizaciones", path: "/ventas/cotizaciones" },
        { name: "Facturas", path: "/ventas/facturas" },
        { name: "Boletas", path: "/ventas/boletas" },
        { name: "Notas de crédito", path: "/ventas/notas-credito" },
        { name: "Notas de preventa", path: "/ventas/preventas" },
        { name: "Comunicación de baja", path: "/ventas/comunicacion-baja" },
        { name: "Resumen diario", path: "/ventas/resumen-diario" },
        { name: "Formas de pago", path: "/ventas/formas-pago" },
      ],
    },
  ];

  // Configuración y referencias internas
  othersItems: NavItem[] = [
    {
      icon: ICONO.configuracion,
      name: "Configuración",
      subItems: [
        { name: "Empresa", path: "/configuracion/empresa" },
        { name: "Identidad visual", path: "/configuracion/identidad" },
        { name: "Establecimientos", path: "/configuracion/establecimientos" },
        { name: "Series y correlativos", path: "/configuracion/series" },
        { name: "Usuarios", path: "/configuracion/usuarios" },
        { name: "Roles y permisos", path: "/configuracion/roles" },
        { name: "Comprobantes", path: "/configuracion/comprobantes" },
        { name: "Suscripción", path: "/configuracion/suscripcion" },
      ],
    },
    {
      icon: ICONO.kit,
      name: "Kit de plantilla",
      subItems: [
        { name: "Componentes de Ondexia", path: "/kit/componentes" },
        { name: "Panel de la plantilla", path: "/kit/panel-plantilla" },
        { name: "Formularios", path: "/kit/formularios" },
        { name: "Tablas", path: "/kit/tablas" },
        { name: "Alertas", path: "/kit/alertas" },
        { name: "Botones", path: "/kit/botones" },
        { name: "Insignias", path: "/kit/insignias" },
        { name: "Avatares", path: "/kit/avatares" },
        { name: "Gráfico de líneas", path: "/kit/grafico-lineas" },
        { name: "Gráfico de barras", path: "/kit/grafico-barras" },
        { name: "Calendario", path: "/kit/calendario" },
        { name: "Comprobante", path: "/kit/comprobante" },
      ],
    },
  ];

  openSubmenu: string | null | number = null;
  subMenuHeights: { [key: string]: number } = {};
  @ViewChildren('subMenu') subMenuRefs!: QueryList<ElementRef>;

  readonly isExpanded$;
  readonly isMobileOpen$;
  readonly isHovered$;

  private subscription: Subscription = new Subscription();

  constructor(
    public sidebarService: SidebarService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {
    this.isExpanded$ = this.sidebarService.isExpanded$;
    this.isMobileOpen$ = this.sidebarService.isMobileOpen$;
    this.isHovered$ = this.sidebarService.isHovered$;
  }

  ngOnInit() {
    // Subscribe to router events
    this.subscription.add(
      this.router.events.subscribe(event => {
        if (event instanceof NavigationEnd) {
          this.setActiveMenuFromRoute(this.router.url);
        }
      })
    );

    // Subscribe to combined observables to close submenus when all are false
    this.subscription.add(
      combineLatest([this.isExpanded$, this.isMobileOpen$, this.isHovered$]).subscribe(
        ([isExpanded, isMobileOpen, isHovered]) => {
          if (!isExpanded && !isMobileOpen && !isHovered) {
            // this.openSubmenu = null;
            // this.savedSubMenuHeights = { ...this.subMenuHeights };
            // this.subMenuHeights = {};
            this.cdr.detectChanges();
          } else {
            // Restore saved heights when reopening
            // this.subMenuHeights = { ...this.savedSubMenuHeights };
            // this.cdr.detectChanges();
          }
        }
      )
    );

    // Initial load
    this.setActiveMenuFromRoute(this.router.url);
  }

  ngOnDestroy() {
    // Clean up subscriptions
    this.subscription.unsubscribe();
  }

  isActive(path: string): boolean {
    if (path === '/') {
      return this.router.url === '/';
    }
    return this.router.url === path || this.router.url.startsWith(path + '/');
  }

  toggleSubmenu(section: string, index: number) {
    const key = `${section}-${index}`;

    if (this.openSubmenu === key) {
      this.openSubmenu = null;
      this.subMenuHeights[key] = 0;
    } else {
      this.openSubmenu = key;

      setTimeout(() => {
        const el = document.getElementById(key);
        if (el) {
          this.subMenuHeights[key] = el.scrollHeight;
          this.cdr.detectChanges(); // Ensure UI updates
        }
      });
    }
  }

  onSidebarMouseEnter() {
    this.isExpanded$.subscribe(expanded => {
      if (!expanded) {
        this.sidebarService.setHovered(true);
      }
    }).unsubscribe();
  }

  private setActiveMenuFromRoute(currentUrl: string) {
    const menuGroups = [
      { items: this.navItems, prefix: 'main' },
      { items: this.othersItems, prefix: 'others' },
    ];

    menuGroups.forEach(group => {
      group.items.forEach((nav, i) => {
        if (nav.subItems) {
          nav.subItems.forEach(subItem => {
            if (currentUrl === subItem.path || currentUrl.startsWith(subItem.path + '/')) {
              const key = `${group.prefix}-${i}`;
              this.openSubmenu = key;

              setTimeout(() => {
                const el = document.getElementById(key);
                if (el) {
                  this.subMenuHeights[key] = el.scrollHeight;
                  this.cdr.detectChanges(); // Ensure UI updates
                }
              });
            }
          });
        }
      });
    });
  }

  onSubmenuClick() {
    this.isMobileOpen$.subscribe(isMobile => {
      if (isMobile) {
        this.sidebarService.setMobileOpen(false);
      }
    }).unsubscribe();
  }  

  
}
