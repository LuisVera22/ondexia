import { Component, Input, Output, EventEmitter, TemplateRef, ContentChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PaginaVaciaComponent } from '../pagina-vacia/pagina-vacia.component';

export type AlineacionColumna = 'izquierda' | 'centro' | 'derecha';

export interface ColumnaTabla {
  /** Propiedad del registro que se muestra en la columna. */
  campo: string;
  /** Encabezado visible. */
  titulo: string;
  /** Habilita el ordenamiento por esta columna. */
  ordenable?: boolean;
  /** Los importes y cantidades van a la derecha; el resto a la izquierda. */
  alineacion?: AlineacionColumna;
  /** Ancho fijo opcional, en clases de Tailwind. */
  ancho?: string;
  /** Formato de presentación del valor. */
  formato?: 'texto' | 'importe' | 'cantidad' | 'fecha';
}

export interface OrdenTabla {
  campo: string;
  direccion: 'asc' | 'desc';
}

/**
 * Tabla de datos con ordenamiento, paginación, selección múltiple y estado
 * vacío. La plantilla solo trae tablas estáticas, y un ERP necesita esto en
 * cada listado.
 *
 * El componente no ordena ni pagina por sí mismo: emite la intención y el
 * contenedor decide. Cuando exista backend, ese cálculo se hace en el
 * servidor y esta tabla no cambia.
 */
@Component({
  selector: 'app-tabla-datos',
  imports: [CommonModule, PaginaVaciaComponent],
  templateUrl: './tabla-datos.component.html',
})
export class TablaDatosComponent {
  @Input({ required: true }) columnas: ColumnaTabla[] = [];
  @Input({ required: true }) registros: Record<string, unknown>[] = [];

  /** Campo que identifica de forma única a cada fila. */
  @Input() campoClave = 'id';

  @Input() cargando = false;
  @Input() seleccionable = false;

  @Input() orden: OrdenTabla | null = null;

  @Input() pagina = 1;
  @Input() tamanoPagina = 20;
  @Input() totalRegistros = 0;

  /** Textos del estado vacío. */
  @Input() vacioTitulo = 'No hay registros';
  @Input() vacioDescripcion = '';
  @Input() vacioAccion = '';
  @Input() vacioPorFiltros = false;

  /** Plantilla opcional para la columna de acciones de cada fila. */
  @ContentChild('acciones') plantillaAcciones?: TemplateRef<unknown>;

  @Output() ordenarPor = new EventEmitter<OrdenTabla>();
  @Output() cambiarPagina = new EventEmitter<number>();
  @Output() seleccionCambio = new EventEmitter<unknown[]>();
  @Output() filaClic = new EventEmitter<Record<string, unknown>>();
  @Output() accionVacio = new EventEmitter<void>();

  seleccionados = new Set<unknown>();

  get totalPaginas(): number {
    return Math.max(1, Math.ceil(this.totalRegistros / this.tamanoPagina));
  }

  get desde(): number {
    return this.totalRegistros === 0 ? 0 : (this.pagina - 1) * this.tamanoPagina + 1;
  }

  get hasta(): number {
    return Math.min(this.pagina * this.tamanoPagina, this.totalRegistros);
  }

  get todosSeleccionados(): boolean {
    return this.registros.length > 0 && this.seleccionados.size === this.registros.length;
  }

  alternarOrden(columna: ColumnaTabla): void {
    if (!columna.ordenable) {
      return;
    }
    const direccion =
      this.orden?.campo === columna.campo && this.orden.direccion === 'asc' ? 'desc' : 'asc';
    this.ordenarPor.emit({ campo: columna.campo, direccion });
  }

  alternarSeleccionTodos(): void {
    if (this.todosSeleccionados) {
      this.seleccionados.clear();
    } else {
      this.registros.forEach((r) => this.seleccionados.add(r[this.campoClave]));
    }
    this.seleccionCambio.emit([...this.seleccionados]);
  }

  alternarSeleccion(registro: Record<string, unknown>): void {
    const clave = registro[this.campoClave];
    if (this.seleccionados.has(clave)) {
      this.seleccionados.delete(clave);
    } else {
      this.seleccionados.add(clave);
    }
    this.seleccionCambio.emit([...this.seleccionados]);
  }

  estaSeleccionado(registro: Record<string, unknown>): boolean {
    return this.seleccionados.has(registro[this.campoClave]);
  }

  irAPagina(numero: number): void {
    if (numero >= 1 && numero <= this.totalPaginas && numero !== this.pagina) {
      this.cambiarPagina.emit(numero);
    }
  }

  clasesAlineacion(columna: ColumnaTabla): string {
    // Importes y cantidades van a la derecha: es lo que permite comparar
    // cifras de un vistazo recorriendo la columna.
    if (columna.alineacion === 'derecha' || columna.formato === 'importe' || columna.formato === 'cantidad') {
      return 'text-right';
    }
    return columna.alineacion === 'centro' ? 'text-center' : 'text-left';
  }

  valor(registro: Record<string, unknown>, columna: ColumnaTabla): string {
    const bruto = registro[columna.campo];
    if (bruto === null || bruto === undefined || bruto === '') {
      return '—';
    }
    if (columna.formato === 'importe') {
      return Number(bruto).toLocaleString('es-PE', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      });
    }
    if (columna.formato === 'cantidad') {
      return Number(bruto).toLocaleString('es-PE');
    }
    return String(bruto);
  }

  /** Ventana de páginas alrededor de la actual, para no imprimir cientos. */
  get paginasVisibles(): number[] {
    const total = this.totalPaginas;
    const inicio = Math.max(1, Math.min(this.pagina - 2, total - 4));
    const fin = Math.min(total, inicio + 4);
    const paginas: number[] = [];
    for (let i = inicio; i <= fin; i++) {
      paginas.push(i);
    }
    return paginas;
  }
}
