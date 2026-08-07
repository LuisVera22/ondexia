import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';

/**
 * Códigos del catálogo SUNAT nº 12 — tipo de operación del inventario.
 * Se incluyen los de uso corriente; la tabla completa es más extensa.
 */
export type CodigoOperacion = '01' | '02' | '03' | '04' | '05' | '10' | '16';

export interface MovimientoKardex {
  fecha: string;
  codigoOperacion: CodigoOperacion;
  /** Tipo, serie y número del documento que sustenta el movimiento. */
  documento: string;
  almacen: string;
  /** Cantidad que ingresa, con su costo unitario de adquisición. */
  entradaCantidad?: number;
  entradaCostoUnitario?: number;
  /** Cantidad que sale. Su costo lo determina el método de valuación. */
  salidaCantidad?: number;
}

/** Fila del kardex ya valorizada, con el saldo acumulado. */
interface FilaKardex extends MovimientoKardex {
  operacion: string;
  salidaCostoUnitario: number;
  entradaCostoTotal: number;
  salidaCostoTotal: number;
  saldoCantidad: number;
  saldoCostoUnitario: number;
  saldoCostoTotal: number;
}

const OPERACIONES: Record<CodigoOperacion, string> = {
  '01': 'Inventario inicial',
  '02': 'Compra',
  '03': 'Consignación recibida',
  '04': 'Devolución de cliente',
  '05': 'Traslado de ingreso',
  '10': 'Venta',
  '16': 'Traslado de salida',
};

/**
 * Kardex de un producto — libro mayor de sus existencias.
 *
 * Muestra cada movimiento en orden cronológico con el saldo acumulado, y
 * valoriza las salidas por **promedio ponderado**: cada entrada recalcula el
 * costo promedio, y las salidas se descargan a ese costo.
 *
 * Es de solo lectura y siempre lo será. El kardex no se edita: se construye
 * a partir de los documentos que mueven existencias —guías de ingreso,
 * ventas, traslados—, y poder tocarlo a mano destruiría la trazabilidad que
 * justifica su existencia.
 *
 * > **Verificar antes de usarlo con fines tributarios:** la correspondencia
 * > con el Registro de Inventario Permanente Valorizado y los códigos del
 * > catálogo nº 12 deben contrastarse con la normativa vigente de SUNAT.
 */
@Component({
  selector: 'app-kardex',
  imports: [FormsModule],
  templateUrl: './kardex.component.html',
})
export class KardexComponent {
  @Input() movimientos: MovimientoKardex[] = [];
  @Input() unidad = 'NIU';
  @Input() moneda = 'PEN';
  @Input() almacenes: string[] = [];

  almacenFiltro = '';
  desde = '';
  hasta = '';

  get simboloMoneda(): string {
    return this.moneda === 'USD' ? '$' : 'S/';
  }

  get hayFiltrosActivos(): boolean {
    return Boolean(this.almacenFiltro || this.desde || this.hasta);
  }

  limpiarFiltros(): void {
    this.almacenFiltro = '';
    this.desde = '';
    this.hasta = '';
  }

  /**
   * Valoriza los movimientos por promedio ponderado.
   *
   * El filtro por almacén se aplica **antes** de acumular: un saldo mezclado
   * de dos almacenes no corresponde a ninguna existencia real.
   */
  get filas(): FilaKardex[] {
    const movimientos = this.almacenFiltro
      ? this.movimientos.filter((m) => m.almacen === this.almacenFiltro)
      : this.movimientos;

    let saldoCantidad = 0;
    let saldoCostoTotal = 0;

    return movimientos.map((m) => {
      const entradaCantidad = m.entradaCantidad ?? 0;
      const entradaCostoUnitario = m.entradaCostoUnitario ?? 0;
      const salidaCantidad = m.salidaCantidad ?? 0;

      const entradaCostoTotal = this.redondear(entradaCantidad * entradaCostoUnitario);

      // El costo de salida es el promedio vigente antes del movimiento.
      const promedioVigente = saldoCantidad > 0 ? saldoCostoTotal / saldoCantidad : 0;
      const salidaCostoTotal = this.redondear(salidaCantidad * promedioVigente);

      saldoCantidad += entradaCantidad - salidaCantidad;
      saldoCostoTotal = this.redondear(saldoCostoTotal + entradaCostoTotal - salidaCostoTotal);

      const saldoCostoUnitario =
        saldoCantidad > 0 ? this.redondear(saldoCostoTotal / saldoCantidad) : 0;

      return {
        ...m,
        operacion: OPERACIONES[m.codigoOperacion] ?? 'Otro',
        salidaCostoUnitario: this.redondear(promedioVigente),
        entradaCostoTotal,
        salidaCostoTotal,
        saldoCantidad,
        saldoCostoUnitario,
        saldoCostoTotal,
      };
    });
  }

  get saldoFinal(): FilaKardex | null {
    const filas = this.filas;
    return filas.length > 0 ? filas[filas.length - 1] : null;
  }

  get totalEntradas(): number {
    return this.filas.reduce((s, f) => s + (f.entradaCantidad ?? 0), 0);
  }

  get totalSalidas(): number {
    return this.filas.reduce((s, f) => s + (f.salidaCantidad ?? 0), 0);
  }

  formato(valor: number): string {
    return valor.toLocaleString('es-PE', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  }

  cantidad(valor: number | undefined): string {
    if (!valor) {
      return '';
    }
    return valor.toLocaleString('es-PE');
  }

  /** Dos decimales, igual que en el resto del sistema. */
  private redondear(valor: number): number {
    return Math.round((valor + Number.EPSILON) * 100) / 100;
  }
}
