import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { MODULOS } from '../../../nucleo/permiso.guard';
import { MenuLateralService } from '../../services/menu-lateral.service';
import { ContextoService } from '../../services/contexto.service';
import { HtmlSeguroPipe, Icono } from '../../pipe/html-seguro.pipe';

export interface EntradaMenu {
  nombre: string;
  icono: Icono;
  /** Ruta directa, para entradas sin submenú. */
  ruta?: string;
  submenu?: EntradaSubmenu[];
}

export interface EntradaSubmenu {
  nombre: string;
  ruta: string;

  /**
   * El submódulo al que pertenece, SIN la acción: {@code almacen.guia_remision}.
   *
   * <p>Va escrito y no deducido de la ruta a propósito. No hay regla que lleve
   * de {@code /almacen/guias-remision} a {@code almacen.guia_remision}, y
   * {@code /configuracion/establecimientos} corresponde a
   * {@code configuracion.sucursal} —«establecimiento» es el término de SUNAT y
   * «sucursal» el de la base—. Cualquier conversión automática acertaría en
   * casi todas y fallaría en silencio en las demás.
   *
   * <p>Ausente significa <strong>siempre visible</strong>, y hay entradas que no
   * tienen submódulo en la tabla {@code permiso}. Se muestra por omisión porque
   * el error contrario —esconder una pantalla porque alguien olvidó anotarla—
   * es el que nadie detecta.
   */
  permiso?: string;
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
  private readonly contexto = inject(ContextoService);
  private readonly router = inject(Router);

  /** Nombre del grupo desplegado, o `null` si están todos cerrados. */
  readonly abierto = signal<string | null>(null);

  private readonly TODOS: GrupoMenu[] = [
    {
      titulo: 'Operación',
      entradas: [
        { icono: 'panel', nombre: 'Panel', ruta: '/' },
        {
          icono: 'almacen',
          nombre: 'Almacén',
          submenu: [
            { nombre: 'Productos', ruta: '/almacen/productos', permiso: 'almacen.producto' },
            { nombre: 'Presentaciones', ruta: '/almacen/presentaciones', permiso: 'almacen.presentacion' },
            // Sin submódulo propio en la tabla `permiso`: es una vista de
            // existencias, pero mapearla a almacen.stock seria suponerlo.
            { nombre: 'Productos por agotarse', ruta: '/almacen/por-agotarse' },
            { nombre: 'Guías de remisión', ruta: '/almacen/guias-remision', permiso: 'almacen.guia_remision' },
            { nombre: 'Guías de ingreso', ruta: '/almacen/guias-ingreso', permiso: 'almacen.guia_ingreso' },
            { nombre: 'Tipos de precio', ruta: '/almacen/tipos-precio', permiso: 'almacen.tipo_precio' },
            { nombre: 'Marcas', ruta: '/almacen/marcas', permiso: 'almacen.marca' },
            { nombre: 'Modelos', ruta: '/almacen/modelos', permiso: 'almacen.modelo' },
            // Sin submódulo propio.
            { nombre: 'Unidades', ruta: '/almacen/unidades' },
            { nombre: 'Almacenes', ruta: '/almacen/almacenes', permiso: 'almacen.almacen' },
          ],
        },
        {
          icono: 'compras',
          nombre: 'Compras',
          submenu: [
            { nombre: 'Facturas', ruta: '/compras/facturas', permiso: 'compras.factura_compra' },
            { nombre: 'Notas de pedido', ruta: '/compras/notas-pedido', permiso: 'compras.nota_pedido' },
            { nombre: 'Liquidación de compra', ruta: '/compras/liquidaciones', permiso: 'compras.liquidacion' },
            { nombre: 'Notas de compra', ruta: '/compras/notas-compra', permiso: 'compras.nota_compra' },
            { nombre: 'Órdenes de compra', ruta: '/compras/ordenes-compra', permiso: 'compras.orden_compra' },
            { nombre: 'Órdenes de servicio', ruta: '/compras/ordenes-servicio', permiso: 'compras.orden_servicio' },
            { nombre: 'Proveedores', ruta: '/compras/proveedores', permiso: 'compras.proveedor' },
          ],
        },
        {
          icono: 'ventas',
          nombre: 'Ventas',
          submenu: [
            { nombre: 'Clientes', ruta: '/ventas/clientes', permiso: 'ventas.cliente' },
            { nombre: 'Cotizaciones', ruta: '/ventas/cotizaciones', permiso: 'ventas.cotizacion' },
            // Facturas y boletas son el mismo submódulo: `ventas.comprobante`.
            // La tabla no las separa, y separarlas aqui sugeriria que se pueden
            // contratar por separado, que no es cierto.
            { nombre: 'Facturas', ruta: '/ventas/facturas', permiso: 'ventas.comprobante' },
            { nombre: 'Boletas', ruta: '/ventas/boletas', permiso: 'ventas.comprobante' },
            { nombre: 'Notas de crédito', ruta: '/ventas/notas-credito', permiso: 'ventas.nota_credito' },
            { nombre: 'Notas de preventa', ruta: '/ventas/preventas', permiso: 'ventas.nota_preventa' },
            // Sin submódulo propio.
            { nombre: 'Comunicación de baja', ruta: '/ventas/comunicacion-baja' },
            { nombre: 'Resumen diario', ruta: '/ventas/resumen-diario', permiso: 'ventas.resumen_diario' },
            // Sin submódulo propio.
            { nombre: 'Formas de pago', ruta: '/ventas/formas-pago' },
          ],
        },
      ],
    },
    {
      titulo: 'Sistema',
      entradas: [
        {
          icono: 'configuracion',
          nombre: 'Configuración',
          submenu: [
            { nombre: 'Empresas', ruta: '/configuracion/empresas', permiso: 'configuracion.empresa' },
            { nombre: 'Identidad visual', ruta: '/configuracion/identidad', permiso: 'configuracion.identidad' },
            // `sucursal` en la base, «Establecimientos» en pantalla: es el
            // termino de SUNAT. El codigo NO se deduce del nombre.
            { nombre: 'Establecimientos', ruta: '/configuracion/establecimientos', permiso: 'configuracion.sucursal' },
            { nombre: 'Series y correlativos', ruta: '/configuracion/series', permiso: 'configuracion.serie' },
            { nombre: 'Usuarios', ruta: '/configuracion/usuarios', permiso: 'configuracion.usuario' },
            { nombre: 'Roles y permisos', ruta: '/configuracion/roles', permiso: 'configuracion.rol' },
            { nombre: 'Comprobantes', ruta: '/configuracion/comprobantes', permiso: 'configuracion.comprobante' },
            // Sin submódulo: la suscripcion es de la CUENTA y la gobierna su
            // administrador, no un permiso de empresa.
            { nombre: 'Suscripción', ruta: '/configuracion/suscripcion' },
          ],
        },
      ],
    },
  ];

  /**
   * El menú, sin lo que esta cuenta no tiene contratado.
   *
   * <h2>El módulo sale de la ruta, igual que en la guarda</h2>
   *
   * <p>El primer segmento de {@code ruta} es el código del módulo, así que una
   * entrada nueva se filtra sola. Es a propósito la misma regla que aplica
   * {@code permisoGuard}: si el menú y la guarda dedujeran el módulo de formas
   * distintas, tarde o temprano una entrada visible llevaría a una redirección,
   * que es justo lo que esto viene a evitar.
   *
   * <h2>Esconder no es proteger</h2>
   *
   * <p>Quien escriba la URL a mano sigue topándose con la guarda, y quien se
   * salte la guarda editando su navegador topa con la API, que recorta los
   * permisos en cada petición. Esto solo evita ofrecer una puerta cerrada.
   *
   * <h2>Dos niveles, dos criterios</h2>
   *
   * <p>El módulo sale de la ruta. El submódulo va escrito en cada entrada,
   * porque de la ruta no se deduce — ver {@link EntradaSubmenu#permiso}.
   *
   * <p>Una entrada cuyo submenú se queda vacío desaparece, y un grupo sin
   * entradas también: un módulo desplegable que no despliega nada, o un título
   * de sección sobre un hueco, se leen como que algo se rompió.
   *
   * <h2>El guardián sigue cubriendo solo el módulo</h2>
   *
   * <p>Esconder la entrada de un submódulo apagado no impide llegar escribiendo
   * la URL: eso lo para la API, que responde 403. Cerrar también esa puerta
   * exige que {@code permisoGuard} conozca este mismo mapa, y entonces conviene
   * que deje de vivir en un componente de presentación.
   */
  readonly grupos = computed(() =>
    this.TODOS.map((grupo) => ({
      ...grupo,
      entradas: grupo.entradas
        .map((entrada) => this.recortar(entrada))
        .filter((entrada): entrada is EntradaMenu => entrada !== null),
    })).filter((grupo) => grupo.entradas.length > 0)
  );

  /** El menú sin filtrar. Solo para que la prueba compruebe los códigos. */
  get todosParaPruebas(): GrupoMenu[] {
    return this.TODOS;
  }

  /** La entrada sin lo que no se alcanza, o {@code null} si no queda nada. */
  private recortar(entrada: EntradaMenu): EntradaMenu | null {
    if (!this.alcanzable(entrada)) {
      return null;
    }

    if (!entrada.submenu) {
      return entrada;
    }

    const submenu = entrada.submenu.filter(
      (sub) => !sub.permiso || this.contexto.puede(`${sub.permiso}:acceder`)
    );

    return submenu.length > 0 ? { ...entrada, submenu } : null;
  }

  private alcanzable(entrada: EntradaMenu): boolean {
    const ruta = entrada.ruta ?? entrada.submenu?.[0]?.ruta;
    const modulo = ruta?.split('/').filter(Boolean)[0];

    // Sin ruta reconocible, o ruta que no es de un módulo —el panel, el
    // perfil—, se muestra: no hay permiso que consultar.
    return !modulo || !MODULOS.has(modulo) || this.contexto.puede(`${modulo}:acceder`);
  }

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
