import { Component, Input, Output, EventEmitter } from '@angular/core';
import { FormsModule } from '@angular/forms';

/**
 * Códigos del catálogo SUNAT nº 07 — tipo de afectación del IGV.
 * Solo se incluyen los de uso corriente en la v1.0.
 */
export type AfectacionIgv = 'GRAVADO' | 'EXONERADO' | 'INAFECTO' | 'GRATUITO';

export interface LineaDocumento {
  id: string | number;
  productoId?: string | number;
  codigo?: string;
  descripcion: string;
  unidad: string;
  cantidad: number;
  /** Valor sin IGV. Es la base de todo el cálculo. */
  valorUnitario: number;
  descuento: number;
  afectacion: AfectacionIgv;
}

export interface TotalesDocumento {
  gravado: number;
  exonerado: number;
  inafecto: number;
  gratuito: number;
  descuentoTotal: number;
  igv: number;
  total: number;
}

/**
 * Editor de líneas de detalle de un documento.
 *
 * Es el componente P3 del plan de vistas: lo comparten catorce pantallas
 * (cotización, preventa, boleta, factura, nota de crédito, órdenes, notas
 * de compra, liquidación y guías). Se construye una sola vez y se
 * parametriza; duplicarlo por tipo de documento es el error más caro que
 * admite este frontend.
 *
 * El cálculo vive acá y no en cada pantalla porque el redondeo tributario
 * no admite dos implementaciones distintas: SUNAT valida los totales a dos
 * decimales y una diferencia de un céntimo es un comprobante rechazado.
 */
@Component({
  selector: 'app-editor-lineas',
  imports: [FormsModule],
  templateUrl: './editor-lineas.component.html',
})
export class EditorLineasComponent {
  @Input() lineas: LineaDocumento[] = [];

  /** Tasa vigente del IGV. Se recibe como dato, nunca fija en el código. */
  @Input() tasaIgv = 0.18;

  @Input() moneda = 'PEN';

  /** Oculta importes y afectación: las guías de remisión trasladan, no venden. */
  @Input() soloCantidades = false;

  /** Bloquea la edición cuando el documento ya fue emitido. */
  @Input() soloLectura = false;

  @Output() lineasCambio = new EventEmitter<LineaDocumento[]>();
  @Output() totalesCambio = new EventEmitter<TotalesDocumento>();
  @Output() solicitarProducto = new EventEmitter<void>();

  get simboloMoneda(): string {
    return this.moneda === 'USD' ? '$' : 'S/';
  }

  /** Importe de una línea sin IGV, ya descontado. */
  valorLinea(linea: LineaDocumento): number {
    const bruto = linea.cantidad * linea.valorUnitario;
    return this.redondear(bruto - (linea.descuento || 0));
  }

  /** IGV de la línea. Solo las gravadas lo generan. */
  igvLinea(linea: LineaDocumento): number {
    if (linea.afectacion !== 'GRAVADO') {
      return 0;
    }
    return this.redondear(this.valorLinea(linea) * this.tasaIgv);
  }

  totalLinea(linea: LineaDocumento): number {
    return this.redondear(this.valorLinea(linea) + this.igvLinea(linea));
  }

  get totales(): TotalesDocumento {
    const totales: TotalesDocumento = {
      gravado: 0,
      exonerado: 0,
      inafecto: 0,
      gratuito: 0,
      descuentoTotal: 0,
      igv: 0,
      total: 0,
    };

    for (const linea of this.lineas) {
      const valor = this.valorLinea(linea);
      totales.descuentoTotal += linea.descuento || 0;

      switch (linea.afectacion) {
        case 'GRAVADO':
          totales.gravado += valor;
          totales.igv += this.igvLinea(linea);
          break;
        case 'EXONERADO':
          totales.exonerado += valor;
          break;
        case 'INAFECTO':
          totales.inafecto += valor;
          break;
        case 'GRATUITO':
          // Las entregas gratuitas no suman al total a pagar, pero deben
          // declararse por separado en el comprobante.
          totales.gratuito += valor;
          break;
      }
    }

    totales.gravado = this.redondear(totales.gravado);
    totales.exonerado = this.redondear(totales.exonerado);
    totales.inafecto = this.redondear(totales.inafecto);
    totales.gratuito = this.redondear(totales.gratuito);
    totales.igv = this.redondear(totales.igv);
    totales.descuentoTotal = this.redondear(totales.descuentoTotal);
    totales.total = this.redondear(
      totales.gravado + totales.exonerado + totales.inafecto + totales.igv
    );

    return totales;
  }

  agregarLinea(linea: LineaDocumento): void {
    this.lineas = [...this.lineas, linea];
    this.notificar();
  }

  quitarLinea(id: string | number): void {
    this.lineas = this.lineas.filter((l) => l.id !== id);
    this.notificar();
  }

  alEditar(): void {
    this.notificar();
  }

  formato(valor: number): string {
    return valor.toLocaleString('es-PE', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  }

  private notificar(): void {
    this.lineasCambio.emit(this.lineas);
    this.totalesCambio.emit(this.totales);
  }

  /** Redondeo a dos decimales, que es lo que SUNAT valida en los totales. */
  private redondear(valor: number): number {
    return Math.round((valor + Number.EPSILON) * 100) / 100;
  }
}
