import { Routes } from '@angular/router';
import { AppLayoutComponent } from './shared/layout/app-layout/app-layout.component';
import { EcommerceComponent } from './pages/dashboard/ecommerce/ecommerce.component';
import { ProfileComponent } from './pages/profile/profile.component';
import { EnConstruccionComponent } from './pages/en-construccion/en-construccion.component';
import { NotFoundComponent } from './pages/other-page/not-found/not-found.component';
import { IngresarComponent } from './pages/acceso/ingresar/ingresar.component';
import { RecuperarComponent } from './pages/acceso/recuperar/recuperar.component';
import { SinPermisosComponent } from './pages/acceso/sin-permisos/sin-permisos.component';
import { KitOndexiaComponent } from './pages/kit-ondexia/kit-ondexia.component';
import { EmpresaComponent } from './pages/configuracion/empresa/empresa.component';
import { IdentidadComponent } from './pages/configuracion/identidad/identidad.component';
import { EstablecimientosComponent } from './pages/configuracion/establecimientos/establecimientos.component';
import { AlmacenesComponent } from './pages/configuracion/almacenes/almacenes.component';
import { SeriesComponent } from './pages/configuracion/series/series.component';
import { UsuariosComponent } from './pages/configuracion/usuarios/usuarios.component';
import { RolesComponent } from './pages/configuracion/roles/roles.component';
import { ComprobantesComponent } from './pages/configuracion/comprobantes/comprobantes.component';
import { SuscripcionComponent } from './pages/configuracion/suscripcion/suscripcion.component';
import { UnidadesComponent } from './pages/almacen/unidades/unidades.component';
import { MarcasComponent } from './pages/almacen/marcas/marcas.component';
import { ModelosComponent } from './pages/almacen/modelos/modelos.component';
import { TiposPrecioComponent } from './pages/almacen/tipos-precio/tipos-precio.component';
import { PresentacionesComponent } from './pages/almacen/presentaciones/presentaciones.component';
import { ProductosComponent } from './pages/almacen/productos/productos.component';
import { FichaProductoComponent } from './pages/almacen/ficha-producto/ficha-producto.component';
import { ClientesComponent } from './pages/ventas/clientes/clientes.component';
import { FichaClienteComponent } from './pages/ventas/clientes/ficha-cliente.component';
import { ProveedoresComponent } from './pages/compras/proveedores/proveedores.component';
import { FichaProveedorComponent } from './pages/compras/proveedores/ficha-proveedor.component';

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
      { path: 'almacen/productos', component: ProductosComponent, title: `Productos | ${TITULO}` },
      { path: 'almacen/productos/:id', component: FichaProductoComponent, title: `Ficha de producto | ${TITULO}` },
      { path: 'almacen/presentaciones', component: PresentacionesComponent, title: `Presentaciones | ${TITULO}` },
      pendiente('almacen/por-agotarse', 'Productos por agotarse', 'Almacén', 'Etapa 6'),
      pendiente('almacen/guias-remision', 'Guías de remisión', 'Almacén', 'Etapa 6'),
      pendiente('almacen/guias-remision/nueva', 'Emitir guía de remisión', 'Almacén', 'Etapa 6'),
      pendiente('almacen/guias-ingreso', 'Guías de ingreso', 'Almacén', 'Etapa 6'),
      { path: 'almacen/tipos-precio', component: TiposPrecioComponent, title: `Tipos de precio | ${TITULO}` },
      { path: 'almacen/marcas', component: MarcasComponent, title: `Marcas | ${TITULO}` },
      { path: 'almacen/modelos', component: ModelosComponent, title: `Modelos | ${TITULO}` },
      { path: 'almacen/unidades', component: UnidadesComponent, title: `Unidades de medida | ${TITULO}` },
      { path: 'almacen/almacenes', component: AlmacenesComponent, title: `Almacenes | ${TITULO}` },

      // ── Compras ──────────────────────────────────────────────────────────
      pendiente('compras/facturas', 'Facturas de compra', 'Compras', 'Etapa 5'),
      pendiente('compras/notas-pedido', 'Notas de pedido', 'Compras', 'Etapa 5'),
      pendiente('compras/liquidaciones', 'Liquidaciones de compra', 'Compras', 'Etapa 5'),
      pendiente('compras/notas-compra', 'Notas de compra', 'Compras', 'Etapa 5'),
      pendiente('compras/ordenes-compra', 'Órdenes de compra', 'Compras', 'Etapa 5'),
      pendiente('compras/ordenes-compra/nueva', 'Nueva orden de compra', 'Compras', 'Etapa 5'),
      pendiente('compras/ordenes-servicio', 'Órdenes de servicio', 'Compras', 'Etapa 5'),
      { path: 'compras/proveedores', component: ProveedoresComponent, title: `Proveedores | ${TITULO}` },
      { path: 'compras/proveedores/:id', component: FichaProveedorComponent, title: `Ficha de proveedor | ${TITULO}` },

      // ── Ventas ───────────────────────────────────────────────────────────
      { path: 'ventas/clientes', component: ClientesComponent, title: `Clientes | ${TITULO}` },
      { path: 'ventas/clientes/:id', component: FichaClienteComponent, title: `Ficha de cliente | ${TITULO}` },
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
      { path: 'configuracion/empresa', component: EmpresaComponent, title: `Datos de la empresa | ${TITULO}` },
      { path: 'configuracion/identidad', component: IdentidadComponent, title: `Identidad visual | ${TITULO}` },
      { path: 'configuracion/establecimientos', component: EstablecimientosComponent, title: `Establecimientos | ${TITULO}` },
      { path: 'configuracion/series', component: SeriesComponent, title: `Series y correlativos | ${TITULO}` },
      { path: 'configuracion/usuarios', component: UsuariosComponent, title: `Usuarios | ${TITULO}` },
      { path: 'configuracion/roles', component: RolesComponent, title: `Roles y permisos | ${TITULO}` },
      { path: 'configuracion/comprobantes', component: ComprobantesComponent, title: `Configuración de comprobantes | ${TITULO}` },
      { path: 'configuracion/suscripcion', component: SuscripcionComponent, title: `Suscripción y consumo | ${TITULO}` },

      // ── Estados del sistema ──────────────────────────────────────────────
      {
        path: 'sin-permisos',
        component: SinPermisosComponent,
        title: `Sin permisos | ${TITULO}`,
      },

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
    component: IngresarComponent,
    title: `Iniciar sesión | ${TITULO}`,
  },
  {
    path: 'acceso/recuperar',
    component: RecuperarComponent,
    title: `Recuperar contraseña | ${TITULO}`,
  },

  {
    path: '**',
    component: NotFoundComponent,
    title: `Página no encontrada | ${TITULO}`,
  },
];
