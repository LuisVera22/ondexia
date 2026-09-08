import { Routes } from '@angular/router';
import { permisoGuard } from './nucleo/permiso.guard';
import { sesionGuard } from './nucleo/sesion.guard';
import { RegistroComponent } from './pages/acceso/registro/registro.component';
import { RetornoComponent } from './pages/acceso/retorno/retorno.component';
import { MarcoAppComponent } from './shared/layout/marco-app/marco-app.component';
import { PanelComponent } from './pages/panel/panel.component';
import { PerfilComponent } from './pages/perfil/perfil.component';
import { NoEncontradoComponent } from './pages/no-encontrado/no-encontrado.component';
import { IngresarComponent } from './pages/acceso/ingresar/ingresar.component';
import { RecuperarComponent } from './pages/acceso/recuperar/recuperar.component';
import { SinPermisosComponent } from './pages/acceso/sin-permisos/sin-permisos.component';
import { EmpresasComponent } from './pages/configuracion/empresas/empresas.component';
import { EmpresaComponent } from './pages/configuracion/empresa/empresa.component';
import { NuevaEmpresaComponent } from './pages/configuracion/empresas/nueva-empresa.component';
import { IdentidadComponent } from './pages/configuracion/identidad/identidad.component';
import { EstablecimientosComponent } from './pages/configuracion/establecimientos/establecimientos.component';
import { FichaEstablecimientoComponent } from './pages/configuracion/establecimientos/ficha-establecimiento.component';
import { AlmacenesComponent } from './pages/configuracion/almacenes/almacenes.component';
import { FichaAlmacenComponent } from './pages/configuracion/almacenes/ficha-almacen.component';
import { SeriesComponent } from './pages/configuracion/series/series.component';
import { UsuariosComponent } from './pages/configuracion/usuarios/usuarios.component';
import { FichaUsuarioComponent } from './pages/configuracion/usuarios/ficha-usuario.component';
import { RolesComponent } from './pages/configuracion/roles/roles.component';
import { ComprobantesComponent } from './pages/configuracion/comprobantes/comprobantes.component';
import { EmisionComponent } from './pages/configuracion/emision/emision.component';
import { SuscripcionComponent } from './pages/configuracion/suscripcion/suscripcion.component';
import { UnidadesComponent } from './pages/almacen/unidades/unidades.component';
import { MarcasComponent } from './pages/almacen/marcas/marcas.component';
import { ModelosComponent } from './pages/almacen/modelos/modelos.component';
import { TiposPrecioComponent } from './pages/almacen/tipos-precio/tipos-precio.component';
import { PresentacionesComponent } from './pages/almacen/presentaciones/presentaciones.component';
import { ProductosComponent } from './pages/almacen/productos/productos.component';
import { FichaProductoComponent } from './pages/almacen/ficha-producto/ficha-producto.component';
import { CajasComponent } from './pages/ventas/cajas/cajas.component';
import { PuntoDeVentaComponent } from './pages/ventas/punto-de-venta/punto-de-venta.component';
import { ListaDocumentosComponent } from './pages/ventas/documentos/lista-documentos.component';
import { DetalleDocumentoComponent } from './pages/ventas/documentos/detalle-documento.component';
import { ClientesComponent } from './pages/ventas/clientes/clientes.component';
import { FichaClienteComponent } from './pages/ventas/clientes/ficha-cliente.component';
import { ProveedoresComponent } from './pages/compras/proveedores/proveedores.component';
import { FichaProveedorComponent } from './pages/compras/proveedores/ficha-proveedor.component';
import { CotizacionesComponent } from './pages/ventas/cotizaciones/cotizaciones.component';
import { NuevaCotizacionComponent } from './pages/ventas/cotizaciones/nueva-cotizacion.component';
import { PreventasComponent } from './pages/ventas/preventas/preventas.component';
import { NuevaPreventaComponent } from './pages/ventas/preventas/nueva-preventa.component';
import { DetalleComprobanteComponent } from './pages/ventas/comprobantes/detalle-comprobante.component';
import { ComunicacionBajaComponent } from './pages/ventas/comunicacion-baja/comunicacion-baja.component';
import { ResumenDiarioComponent } from './pages/ventas/resumen-diario/resumen-diario.component';
import { FormasPagoComponent } from './pages/ventas/formas-pago/formas-pago.component';
import { GuiasIngresoComponent } from './pages/almacen/guias-ingreso/guias-ingreso.component';
import { NuevaGuiaIngresoComponent } from './pages/almacen/guias-ingreso/nueva-guia-ingreso.component';
import { GuiasRemisionComponent } from './pages/almacen/guias-remision/guias-remision.component';
import { EmitirGuiaRemisionComponent } from './pages/almacen/guias-remision/emitir-guia-remision.component';
import { PorAgotarseComponent } from './pages/almacen/por-agotarse/por-agotarse.component';
import { NotasPedidoComponent } from './pages/compras/notas-pedido/notas-pedido.component';
import { NuevaNotaPedidoComponent } from './pages/compras/notas-pedido/nueva-nota-pedido.component';
import { OrdenesCompraComponent } from './pages/compras/ordenes-compra/ordenes-compra.component';
import { NuevaOrdenCompraComponent } from './pages/compras/ordenes-compra/nueva-orden-compra.component';
import { OrdenesServicioComponent } from './pages/compras/ordenes-servicio/ordenes-servicio.component';
import { NuevaOrdenServicioComponent } from './pages/compras/ordenes-servicio/nueva-orden-servicio.component';
import { NotasCompraComponent } from './pages/compras/notas-compra/notas-compra.component';
import { NuevaNotaCompraComponent } from './pages/compras/notas-compra/nueva-nota-compra.component';
import { FacturasCompraComponent } from './pages/compras/facturas/facturas.component';
import { RegistrarFacturaCompraComponent } from './pages/compras/facturas/registrar-factura-compra.component';
import { LiquidacionesComponent } from './pages/compras/liquidaciones/liquidaciones.component';
import { EmitirLiquidacionComponent } from './pages/compras/liquidaciones/emitir-liquidacion.component';
import { salidaConCambios } from './shared/formularios/salida-con-cambios.guard';

// Páginas de demostración de la plantilla. Se conservan bajo /kit como
// referencia visual mientras se construyen las vistas reales, y se eliminan
// antes de publicar.

const TITULO = 'Ondexia';

export const routes: Routes = [
  {
    path: '',
    component: MarcoAppComponent,
    /*
     * Una sola guarda en el padre protege las 58 rutas hijas. Ponerla en cada
     * una sería la forma segura de que a la número 59 se le olvide.
     *
     * Es comodidad, no seguridad: quien edite el JavaScript en su navegador
     * puede saltarla y no verá nada, porque los datos los sirve la API y esa
     * valida el token en cada petición.
     */
    /*
     * Una sola guarda para todos los modulos, y no una anotacion por ruta.
     *
     * permisoGuard deduce el modulo del primer segmento de la URL, asi que
     * cubre las mas de cincuenta rutas de abajo y las que se anadan despues.
     * Ver permiso.guard.ts para por que se eligio asi.
     */
    canActivate: [sesionGuard, permisoGuard],

    /*
     * Sin esto la guarda se ejecuta UNA sola vez.
     *
     * Angular corre las guardas de las rutas que se activan. Al pasar de
     * almacen/productos a ventas/facturas el padre no se desactiva —es el mismo
     * marco— asi que su canActivate no se vuelve a evaluar y solo quedaria
     * protegida la primera pantalla de la sesion.
     *
     * El coste es una comprobacion en memoria por navegacion: asegurarCargado
     * vuelve enseguida cuando el contexto ya esta, y puede() mira una lista.
     */
    runGuardsAndResolvers: 'always',

    children: [
      {
        path: '',
        component: PanelComponent,
        pathMatch: 'full',
        title: `Panel principal | ${TITULO}`,
      },
      {
        path: 'perfil',
        component: PerfilComponent,
        title: `Mi perfil | ${TITULO}`,
      },

      // ── Almacén ──────────────────────────────────────────────────────────
      { path: 'almacen/productos', component: ProductosComponent, title: `Productos | ${TITULO}` },
      { path: 'almacen/productos/:id', component: FichaProductoComponent, title: `Ficha de producto | ${TITULO}`, canDeactivate: [salidaConCambios] },
      { path: 'almacen/presentaciones', component: PresentacionesComponent, title: `Presentaciones | ${TITULO}` },
      { path: 'almacen/por-agotarse', component: PorAgotarseComponent, title: `Productos por agotarse | ${TITULO}` },
      { path: 'almacen/guias-remision', component: GuiasRemisionComponent, title: `Guías de remisión | ${TITULO}` },
      { path: 'almacen/guias-remision/nueva', component: EmitirGuiaRemisionComponent, title: `Emitir guía de remisión | ${TITULO}` },
      { path: 'almacen/guias-ingreso', component: GuiasIngresoComponent, title: `Guías de ingreso | ${TITULO}` },
      { path: 'almacen/guias-ingreso/nueva', component: NuevaGuiaIngresoComponent, title: `Nueva guía de ingreso | ${TITULO}` },
      { path: 'almacen/tipos-precio', component: TiposPrecioComponent, title: `Tipos de precio | ${TITULO}` },
      { path: 'almacen/marcas', component: MarcasComponent, title: `Marcas | ${TITULO}` },
      { path: 'almacen/modelos', component: ModelosComponent, title: `Modelos | ${TITULO}` },
      { path: 'almacen/unidades', component: UnidadesComponent, title: `Unidades de medida | ${TITULO}` },
      { path: 'almacen/almacenes', component: AlmacenesComponent, title: `Almacenes | ${TITULO}` },
      { path: 'almacen/almacenes/:id', component: FichaAlmacenComponent, title: `Almacén | ${TITULO}`, canDeactivate: [salidaConCambios] },

      // ── Compras ──────────────────────────────────────────────────────────
      { path: 'compras/notas-pedido', component: NotasPedidoComponent, title: `Notas de pedido | ${TITULO}` },
      { path: 'compras/notas-pedido/nueva', component: NuevaNotaPedidoComponent, title: `Nueva nota de pedido | ${TITULO}` },
      { path: 'compras/ordenes-compra', component: OrdenesCompraComponent, title: `Órdenes de compra | ${TITULO}` },
      { path: 'compras/ordenes-compra/nueva', component: NuevaOrdenCompraComponent, title: `Nueva orden de compra | ${TITULO}` },
      { path: 'compras/ordenes-servicio', component: OrdenesServicioComponent, title: `Órdenes de servicio | ${TITULO}` },
      { path: 'compras/ordenes-servicio/nueva', component: NuevaOrdenServicioComponent, title: `Nueva orden de servicio | ${TITULO}` },
      { path: 'compras/notas-compra', component: NotasCompraComponent, title: `Notas de compra | ${TITULO}` },
      { path: 'compras/notas-compra/nueva', component: NuevaNotaCompraComponent, title: `Nueva nota de compra | ${TITULO}` },
      { path: 'compras/facturas', component: FacturasCompraComponent, title: `Facturas de compra | ${TITULO}` },
      { path: 'compras/facturas/nueva', component: RegistrarFacturaCompraComponent, title: `Registrar factura de compra | ${TITULO}` },
      { path: 'compras/liquidaciones', component: LiquidacionesComponent, title: `Liquidaciones de compra | ${TITULO}` },
      { path: 'compras/liquidaciones/nueva', component: EmitirLiquidacionComponent, title: `Emitir liquidación de compra | ${TITULO}` },
      { path: 'compras/proveedores', component: ProveedoresComponent, title: `Proveedores | ${TITULO}` },
      { path: 'compras/proveedores/:id', component: FichaProveedorComponent, title: `Ficha de proveedor | ${TITULO}` },

      // ── Ventas ───────────────────────────────────────────────────────────
      { path: 'ventas/cajas', component: CajasComponent, title: `Cajas | ${TITULO}` },
      { path: 'ventas/punto-de-venta', component: PuntoDeVentaComponent, title: `Punto de venta | ${TITULO}` },
      { path: 'ventas/notas-venta', component: ListaDocumentosComponent, data: { tipo: 'NV' }, title: `Notas de venta | ${TITULO}` },
      { path: 'ventas/documentos/:tipo/:id', component: DetalleDocumentoComponent, title: `Documento | ${TITULO}` },
      { path: 'ventas/clientes', component: ClientesComponent, title: `Clientes | ${TITULO}` },
      { path: 'ventas/clientes/:id', component: FichaClienteComponent, title: `Ficha de cliente | ${TITULO}`, canDeactivate: [salidaConCambios] },
      { path: 'ventas/cotizaciones', component: CotizacionesComponent, title: `Cotizaciones | ${TITULO}` },
      { path: 'ventas/cotizaciones/nueva', component: NuevaCotizacionComponent, title: `Nueva cotización | ${TITULO}` },
      // Facturas y boletas salen del punto de venta; las maquetas de emisión
      // (EmitirFacturaComponent, EmitirBoletaComponent) quedan sin ruta.
      { path: 'ventas/facturas', component: ListaDocumentosComponent, data: { tipo: 'FACTURA' }, title: `Facturas | ${TITULO}` },
      { path: 'ventas/notas-credito', component: ListaDocumentosComponent, data: { tipo: 'NOTA_CREDITO' }, title: `Notas de crédito | ${TITULO}` },
      { path: 'ventas/facturas/nueva', redirectTo: 'ventas/punto-de-venta' },
      { path: 'ventas/boletas', component: ListaDocumentosComponent, data: { tipo: 'BOLETA' }, title: `Boletas | ${TITULO}` },
      { path: 'ventas/boletas/nueva', redirectTo: 'ventas/punto-de-venta' },
      // La maqueta de «emitir nota de crédito» se retira con la iteración 6: una
      // nota nace de un comprobante concreto, así que se emite desde su ficha y
      // no desde un formulario en blanco. La ruta redirige al listado real.
      { path: 'ventas/notas-credito/nueva', redirectTo: 'ventas/notas-credito', pathMatch: 'full' },
      { path: 'ventas/preventas', component: PreventasComponent, title: `Notas de preventa | ${TITULO}` },
      { path: 'ventas/preventas/nueva', component: NuevaPreventaComponent, title: `Nueva nota de preventa | ${TITULO}` },
      { path: 'ventas/comunicacion-baja', component: ComunicacionBajaComponent, title: `Comunicación de baja | ${TITULO}` },
      { path: 'ventas/resumen-diario', component: ResumenDiarioComponent, title: `Resumen diario | ${TITULO}` },
      { path: 'ventas/formas-pago', component: FormasPagoComponent, title: `Formas de pago | ${TITULO}` },
      { path: 'ventas/comprobantes/:id', component: DetalleComprobanteComponent, title: `Detalle de comprobante | ${TITULO}` },

      // ── Configuración ────────────────────────────────────────────────────
      // La ruta en singular era el formulario de la empresa activa. Se mantiene
      // como redirección porque está enlazada desde el menú de usuario de
      // cualquier pestaña abierta y en los marcadores de quien ya la usaba.
      { path: 'configuracion/empresa', redirectTo: 'configuracion/empresas', pathMatch: 'full' },
      { path: 'configuracion/empresas', component: EmpresasComponent, title: `Empresas | ${TITULO}` },
      // ANTES de ':id', o el parametro se comeria la palabra «nueva» y esta
      // pantalla abriria la ficha de una empresa con ese identificador.
      { path: 'configuracion/empresas/nueva', component: NuevaEmpresaComponent, title: `Registrar empresa | ${TITULO}` },
      { path: 'configuracion/empresas/:id', component: EmpresaComponent, title: `Datos de la empresa | ${TITULO}`, canDeactivate: [salidaConCambios] },
      { path: 'configuracion/identidad', component: IdentidadComponent, title: `Identidad visual | ${TITULO}` },
      { path: 'configuracion/establecimientos', component: EstablecimientosComponent, title: `Establecimientos | ${TITULO}` },
      { path: 'configuracion/establecimientos/:id', component: FichaEstablecimientoComponent, title: `Establecimiento | ${TITULO}`, canDeactivate: [salidaConCambios] },
      { path: 'configuracion/series', component: SeriesComponent, title: `Series y correlativos | ${TITULO}` },
      { path: 'configuracion/usuarios', component: UsuariosComponent, title: `Usuarios | ${TITULO}` },
      { path: 'configuracion/usuarios/:id', component: FichaUsuarioComponent, title: `Acceso de usuario | ${TITULO}`, canDeactivate: [salidaConCambios] },
      { path: 'configuracion/roles', component: RolesComponent, title: `Roles y permisos | ${TITULO}` },
      { path: 'configuracion/comprobantes', component: ComprobantesComponent, title: `Configuración de comprobantes | ${TITULO}` },
      { path: 'configuracion/emision', component: EmisionComponent, title: `Emisión electrónica | ${TITULO}` },
      { path: 'configuracion/suscripcion', component: SuscripcionComponent, title: `Suscripción y consumo | ${TITULO}` },

      // ── Estados del sistema ──────────────────────────────────────────────
      {
        path: 'sin-permisos',
        component: SinPermisosComponent,
        title: `Sin permisos | ${TITULO}`,
      },

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
    // A donde vuelve Cognito con el código. La URL está declarada en
    // callback_urls (ondexia.infra/identidad.tf) y las dos deben coincidir
    // exactamente: Cognito las compara carácter a carácter.
    path: 'acceso/retorno',
    component: RetornoComponent,
    title: `Accediendo | ${TITULO}`,
  },
  {
    // Último paso del alta: los datos de la empresa. Exige sesión —la
    // identidad ya está probada por Cognito— pero queda fuera del marco de la
    // aplicación: quien está aquí todavía no tiene panel al que ir.
    path: 'acceso/registro',
    component: RegistroComponent,
    canActivate: [sesionGuard],
    title: `Crear cuenta | ${TITULO}`,
  },

  {
    path: '**',
    component: NoEncontradoComponent,
    title: `Página no encontrada | ${TITULO}`,
  },
];
