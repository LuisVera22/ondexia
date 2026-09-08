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
 * Solo un submenú permanece abierto a la vez: permitir varios abiertos obliga
 * a desplazarse para encontrar lo que se busca.
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

  /*
   * Tres entradas, y ninguna que no funcione.
   *
   * El menú listaba Compras entera, ocho catálogos de almacén, cotizaciones,
   * preventas, formas de pago y resumen diario: treinta y tantas entradas, de
   * las cuales las que respondían con datos reales eran una docena. Un menú
   * con entradas que llevan a una maqueta no es un adelanto de lo que vendrá,
   * es una promesa que el cliente descubre incumplida después de configurar
   * algo. Las maquetas siguen en el repositorio bajo `pages/_maquetas/` y
   * vuelven con su iteración (doc 12 §7.3).
   *
   * Almacén no tiene entrada de existencias porque no hay listado de
   * existencias: son por almacén y se ven y se ajustan en la ficha del
   * producto, que es donde el dato tiene contexto.
   */
  private readonly TODOS: GrupoMenu[] = [
    {
      titulo: 'Operación',
      entradas: [
        { icono: 'panel', nombre: 'Panel', ruta: '/' },
        {
          icono: 'ventas',
          nombre: 'Ventas',
          submenu: [
            // Primero: sin caja abierta no se vende (doc 12 §3.4).
            { nombre: 'Cajas', ruta: '/ventas/cajas', permiso: 'ventas.caja' },
            { nombre: 'Punto de venta', ruta: '/ventas/punto-de-venta', permiso: 'ventas.nota_venta' },
            { nombre: 'Notas de venta', ruta: '/ventas/notas-venta', permiso: 'ventas.nota_venta' },
            // Boletas y facturas son el mismo submódulo, `ventas.comprobante`:
            // la tabla no las separa, y separarlas aquí sugeriría que se pueden
            // contratar por separado, que no es cierto.
            { nombre: 'Boletas de venta', ruta: '/ventas/boletas', permiso: 'ventas.comprobante' },
            { nombre: 'Facturas', ruta: '/ventas/facturas', permiso: 'ventas.comprobante' },
            // Se emiten desde la ficha del comprobante; aquí solo se listan.
            { nombre: 'Notas de crédito', ruta: '/ventas/notas-credito', permiso: 'ventas.nota_credito' },
            { nombre: 'Comunicaciones de baja', ruta: '/ventas/comunicaciones-baja', permiso: 'ventas.comunicacion_baja' },
            { nombre: 'Clientes', ruta: '/ventas/clientes', permiso: 'ventas.cliente' },
          ],
        },
        {
          icono: 'almacen',
          nombre: 'Almacén',
          submenu: [
            { nombre: 'Productos', ruta: '/almacen/productos', permiso: 'almacen.producto' },
            { nombre: 'Almacenes', ruta: '/almacen/almacenes', permiso: 'almacen.almacen' },
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
            // Certificado, clave SOL y entorno de SUNAT (doc 14 §4). Es de la
            // empresa, y por eso va con su permiso.
            { nombre: 'Emisión electrónica', ruta: '/configuracion/emision', permiso: 'configuracion.empresa' },
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
