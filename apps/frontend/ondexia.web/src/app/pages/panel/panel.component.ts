import { Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { BotonComponent } from '../../shared/components/comunes/boton/boton.component';
import {
  GraficoBarrasComponent,
  PuntoGrafico,
} from '../../shared/components/comunes/grafico-barras/grafico-barras.component';
import {
  EstadoComprobanteComponent,
  EstadoComprobante,
} from '../../shared/components/comunes/estado-comprobante/estado-comprobante.component';
import { ContextoService } from '../../shared/services/contexto.service';

/**
 * Dirección deseada de un indicador.
 *
 * <p>No todo lo que sube es una buena noticia. «Por cobrar» bajando significa
 * que los clientes están pagando; con la regla ingenua de subir en verde y
 * bajar en rojo, esa cifra se pintaba de rojo justo cuando el negocio va bien.
 */
type Mejor = 'arriba' | 'abajo';

interface Indicador {
  etiqueta: string;
  valor: string;
  detalle: string;
  mejor: Mejor;
  /** Movimiento real respecto al periodo anterior. */
  tendencia?: 'sube' | 'baja';
  variacion?: string;
}

interface Aviso {
  texto: string;
  accion: string;
  ruta: string;
  grave: boolean;
}

interface Existencia {
  nombre: string;
  existencias: number;
  minimo: number;
  unidad: string;
}

type Periodo = 'semana' | 'mes' | 'anio';

const FECHA_LIMA = new Intl.DateTimeFormat('es-PE', {
  timeZone: 'America/Lima',
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

const MES_LIMA = new Intl.DateTimeFormat('es-PE', {
  timeZone: 'America/Lima',
  month: 'long',
  year: 'numeric',
});

const HORA_LIMA = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'America/Lima',
  hour: '2-digit',
  hour12: false,
});

/** «agosto de 2026» con la inicial en mayuscula, como cualquier titulo. */
function mesEnCurso(): string {
  const mes = MES_LIMA.format(new Date());
  return mes.charAt(0).toUpperCase() + mes.slice(1);
}

/**
 * Panel principal.
 *
 * <p>Se construye al final del plan a propósito: diseñarlo primero habría
 * significado inventar métricas sobre entidades que aún no existían.
 *
 * <h2>Orden de la página</h2>
 *
 * <p>Primero lo que exige atención, después lo que informa. En un sistema de
 * facturación un comprobante rechazado tiene consecuencia tributaria y plazo,
 * así que no puede vivir como el 5 % de una barra apilada en la columna
 * derecha: cuando hay alguno, sube arriba en una banda roja. Cuando no hay
 * ninguno —el caso normal— la banda no se pinta y el panel empieza por las
 * cifras.
 *
 * <h2>Las cifras son de prueba</h2>
 *
 * <p>No hay endpoint de panel todavía. Los datos están aquí como constantes
 * para poder construir y revisar el diseño, y por eso llevan forma de lo que
 * pedirá la API —números, no cadenas ya formateadas— para que conectarlo sea
 * sustituir el origen y no reescribir la plantilla.
 *
 * <h2>Lo que NO se inventa</h2>
 *
 * <p>La propuesta de diseño traía «Meta mensual: S/ 80 000». No existen metas
 * en el sistema, así que no se pinta ninguna: un dato inventado en el panel
 * enseña al usuario a desconfiar de los que sí son ciertos. Vuelve cuando haya
 * dónde configurarla.
 */
@Component({
  selector: 'app-panel',
  imports: [RouterModule, BotonComponent, GraficoBarrasComponent, EstadoComprobanteComponent],
  templateUrl: './panel.component.html',
})
export class PanelComponent {
  private readonly anfitrion = inject(ElementRef<HTMLElement>);
  readonly contexto = inject(ContextoService);

  /** Menú de «Nueva venta»: boleta o factura. */
  readonly menuVenta = signal(false);

  readonly periodo = signal<Periodo>('semana');

  // ── Datos (provisionales, ver la nota de la clase) ──────────────────────

  readonly comprobantesDelMes: number = 128;
  readonly boletasSinDeclarar: number = 2;

  readonly sunat = { aceptados: 108, pendientes: 14, rechazados: 6 };

  readonly indicadores: Indicador[] = [
    {
      etiqueta: 'Ventas de hoy',
      valor: this.soles(3420),
      detalle: '14 comprobantes emitidos',
      tendencia: 'sube',
      variacion: '12 %',
      mejor: 'arriba',
    },
    {
      etiqueta: 'Ventas del mes',
      valor: this.soles(64180),
      detalle: mesEnCurso(),
      tendencia: 'sube',
      variacion: '8 %',
      mejor: 'arriba',
    },
    {
      etiqueta: 'Por cobrar',
      valor: this.soles(9750),
      detalle: '7 facturas al crédito vencen pronto',
      tendencia: 'baja',
      variacion: '3 %',
      mejor: 'abajo',
    },
    {
      etiqueta: 'Ticket promedio',
      valor: this.soles(244),
      detalle: 'Últimos 30 días',
      tendencia: 'sube',
      variacion: '5 %',
      mejor: 'arriba',
    },
  ];

  /**
   * Las tres series del gráfico.
   *
   * <p>El mes va por semanas y no por días: con treinta barras las etiquetas se
   * solapan hasta ser ilegibles, y el panel no es el sitio donde se compara el
   * día 12 con el 13 — eso es el informe de ventas.
   */
  private readonly series: Record<Periodo, PuntoGrafico[]> = {
    semana: [
      { etiqueta: 'Lun', valor: 2180 },
      { etiqueta: 'Mar', valor: 2740 },
      { etiqueta: 'Mié', valor: 1960 },
      { etiqueta: 'Jue', valor: 3100 },
      { etiqueta: 'Vie', valor: 3620 },
      { etiqueta: 'Sáb', valor: 3420, destacado: true },
      { etiqueta: 'Dom', valor: 1220 },
    ],
    mes: [
      { etiqueta: 'Sem 1', valor: 14820 },
      { etiqueta: 'Sem 2', valor: 16340 },
      { etiqueta: 'Sem 3', valor: 15060 },
      { etiqueta: 'Sem 4', valor: 17960, destacado: true },
    ],
    anio: [
      { etiqueta: 'Ene', valor: 52400 },
      { etiqueta: 'Feb', valor: 48900 },
      { etiqueta: 'Mar', valor: 61200 },
      { etiqueta: 'Abr', valor: 57800 },
      { etiqueta: 'May', valor: 63100 },
      { etiqueta: 'Jun', valor: 59400 },
      { etiqueta: 'Jul', valor: 66700 },
      { etiqueta: 'Ago', valor: 64180, destacado: true },
      { etiqueta: 'Set', valor: 0 },
      { etiqueta: 'Oct', valor: 0 },
      { etiqueta: 'Nov', valor: 0 },
      { etiqueta: 'Dic', valor: 0 },
    ],
  };

  readonly porAgotarse: Existencia[] = [
    { nombre: 'Papel bond A4 75g (millar)', existencias: 4, minimo: 20, unidad: 'uds' },
    { nombre: 'Tinta Epson 664 negro', existencias: 7, minimo: 15, unidad: 'uds' },
    { nombre: 'Archivador lomo ancho', existencias: 12, minimo: 24, unidad: 'uds' },
    { nombre: 'Cinta de embalaje 2"', existencias: 15, minimo: 12, unidad: 'uds' },
  ];

  readonly ultimosComprobantes = [
    { id: 241, numero: 'F001-000241', tipo: 'Factura', cliente: 'Comercial Andina S.A.C.', total: 1240, estado: 'ACEPTADO' as EstadoComprobante },
    { id: 1873, numero: 'B001-001873', tipo: 'Boleta', cliente: 'Cliente varios', total: 86.5, estado: 'ACEPTADO' as EstadoComprobante },
    { id: 240, numero: 'F001-000240', tipo: 'Factura', cliente: 'Distribuidora Norte E.I.R.L.', total: 2980, estado: 'PENDIENTE' as EstadoComprobante },
    { id: 1872, numero: 'B001-001872', tipo: 'Boleta', cliente: 'Cliente varios', total: 145, estado: 'ACEPTADO' as EstadoComprobante },
  ];

  // ── Saludo y fecha ──────────────────────────────────────────────────────

  /**
   * La hora de Lima, no la del navegador.
   *
   * <p>Un portátil con el reloj en otro huso saludaría «buenas noches» a media
   * mañana, y lo que es peor, la fecha del panel diría un día distinto del que
   * SUNAT va a estampar en el comprobante. La zona se fija, no se hereda.
   */
  get saludo(): string {
    const hora = Number(HORA_LIMA.format(new Date()));
    if (hora < 12) {
      return 'Buenos días';
    }
    return hora < 19 ? 'Buenas tardes' : 'Buenas noches';
  }

  get fecha(): string {
    return FECHA_LIMA.format(new Date());
  }

  readonly establecimiento = computed(
    () => this.contexto.establecimientoActivo()?.nombre ?? ''
  );

  // ── Estado de la cuenta ─────────────────────────────────────────────────

  /**
   * Cuenta sin actividad: el panel no tiene nada que resumir.
   *
   * <p>Es el primer estado que ve todo cliente nuevo, y con cifras en cero, un
   * gráfico vacío y una barra apilada gris no dice qué hacer. Se sustituye por
   * una sola tarjeta que lleva a emitir el primer comprobante.
   */
  get sinActividad(): boolean {
    return this.comprobantesDelMes === 0;
  }

  /**
   * Lo que exige atención, derivado de los datos y no de una lista a mano.
   *
   * <p>Escrito como constante, el aviso seguiría ahí cuando ya no haya nada que
   * avisar — que es la forma más rápida de que la gente aprenda a ignorar la
   * banda roja.
   */
  get avisos(): Aviso[] {
    const lista: Aviso[] = [];

    if (this.sunat.rechazados > 0) {
      const varios = this.sunat.rechazados > 1;
      lista.push({
        texto: `${this.sunat.rechazados} ${varios ? 'comprobantes rechazados' : 'comprobante rechazado'} por SUNAT`,
        accion: 'Corregir',
        ruta: '/ventas/facturas',
        grave: true,
      });
    }

    if (this.boletasSinDeclarar > 0) {
      const varias = this.boletasSinDeclarar > 1;
      lista.push({
        texto: `${this.boletasSinDeclarar} ${varias ? 'boletas' : 'boleta'} sin incluir en el resumen diario`,
        accion: 'Declarar',
        ruta: '/ventas/resumen-diario',
        grave: false,
      });
    }

    const agotados = this.porAgotarse.filter((p) => p.existencias === 0).length;
    if (agotados > 0) {
      lista.push({
        texto: `${agotados} ${agotados > 1 ? 'productos agotados' : 'producto agotado'}`,
        accion: 'Ver almacén',
        ruta: '/almacen/por-agotarse',
        grave: false,
      });
    }

    return lista;
  }

  // ── Indicadores ─────────────────────────────────────────────────────────

  /**
   * La flecha dice el movimiento; el color, si ese movimiento conviene.
   *
   * <p>Son dos cosas distintas y por eso se calculan aparte: «Por cobrar»
   * bajando lleva flecha hacia abajo y color verde.
   */
  esBuenaSenal(indicador: Indicador): boolean {
    return (indicador.tendencia === 'sube') === (indicador.mejor === 'arriba');
  }

  clasesVariacion(indicador: Indicador): string {
    return this.esBuenaSenal(indicador)
      ? 'bg-success-50 text-success-700 dark:bg-success-500/15 dark:text-success-400'
      : 'bg-error-50 text-error-700 dark:bg-error-500/15 dark:text-error-400';
  }

  /** Lo que se lee en voz alta, porque la flecha y el color no se anuncian. */
  variacionEnPalabras(indicador: Indicador): string {
    const movimiento = indicador.tendencia === 'sube' ? 'sube' : 'baja';
    return `${movimiento} ${indicador.variacion} respecto al periodo anterior`;
  }

  // ── Gráfico ─────────────────────────────────────────────────────────────

  readonly periodos: { clave: Periodo; nombre: string }[] = [
    { clave: 'semana', nombre: 'Semana' },
    { clave: 'mes', nombre: 'Mes' },
    { clave: 'anio', nombre: 'Año' },
  ];

  get puntos(): PuntoGrafico[] {
    return this.series[this.periodo()];
  }

  get totalDelPeriodo(): string {
    return this.soles(this.puntos.reduce((suma, p) => suma + p.valor, 0));
  }

  get tituloGrafico(): string {
    if (this.periodo() === 'semana') {
      return 'Ventas de la semana';
    }
    return this.periodo() === 'mes' ? 'Ventas del mes' : 'Ventas del año';
  }

  // ── SUNAT ───────────────────────────────────────────────────────────────

  get totalSunat(): number {
    return this.sunat.aceptados + this.sunat.pendientes + this.sunat.rechazados;
  }

  /**
   * Porcentaje de cada estado.
   *
   * <p>El último sale por resta y no por redondeo propio: tres redondeos
   * independientes suman 99 o 101 con la misma facilidad que 100, y un panel
   * que no cuadra por un punto es un panel que nadie vuelve a creer.
   */
  porcentaje(cuantos: number): number {
    if (this.totalSunat === 0) {
      return 0;
    }
    return Math.round((cuantos / this.totalSunat) * 100);
  }

  get porcentajeRechazados(): number {
    if (this.totalSunat === 0) {
      return 0;
    }
    return 100 - this.porcentaje(this.sunat.aceptados) - this.porcentaje(this.sunat.pendientes);
  }

  // ── Existencias ─────────────────────────────────────────────────────────

  /** Cuánto queda frente al mínimo del producto. Ahí está el 100 % de la barra. */
  llenado(item: Existencia): number {
    if (item.minimo <= 0) {
      return 100;
    }
    return Math.min(100, Math.max(3, Math.round((item.existencias / item.minimo) * 100)));
  }

  clasesBarra(item: Existencia): string {
    if (item.existencias === 0) {
      return 'bg-error-500';
    }
    return item.existencias < item.minimo ? 'bg-warning-500' : 'bg-brand-300';
  }

  /** El número solo no dice si 15 es poco: hace falta el mínimo al lado. */
  detalleExistencia(item: Existencia): string {
    return `${item.existencias} ${item.unidad} de un mínimo de ${item.minimo}`;
  }

  // ── Formato ─────────────────────────────────────────────────────────────

  /** Sin céntimos: en un resumen, los dos decimales solo añaden ruido. */
  soles(valor: number): string {
    return 'S/ ' + valor.toLocaleString('es-PE', { maximumFractionDigits: 0 });
  }

  /** Con céntimos: el importe de un comprobante concreto es exacto. */
  importe(valor: number): string {
    return valor.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  // ── Menú de «Nueva venta» ───────────────────────────────────────────────

  alternarMenuVenta(): void {
    this.menuVenta.update((abierto) => !abierto);
  }

  cerrarMenuVenta(): void {
    this.menuVenta.set(false);
  }

  @HostListener('document:pointerdown', ['$event'])
  alPulsarFuera(evento: PointerEvent): void {
    if (this.menuVenta() && !this.anfitrion.nativeElement.contains(evento.target as Node)) {
      this.cerrarMenuVenta();
    }
  }

  @HostListener('document:keydown.escape')
  alPulsarEscape(): void {
    this.cerrarMenuVenta();
  }
}
