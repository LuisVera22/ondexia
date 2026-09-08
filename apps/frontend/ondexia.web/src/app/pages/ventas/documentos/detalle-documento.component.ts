import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { DocumentoVentaApi, ESTADOS_SUNAT, EstadoSunatApi, FORMAS_DE_PAGO, TipoDocumentoVenta, VentasApiService } from '../../../nucleo/ventas.api.service';
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
  imports: [EncabezadoPaginaComponent, BotonComponent, RouterModule],
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
    const tipo = this.documento()?.tipo;
    return tipo === '01' ? '/ventas/facturas' : tipo === '03' ? '/ventas/boletas' : '/ventas/notas-venta';
  }

  get tituloImpreso(): string {
    const d = this.documento();
    if (!d) {
      return '';
    }
    return d.tipo === 'NV' ? 'NOTA DE VENTA' : d.tipo === '03' ? 'BOLETA DE VENTA ELECTRÓNICA' : 'FACTURA ELECTRÓNICA';
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
