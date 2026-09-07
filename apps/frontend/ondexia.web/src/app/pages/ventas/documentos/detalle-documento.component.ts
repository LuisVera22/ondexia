import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { DocumentoVentaApi, FORMAS_DE_PAGO, TipoDocumentoVenta, VentasApiService } from '../../../nucleo/ventas.api.service';
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
 * <p>Sin QR ni hash: son del comprobante electrónico, que llega en la
 * iteración 5 con el XML firmado.
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
export class DetalleDocumentoComponent {
  private readonly ventas = inject(VentasApiService);
  private readonly configuracion = inject(ConfiguracionApiService);
  private readonly ruta = inject(ActivatedRoute);

  readonly documento = signal<DocumentoVentaApi | null>(null);
  readonly empresa = signal<Empresa | null>(null);
  readonly establecimiento = signal<Establecimiento | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  /** Ticket de 80 mm o A4. La elección vive solo en esta pestaña. */
  readonly formato = signal<'ticket' | 'a4'>('ticket');

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
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudo cargar el documento.'));
    } finally {
      this.cargando.set(false);
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
