import { Routes } from '@angular/router';
import { AppLayoutComponent } from './shared/layout/app-layout/app-layout.component';
import { EcommerceComponent } from './pages/dashboard/ecommerce/ecommerce.component';
import { ProfileComponent } from './pages/profile/profile.component';
import { EnConstruccionComponent } from './pages/en-construccion/en-construccion.component';
import { NotFoundComponent } from './pages/other-page/not-found/not-found.component';
import { SignInComponent } from './pages/auth-pages/sign-in/sign-in.component';
import { KitOndexiaComponent } from './pages/kit-ondexia/kit-ondexia.component';

// Páginas de demostración de la plantilla. Se conservan bajo /kit como
// referencia visual mientras se construyen las vistas reales, y se eliminan
// antes de publicar.
import { FormElementsComponent } from './pages/forms/form-elements/form-elements.component';
import { BasicTablesComponent } from './pages/tables/basic-tables/basic-tables.component';
import { BlankComponent } from './pages/blank/blank.component';
import { InvoicesComponent } from './pages/invoices/invoices.component';
import { LineChartComponent } from './pages/charts/line-chart/line-chart.component';
import { BarChartComponent } from './pages/charts/bar-chart/bar-chart.component';
import { AlertsComponent } from './pages/ui-elements/alerts/alerts.component';
import { AvatarElementComponent } from './pages/ui-elements/avatar-element/avatar-element.component';
import { BadgesComponent } from './pages/ui-elements/badges/badges.component';
import { ButtonsComponent } from './pages/ui-elements/buttons/buttons.component';
import { ImagesComponent } from './pages/ui-elements/images/images.component';
import { VideosComponent } from './pages/ui-elements/videos/videos.component';
import { CalenderComponent } from './pages/calender/calender.component';

const TITULO = 'Ondexia';

/**
 * Construye una ruta pendiente que apunta al marcador de posición.
 * Todas las vistas del menú son navegables desde la Etapa 0 aunque su
 * pantalla real todavía no exista.
 */
function pendiente(path: string, titulo: string, modulo: string, etapa: string) {
  return {
    path,
    component: EnConstruccionComponent,
    title: `${titulo} | ${TITULO}`,
    data: { titulo, modulo, etapa },
  };
}

export const routes: Routes = [
  {
    path: '',
    component: AppLayoutComponent,
    children: [
      {
        path: '',
        component: EcommerceComponent,
        pathMatch: 'full',
        title: `Panel principal | ${TITULO}`,
      },
      {
        path: 'perfil',
        component: ProfileComponent,
        title: `Mi perfil | ${TITULO}`,
      },

      // ── Almacén ──────────────────────────────────────────────────────────
      pendiente('almacen/productos', 'Productos', 'Almacén', 'Etapa 2'),
      pendiente('almacen/productos/:id', 'Ficha de producto', 'Almacén', 'Etapa 2'),
      pendiente('almacen/presentaciones', 'Presentaciones', 'Almacén', 'Etapa 2'),
      pendiente('almacen/por-agotarse', 'Productos por agotarse', 'Almacén', 'Etapa 6'),
      pendiente('almacen/guias-remision', 'Guías de remisión', 'Almacén', 'Etapa 6'),
      pendiente('almacen/guias-remision/nueva', 'Emitir guía de remisión', 'Almacén', 'Etapa 6'),
      pendiente('almacen/guias-ingreso', 'Guías de ingreso', 'Almacén', 'Etapa 6'),
      pendiente('almacen/tipos-precio', 'Tipos de precio', 'Almacén', 'Etapa 2'),
      pendiente('almacen/marcas', 'Marcas', 'Almacén', 'Etapa 2'),
      pendiente('almacen/modelos', 'Modelos', 'Almacén', 'Etapa 2'),
      pendiente('almacen/unidades', 'Unidades de medida', 'Almacén', 'Etapa 2'),
      pendiente('almacen/almacenes', 'Almacenes', 'Almacén', 'Etapa 1'),

      // ── Compras ──────────────────────────────────────────────────────────
      pendiente('compras/facturas', 'Facturas de compra', 'Compras', 'Etapa 5'),
      pendiente('compras/notas-pedido', 'Notas de pedido', 'Compras', 'Etapa 5'),
      pendiente('compras/liquidaciones', 'Liquidaciones de compra', 'Compras', 'Etapa 5'),
      pendiente('compras/notas-compra', 'Notas de compra', 'Compras', 'Etapa 5'),
      pendiente('compras/ordenes-compra', 'Órdenes de compra', 'Compras', 'Etapa 5'),
      pendiente('compras/ordenes-compra/nueva', 'Nueva orden de compra', 'Compras', 'Etapa 5'),
      pendiente('compras/ordenes-servicio', 'Órdenes de servicio', 'Compras', 'Etapa 5'),
      pendiente('compras/proveedores', 'Proveedores', 'Compras', 'Etapa 3'),
      pendiente('compras/proveedores/:id', 'Ficha de proveedor', 'Compras', 'Etapa 3'),

      // ── Ventas ───────────────────────────────────────────────────────────
      pendiente('ventas/clientes', 'Clientes', 'Ventas', 'Etapa 3'),
      pendiente('ventas/clientes/:id', 'Ficha de cliente', 'Ventas', 'Etapa 3'),
      pendiente('ventas/cotizaciones', 'Cotizaciones', 'Ventas', 'Etapa 4'),
      pendiente('ventas/cotizaciones/nueva', 'Nueva cotización', 'Ventas', 'Etapa 4'),
      pendiente('ventas/facturas', 'Facturas', 'Ventas', 'Etapa 4'),
      pendiente('ventas/facturas/nueva', 'Emitir factura', 'Ventas', 'Etapa 4'),
      pendiente('ventas/boletas', 'Boletas', 'Ventas', 'Etapa 4'),
      pendiente('ventas/boletas/nueva', 'Emitir boleta', 'Ventas', 'Etapa 4'),
      pendiente('ventas/notas-credito', 'Notas de crédito', 'Ventas', 'Etapa 4'),
      pendiente('ventas/preventas', 'Notas de preventa', 'Ventas', 'Etapa 4'),
      pendiente('ventas/comunicacion-baja', 'Comunicación de baja', 'Ventas', 'Etapa 4'),
      pendiente('ventas/resumen-diario', 'Resumen diario', 'Ventas', 'Etapa 4'),
      pendiente('ventas/formas-pago', 'Formas de pago', 'Ventas', 'Etapa 4'),
      pendiente('ventas/comprobantes/:id', 'Detalle de comprobante', 'Ventas', 'Etapa 4'),

      // ── Configuración ────────────────────────────────────────────────────
      pendiente('configuracion/empresa', 'Datos de la empresa', 'Configuración', 'Etapa 1'),
      pendiente('configuracion/identidad', 'Identidad visual', 'Configuración', 'Etapa 1'),
      pendiente('configuracion/establecimientos', 'Establecimientos', 'Configuración', 'Etapa 1'),
      pendiente('configuracion/series', 'Series y correlativos', 'Configuración', 'Etapa 1'),
      pendiente('configuracion/usuarios', 'Usuarios', 'Configuración', 'Etapa 1'),
      pendiente('configuracion/roles', 'Roles y permisos', 'Configuración', 'Etapa 1'),
      pendiente('configuracion/comprobantes', 'Configuración de comprobantes', 'Configuración', 'Etapa 1'),
      pendiente('configuracion/suscripcion', 'Suscripción y consumo', 'Configuración', 'Etapa 1'),

      // ── Estados del sistema ──────────────────────────────────────────────
      pendiente('sin-permisos', 'Sin permisos', 'Sistema', 'Etapa 1'),

      // ── Kit de la plantilla (referencia interna, se elimina al publicar) ──
      { path: 'kit/componentes', component: KitOndexiaComponent, title: `Kit · Componentes de Ondexia | ${TITULO}` },
      { path: 'kit/calendario', component: CalenderComponent, title: `Kit · Calendario | ${TITULO}` },
      { path: 'kit/formularios', component: FormElementsComponent, title: `Kit · Formularios | ${TITULO}` },
      { path: 'kit/tablas', component: BasicTablesComponent, title: `Kit · Tablas | ${TITULO}` },
      { path: 'kit/pagina-vacia', component: BlankComponent, title: `Kit · Página vacía | ${TITULO}` },
      { path: 'kit/comprobante', component: InvoicesComponent, title: `Kit · Comprobante | ${TITULO}` },
      { path: 'kit/grafico-lineas', component: LineChartComponent, title: `Kit · Gráfico de líneas | ${TITULO}` },
      { path: 'kit/grafico-barras', component: BarChartComponent, title: `Kit · Gráfico de barras | ${TITULO}` },
      { path: 'kit/alertas', component: AlertsComponent, title: `Kit · Alertas | ${TITULO}` },
      { path: 'kit/avatares', component: AvatarElementComponent, title: `Kit · Avatares | ${TITULO}` },
      { path: 'kit/insignias', component: BadgesComponent, title: `Kit · Insignias | ${TITULO}` },
      { path: 'kit/botones', component: ButtonsComponent, title: `Kit · Botones | ${TITULO}` },
      { path: 'kit/imagenes', component: ImagesComponent, title: `Kit · Imágenes | ${TITULO}` },
      { path: 'kit/videos', component: VideosComponent, title: `Kit · Videos | ${TITULO}` },
    ],
  },

  // ── Acceso (fuera del layout de la aplicación) ──────────────────────────
  {
    path: 'acceso/ingresar',
    component: SignInComponent,
    title: `Iniciar sesión | ${TITULO}`,
  },
  {
    path: 'acceso/recuperar',
    component: SignInComponent,
    title: `Recuperar contraseña | ${TITULO}`,
  },

  {
    path: '**',
    component: NotFoundComponent,
    title: `Página no encontrada | ${TITULO}`,
  },
];
