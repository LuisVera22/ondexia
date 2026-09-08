import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { ModalComponent } from '../../../shared/components/comunes/modal/modal.component';
import { DesplegableComponent, OpcionDesplegable } from '../../../shared/components/comunes/desplegable/desplegable.component';
import { FormsModule } from '@angular/forms';
import { CajaActivaService } from '../../../nucleo/caja-activa.service';
import {
  DocumentoVentaApi,
  ESTADOS_SUNAT,
  EstadoSunatApi,
  FORMAS_DE_PAGO,
  MotivoNotaCredito,
  ResumenDocumentoApi,
  SerieDisponibleApi,
  TipoComprobanteEmitible,
  TipoDocumentoVenta,
  VentasApiService,
} from '../../../nucleo/ventas.api.service';
import { accionConEstado } from '../../../shared/components/comunes/boton/estado-accion';
import { AvisosService } from '../../../shared/services/avisos.service';
import { ContextoService } from '../../../shared/services/contexto.service';
import { ConfiguracionApiService, Empresa, Establecimiento } from '../../../nucleo/configuracion.api.service';
import { mensajeDeError } from '../../../nucleo/errores';

/**
 * Un documento emitido, tal como se imprime.
 *
 * <p>El PDF lo produce el navegador (doc 12 §5.4): la misma vista tiene una
 * hoja de estilos de impresión para ticket de 80 mm y para A4, y
 * {@code window.print()} hace el resto. La leyenda de la nota de venta
 * —«Documento interno, no válido como comprobante de pago»— va en pantalla y en
 * papel, y es la distinción más importante de la interfaz (doc 12 §3.2).
 *
 * <h2>Lo que SUNAT dijo, al lado</h2>
 *
 * <p>Una boleta o factura trae además su estado ante SUNAT (doc 14 §3): en cola,
 * aceptada con su CDR, rechazada con el código y la descripción de SUNAT, o con
 * error de envío. Mientras está en cola la pantalla vuelve a preguntar cada
 * pocos segundos, porque el resultado se lee cuando alguien pregunta, no llega
 * solo. El XML firmado y el CDR se descargan del bus por URL temporal. El
 * resumen de la firma se imprime; el QR normativo queda para cuando el PDF se
 * genere en servidor (doc 12 §5.4).
 */
@Component({
  selector: 'app-detalle-documento',
  imports: [EncabezadoPaginaComponent, BotonComponent, RouterModule, ModalComponent, DesplegableComponent, FormsModule],
  templateUrl: './detalle-documento.component.html',
  styles: `
    @media print {
      :host { display: block; }
      .no-imprimir { display: none !important; }
      .hoja { box-shadow: none !important; border: none !important; margin: 0 !important; }
    }
    @page { margin: 8mm; }
    .hoja--ticket { width: 80mm; font-size: 12px; }
    .hoja--ticket th, .hoja--ticket td { padding: 2px 0; }
  `,
})
export class DetalleDocumentoComponent implements OnDestroy {
  private readonly ventas = inject(VentasApiService);
  private readonly configuracion = inject(ConfiguracionApiService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly avisos = inject(AvisosService);
  private readonly contexto = inject(ContextoService);
  private readonly cajas = inject(CajaActivaService);
  private readonly router = inject(Router);

  readonly documento = signal<DocumentoVentaApi | null>(null);
  readonly empresa = signal<Empresa | null>(null);
  readonly establecimiento = signal<Establecimiento | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  /** Ticket de 80 mm o A4. La elección vive solo en esta pestaña. */
  readonly formato = signal<'ticket' | 'a4'>('ticket');

  /** Lo que SUNAT dijo. Nulo en una nota de venta. */
  readonly sunat = signal<EstadoSunatApi | null>(null);
  readonly textoSunat = computed(() => {
    const s = this.sunat();
    return s ? ESTADOS_SUNAT[s.estado] : '';
  });
  readonly puedeReintentar = computed(
    () => this.contexto.puede('ventas.comprobante:emitir') && this.sunat()?.admiteReintento === true
  );
  /** Cuántas veces se ha vuelto a preguntar mientras está en cola. */
  private sondeos = 0;
  private sondeo: ReturnType<typeof setTimeout> | null = null;

  // ── Anular y canjear (doc 13 §5) ──────────────────────────────────────────

  /** Lo que salió de este documento: sus notas de crédito, o su canje. */
  readonly relacionados = signal<ResumenDocumentoApi[]>([]);

  readonly modal = signal<'ninguno' | 'anular' | 'canjear' | 'baja'>('ninguno');
  readonly motivos = signal<MotivoNotaCredito[]>([]);
  readonly series = signal<SerieDisponibleApi[]>([]);

  motivoElegido = '';
  serieElegida = '';
  tipoDeCanje: TipoComprobanteEmitible = 'BOLETA';
  devolverElDinero = true;
  observacionesDeLaNota = '';

  /**
   * Anular exige una caja abierta: la devolución pasa por el cajón y el arqueo
   * de esa sesión tiene que verla. Se toma la que ya está en uso, que es la
   * misma que ofrece el punto de venta.
   */
  readonly cajaEnUso = computed(() => this.cajas.enUso());

  /** Una boleta o factura que SUNAT aceptó y que nadie ha anulado todavía. */
  readonly sePuedeAnular = computed(() => {
    const d = this.documento();
    return (
      d != null &&
      d.fiscal &&
      d.tipo !== '07' &&
      d.estado === 'EMITIDO' &&
      this.contexto.puede('ventas.nota_credito:anular')
    );
  });

  readonly sePuedeCanjear = computed(() => {
    const d = this.documento();
    return d != null && d.tipo === 'NV' && d.estado === 'EMITIDO' && this.contexto.puede('ventas.nota_venta:canjear');
  });

  readonly motivosQueAnulan = computed(() => this.motivos().filter((m) => m.anula));

  /**
   * La baja es solo para facturas y sus notas: una boleta se anula con nota de
   * crédito (doc 13 §6). El plazo lo comprueba el servidor, que es quien tiene
   * el calendario; aquí no se adivina.
   */
  readonly sePuedeDarDeBaja = computed(() => {
    const d = this.documento();
    return (
      d != null &&
      (d.tipo === '01' || d.tipo === '07') &&
      d.estado === 'EMITIDO' &&
      this.contexto.puede('ventas.comunicacion_baja:enviar')
    );
  });

  readonly opcionesDeMotivo = computed<OpcionDesplegable[]>(() =>
    this.motivosQueAnulan().map((m) => ({
      valor: m.codigo,
      etiqueta: m.nombre,
      detalle: m.repone ? 'La mercadería vuelve al almacén' : 'No mueve existencias',
    }))
  );

  readonly opcionesDeSerie = computed<OpcionDesplegable[]>(() =>
    this.series().map((s) => ({ valor: s.id, etiqueta: s.serie, detalle: `Siguiente: ${s.siguienteNumero}` }))
  );

  constructor() {
    const tipo = this.ruta.snapshot.paramMap.get('tipo') as TipoDocumentoVenta | null;
    const id = this.ruta.snapshot.paramMap.get('id');
    if (tipo && id) {
      void this.cargar(tipo, id);
    } else {
      this.error.set('Falta el documento.');
      this.cargando.set(false);
    }
  }

  private async cargar(tipo: TipoDocumentoVenta, id: string): Promise<void> {
    try {
      const [documento, empresa, establecimientos] = await Promise.all([
        this.ventas.documento(id, tipo),
        this.configuracion.empresa(),
        this.configuracion.establecimientos(),
      ]);
      this.documento.set(documento);
      this.empresa.set(empresa);
      this.establecimiento.set(establecimientos.find((e) => e.id === documento.sucursalId) ?? null);
      if (documento.fiscal) {
        await this.consultarSunat(id);
      }
      // Lo que salió de este documento: las notas de crédito que lo corrigen,
      // o el comprobante que lo canjeó. Un fallo aquí no impide ver la hoja.
      try {
        this.relacionados.set(await this.ventas.relacionadosCon(id));
      } catch {
        this.relacionados.set([]);
      }
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudo cargar el documento.'));
    } finally {
      this.cargando.set(false);
    }
  }

  /**
   * Pregunta a la API, que a su vez mira si el Emisor dejó resultado. En cola
   * se repite cada cuatro segundos hasta quince veces: una emisión normal tarda
   * segundos; un arranque en frío del Emisor, algo más. Pasado el minuto se
   * deja de insistir y el botón «Actualizar» sigue ahí.
   */
  private async consultarSunat(id: string): Promise<void> {
    try {
      const estado = await this.ventas.estadoSunat(id);
      this.sunat.set(estado);
      const documento = this.documento();
      if (estado.estado === 'ACEPTADO' && documento && documento.estado === 'PENDIENTE') {
        this.documento.set({ ...documento, estado: 'EMITIDO' });
      }
      if (estado.estado === 'EN_COLA' && this.sondeos < 15) {
        this.sondeos++;
        this.sondeo = setTimeout(() => void this.consultarSunat(id), 4000);
      }
    } catch {
      // Sin comprobante electrónico o sin permiso: la hoja se ve igual.
      this.sunat.set(null);
    }
  }

  readonly actualizarSunat = accionConEstado(async () => {
    const d = this.documento();
    if (d) {
      this.sondeos = 0;
      await this.consultarSunat(d.id);
    }
  });

  readonly reintentar = accionConEstado(async () => {
    const d = this.documento();
    if (!d) {
      return;
    }
    try {
      this.sunat.set(await this.ventas.reintentarEnvio(d.id));
      this.sondeos = 0;
      this.sondeo = setTimeout(() => void this.consultarSunat(d.id), 4000);
      this.avisos.info('El comprobante volvió a la cola con el mismo número.', 'Reenviando a SUNAT');
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo volver a enviar.'));
      throw fallo;
    }
  });

  /** Abre el diálogo de anulación con lo que hace falta para decidir. */
  async pedirAnulacion(): Promise<void> {
    const d = this.documento();
    if (!d) {
      return;
    }
    this.modal.set('anular');
    this.observacionesDeLaNota = '';
    this.devolverElDinero = true;
    try {
      const [motivos, series] = await Promise.all([
        this.ventas.motivosDeNotaDeCredito(),
        this.ventas.seriesDeNotaDeCredito(d.id),
      ]);
      this.motivos.set(motivos);
      this.series.set(series);
      this.motivoElegido = motivos.find((m) => m.anula)?.codigo ?? '';
      this.serieElegida = series.length === 1 ? series[0].id : '';
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo preparar la anulación.'));
      this.modal.set('ninguno');
    }
  }

  async pedirCanje(): Promise<void> {
    const d = this.documento();
    if (!d) {
      return;
    }
    this.modal.set('canjear');
    this.observacionesDeLaNota = '';
    await this.cargarSeriesDeCanje();
  }

  async cargarSeriesDeCanje(): Promise<void> {
    const d = this.documento();
    if (!d) {
      return;
    }
    try {
      const series = await this.ventas.seriesDeCanje(d.id, this.tipoDeCanje);
      this.series.set(series);
      this.serieElegida = series.length === 1 ? series[0].id : '';
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudieron cargar las series.'));
    }
  }

  cerrarModal(): void {
    this.modal.set('ninguno');
  }

  pedirBaja(): void {
    this.observacionesDeLaNota = '';
    this.modal.set('baja');
  }

  readonly darDeBaja = accionConEstado(async () => {
    const d = this.documento();
    if (!d) {
      return;
    }
    if (!this.observacionesDeLaNota.trim()) {
      this.avisos.error('Indica por qué el comprobante no debió existir: SUNAT lo recibe.');
      throw new Error('Sin motivo');
    }
    try {
      const comunicacion = await this.ventas.darDeBaja([
        { documentoId: d.id, motivo: this.observacionesDeLaNota.trim() },
      ]);
      this.cerrarModal();
      this.avisos.exito(
        'SUNAT responde con un ticket; el comprobante queda anulado cuando lo acepte.',
        `Comunicación ${comunicacion.identificador} enviada`
      );
      void this.router.navigate(['/ventas/comunicaciones-baja']);
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo comunicar la baja.'));
      throw fallo;
    }
  });

  readonly anular = accionConEstado(async () => {
    const d = this.documento();
    const caja = this.cajaEnUso();
    if (!d || !caja) {
      this.avisos.error('Abre una caja antes de anular: la devolución pasa por el cajón.');
      throw new Error('Sin caja abierta');
    }
    try {
      const nota = await this.ventas.anularComprobante(d.id, {
        motivo: this.motivoElegido || undefined,
        cajaId: caja.id,
        serieId: this.serieElegida || null,
        // Lo cobrado se devuelve por la misma vía y en un solo pago: en el
        // mostrador es lo que ocurre. Un reparto distinto entre formas de pago
        // se hace desde la nota de crédito por devolución.
        pagos: this.devolverElDinero
          ? [{ forma: d.pagos[0]?.forma ?? 'EFECTIVO', monto: d.total }]
          : [],
        observaciones: this.observacionesDeLaNota || null,
      });
      this.cerrarModal();
      this.avisos.exito(
        'El comprobante quedará anulado cuando SUNAT acepte la nota.',
        `Nota de crédito ${nota.numeroCompleto}`
      );
      void this.router.navigate(['/ventas/documentos', '07', nota.id]);
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo anular el comprobante.'));
      throw fallo;
    }
  });

  readonly canjear = accionConEstado(async () => {
    const d = this.documento();
    if (!d) {
      return;
    }
    try {
      const comprobante = await this.ventas.canjear(d.id, {
        tipo: this.tipoDeCanje,
        serieId: this.serieElegida || null,
        clienteId: d.cliente?.id ?? null,
        observaciones: this.observacionesDeLaNota || null,
      });
      this.cerrarModal();
      this.avisos.exito(
        'La nota de venta queda canjeada y el comprobante sale hacia SUNAT.',
        `${comprobante.tipoNombre} ${comprobante.numeroCompleto}`
      );
      void this.router.navigate(['/ventas/documentos', comprobante.tipo, comprobante.id]);
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo canjear la nota de venta.'));
      throw fallo;
    }
  });

  rutaDe(documento: { id: string; tipo: TipoDocumentoVenta }): string[] {
    return ['/ventas/documentos', documento.tipo, documento.id];
  }

  readonly descargarXml = accionConEstado(async () => this.abrir(await this.ventas.urlDelXml(this.documento()!.id)));
  readonly descargarCdr = accionConEstado(async () => this.abrir(await this.ventas.urlDelCdr(this.documento()!.id)));

  private abrir(url: string): void {
    window.open(url, '_blank', 'noopener');
  }

  ngOnDestroy(): void {
    if (this.sondeo) {
      clearTimeout(this.sondeo);
    }
  }

  get rutaListado(): string {
    switch (this.documento()?.tipo) {
      case '01':
        return '/ventas/facturas';
      case '03':
        return '/ventas/boletas';
      case '07':
        return '/ventas/notas-credito';
      default:
        return '/ventas/notas-venta';
    }
  }

  get tituloImpreso(): string {
    const d = this.documento();
    if (!d) {
      return '';
    }
    switch (d.tipo) {
      case 'NV':
        return 'NOTA DE VENTA';
      case '03':
        return 'BOLETA DE VENTA ELECTRÓNICA';
      case '07':
        return 'NOTA DE CRÉDITO ELECTRÓNICA';
      default:
        return 'FACTURA ELECTRÓNICA';
    }
  }

  estadoTexto(estado: string): string {
    switch (estado) {
      case 'EMITIDO':
        return 'Emitido';
      case 'PENDIENTE':
        return 'Pendiente de SUNAT';
      case 'CANJEADO':
        return 'Canjeado';
      case 'ANULADO':
        return 'Anulado';
      default:
        return estado;
    }
  }

  formaDePago(codigo: string): string {
    return FORMAS_DE_PAGO.find((f) => f.codigo === codigo)?.nombre ?? codigo;
  }

  imprimir(): void {
    window.print();
  }

  importe(valor: number): string {
    return Number(valor).toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  cantidad(valor: number): string {
    return Number(valor).toLocaleString('es-PE', { maximumFractionDigits: 6 });
  }

  fecha(instante: string): string {
    return new Date(instante).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' });
  }
}
