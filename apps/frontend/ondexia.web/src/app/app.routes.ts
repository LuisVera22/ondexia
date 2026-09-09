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
import { ProductosComponent } from './pages/almacen/productos/productos.component';
import { FichaProductoComponent } from './pages/almacen/ficha-producto/ficha-producto.component';
import { CajasComponent } from './pages/ventas/cajas/cajas.component';
import { PuntoDeVentaComponent } from './pages/ventas/punto-de-venta/punto-de-venta.component';
import { ListaDocumentosComponent } from './pages/ventas/documentos/lista-documentos.component';
import { DetalleDocumentoComponent } from './pages/ventas/documentos/detalle-documento.component';
import { ClientesComponent } from './pages/ventas/clientes/clientes.component';
import { FichaClienteComponent } from './pages/ventas/clientes/ficha-cliente.component';
import { ComunicacionesBajaComponent } from './pages/ventas/comunicaciones-baja/comunicaciones-baja.component';
import { salidaConCambios } from './shared/formularios/salida-con-cambios.guard';

const TITULO = 'Ondexia';

export const routes: Routes = [
  {
    path: '',
    component: MarcoAppComponent,
    /*
     * Una sola guarda en el padre protege todas las rutas hijas. Ponerla en
     * cada una sería la forma segura de que a la siguiente se le olvide.
     *
     * Es comodidad, no seguridad: quien edite el JavaScript en su navegador
     * puede saltarla y no verá nada, porque los datos los sirve la API y esa
     * valida el token en cada petición.
     */
    /*
     * Una sola guarda para todos los modulos, y no una anotacion por ruta.
     *
     * permisoGuard deduce el modulo del primer segmento de la URL, asi que
     * cubre las rutas de abajo y las que se anadan despues. Ver
     * permiso.guard.ts para por que se eligio asi.
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

      /*
       * Las rutas del primer producto, y solo esas.
       *
       * Las maquetas de Compras, de los catálogos de almacén, de cotizaciones,
       * preventas y formas de pago viven en `pages/_maquetas/`, fuera de la
       * compilación (ver el `exclude` de tsconfig.app.json). No se borran
       * —cada una es el diseño acordado de su iteración— pero tampoco tienen
       * ruta: una pantalla alcanzable que no hace nada es peor que una que no
       * está, porque el cliente configura algo y descubre después que no
       * surtía efecto. Vuelven cuando su módulo se construya (doc 12 §7.3).
       */

      // ── Almacén ──────────────────────────────────────────────────────────
      { path: 'almacen/productos', component: ProductosComponent, title: `Productos | ${TITULO}` },
      // Las existencias por almacén se ven y se ajustan en la ficha del
      // producto, no en un listado propio: son por almacén, y una cifra suelta
      // en una lista de productos no dice de cuál.
      { path: 'almacen/productos/:id', component: FichaProductoComponent, title: `Ficha de producto | ${TITULO}`, canDeactivate: [salidaConCambios] },
      { path: 'almacen/almacenes', component: AlmacenesComponent, title: `Almacenes | ${TITULO}` },
      { path: 'almacen/almacenes/:id', component: FichaAlmacenComponent, title: `Almacén | ${TITULO}`, canDeactivate: [salidaConCambios] },

      // ── Ventas ───────────────────────────────────────────────────────────
      { path: 'ventas/cajas', component: CajasComponent, title: `Cajas | ${TITULO}` },
      { path: 'ventas/punto-de-venta', component: PuntoDeVentaComponent, title: `Punto de venta | ${TITULO}` },
      { path: 'ventas/notas-venta', component: ListaDocumentosComponent, data: { tipo: 'NV' }, title: `Notas de venta | ${TITULO}` },
      { path: 'ventas/boletas', component: ListaDocumentosComponent, data: { tipo: 'BOLETA' }, title: `Boletas de venta | ${TITULO}` },
      { path: 'ventas/facturas', component: ListaDocumentosComponent, data: { tipo: 'FACTURA' }, title: `Facturas | ${TITULO}` },
      { path: 'ventas/notas-credito', component: ListaDocumentosComponent, data: { tipo: 'NOTA_CREDITO' }, title: `Notas de crédito | ${TITULO}` },
      { path: 'ventas/documentos/:tipo/:id', component: DetalleDocumentoComponent, title: `Documento | ${TITULO}` },
      { path: 'ventas/comunicaciones-baja', component: ComunicacionesBajaComponent, title: `Comunicaciones de baja | ${TITULO}` },
      { path: 'ventas/clientes', component: ClientesComponent, title: `Clientes | ${TITULO}` },
      { path: 'ventas/clientes/:id', component: FichaClienteComponent, title: `Ficha de cliente | ${TITULO}`, canDeactivate: [salidaConCambios] },

      /*
       * Redirecciones de rutas que existieron.
       *
       * No son deuda: están en marcadores y en pestañas abiertas de quien ya
       * usaba la aplicación, y llevar a «página no encontrada» a alguien que
       * guardó un enlace es un fallo que se ve como si el producto se hubiera
       * roto. Cada una apunta a la pantalla que hace hoy ese trabajo.
       */
      { path: 'ventas/facturas/nueva', redirectTo: 'ventas/punto-de-venta' },
      { path: 'ventas/boletas/nueva', redirectTo: 'ventas/punto-de-venta' },
      // Una nota de crédito nace de un comprobante concreto: se emite desde su
      // ficha, no desde un formulario en blanco que obligue a referenciar el
      // documento de origen a mano.
      { path: 'ventas/notas-credito/nueva', redirectTo: 'ventas/notas-credito', pathMatch: 'full' },
      { path: 'ventas/comunicacion-baja', redirectTo: 'ventas/comunicaciones-baja', pathMatch: 'full' },
      // La maqueta P4 mostraba un comprobante suelto; hoy los tres tipos se
      // ven en la misma ficha, que sí lee de la API.
      { path: 'ventas/comprobantes/:id', redirectTo: 'ventas/documentos/03/:id' },

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
