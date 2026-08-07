import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';

/**
 * Códigos del catálogo SUNAT nº 12 — tipo de operación del inventario.
 * Se incluyen los de uso corriente; la tabla completa es más extensa.
 */
export type CodigoOperacion = '01' | '02' | '03' | '04' | '05' | '10' | '16';

/**
 * Sentido del movimiento. Un movimiento entra o sale; nunca ambas cosas.
 * Un traslado entre almacenes son dos movimientos, no uno.
 */
export type TipoMovimiento = 'entrada' | 'salida';

export interface MovimientoKardex {
  fecha: string;
  codigoOperacion: CodigoOperacion;
  /** Tipo, serie y número del documento que sustenta el movimiento. */
  documento: string;
  almacen: string;
  tipo: TipoMovimiento;
  cantidad: number;

  /**
   * Costo unitario de adquisición. Solo en entradas: el costo de una salida
   * no se informa, lo determina el método de valuación.
   */
  costoUnitario?: number;

  /**
   * Costo total exacto de la entrada, cuando se conoce por otra vía que la
   * multiplicación. Es el caso del traslado de ingreso: entra al mismo costo
   * con que salió del otro almacén, y recalcularlo desde el unitario
   * redondeado haría aparecer céntimos que nadie desembolsó.
   */
  costoTotal?: number;
}

/** Fila del kardex ya valorizada, con el saldo acumulado. */
interface FilaKardex extends MovimientoKardex {
  operacion: string;
  /** Costo unitario efectivo: el informado en entradas, el promedio en salidas. */
  costoUnitarioEfectivo: number;
  costoTotalEfectivo: number;
  saldoCantidad: number;
  saldoCostoUnitario: number;
  saldoCostoTotal: number;
  /** Posición ISO de la fecha, para el filtro por rango. */
  fechaIso: string;
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
 * Todo lo que aparece aquí es **costo**, nunca precio. El costo es lo que la
 * existencia nos costó adquirirla; el precio es lo que cobramos al venderla.
 * La diferencia entre ambos es el margen, y confundirlos en el kardex
 * distorsiona el costo de ventas y con él la renta declarada.
 *
 * Es de solo lectura y siempre lo será. El kardex no se edita: se construye
 * a partir de los documentos que mueven existencias —guías de ingreso,
 * ventas, traslados—, y poder tocarlo a mano destruiría la trazabilidad que
 * justifica su existencia.
 *
 * > **Verificar antes de usarlo con fines tributarios:** la correspondencia
 * > con el Registro de Inventario Permanente Valorizado y los códigos del
 * > catálogo nº 12 deben contrastarse con la normativa vigente de SUNAT. El
 * > formato oficial presenta entradas y salidas en columnas separadas; esta
 * > vista las consolida para leerlas en pantalla.
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

  get hayRangoDeFechas(): boolean {
    return Boolean(this.desde || this.hasta);
  }

  limpiarFiltros(): void {
    this.almacenFiltro = '';
    this.desde = '';
    this.hasta = '';
  }

  /**
   * Valoriza todos los movimientos por promedio ponderado.
   *
   * El filtro por almacén se aplica **antes** de acumular: un saldo mezclado
   * de dos almacenes no corresponde a ninguna existencia real.
   */
  private get valorizadas(): FilaKardex[] {
    const movimientos = this.almacenFiltro
      ? this.movimientos.filter((m) => m.almacen === this.almacenFiltro)
      : this.movimientos;

    let saldoCantidad = 0;
    let saldoCostoTotal = 0;

    return movimientos.map((m) => {
      const esEntrada = m.tipo === 'entrada';

      // El costo de salida es el promedio vigente antes del movimiento.
      const promedioVigente = saldoCantidad > 0 ? saldoCostoTotal / saldoCantidad : 0;

      const costoTotalEfectivo = esEntrada
        ? this.redondear(m.costoTotal ?? m.cantidad * (m.costoUnitario ?? 0))
        : this.redondear(m.cantidad * promedioVigente);

      const costoUnitarioEfectivo = esEntrada
        ? (m.costoUnitario ?? (m.cantidad > 0 ? costoTotalEfectivo / m.cantidad : 0))
        : promedioVigente;

      saldoCantidad += esEntrada ? m.cantidad : -m.cantidad;
      saldoCostoTotal = this.redondear(
        saldoCostoTotal + (esEntrada ? costoTotalEfectivo : -costoTotalEfectivo)
      );

      const saldoCostoUnitario =
        saldoCantidad > 0 ? this.redondear(saldoCostoTotal / saldoCantidad) : 0;

      return {
        ...m,
        operacion: OPERACIONES[m.codigoOperacion] ?? 'Otro',
        costoUnitarioEfectivo: this.redondear(costoUnitarioEfectivo),
        costoTotalEfectivo,
        saldoCantidad,
        saldoCostoUnitario,
        saldoCostoTotal,
        fechaIso: this.aIso(m.fecha),
      };
    });
  }

  /**
   * Última fila anterior al rango consultado: es el saldo con que abre el
   * período. Sin ella, un kardex filtrado por fechas arrancaría en cero y
   * mostraría existencias que no coinciden con las reales.
   */
  get saldoInicial(): FilaKardex | null {
    if (!this.desde) {
      return null;
    }
    const anteriores = this.valorizadas.filter((f) => f.fechaIso < this.desde);
    return anteriores.length > 0 ? anteriores[anteriores.length - 1] : null;
  }

  /** Filas del rango consultado, ya valorizadas sobre el histórico completo. */
  get filas(): FilaKardex[] {
    return this.valorizadas.filter(
      (f) => (!this.desde || f.fechaIso >= this.desde) && (!this.hasta || f.fechaIso <= this.hasta)
    );
  }

  get saldoFinal(): FilaKardex | null {
    const filas = this.filas;
    if (filas.length > 0) {
      return filas[filas.length - 1];
    }
    return this.saldoInicial;
  }

  get totalEntradas(): number {
    return this.filas.filter((f) => f.tipo === 'entrada').reduce((s, f) => s + f.cantidad, 0);
  }

  get totalSalidas(): number {
    return this.filas.filter((f) => f.tipo === 'salida').reduce((s, f) => s + f.cantidad, 0);
  }

  formato(valor: number): string {
    return valor.toLocaleString('es-PE', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  }

  cantidad(valor: number): string {
    return valor.toLocaleString('es-PE');
  }

  /** `dd/mm/aaaa` a `aaaa-mm-dd`, que es lo que entrega un `input[type=date]`. */
  private aIso(fecha: string): string {
    const partes = fecha.split('/');
    if (partes.length !== 3) {
      return fecha;
    }
    const [dia, mes, anio] = partes;
    return `${anio}-${mes.padStart(2, '0')}-${dia.padStart(2, '0')}`;
  }

  /** Dos decimales, igual que en el resto del sistema. */
  private redondear(valor: number): number {
    return Math.round((valor + Number.EPSILON) * 100) / 100;
  }
}
