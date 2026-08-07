import {
  Component,
  Input,
  Output,
  EventEmitter,
  TemplateRef,
  ContentChild,
  OnChanges,
  SimpleChanges,
} from '@angular/core';
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

/** Opciones de tamaño de página ofrecidas al usuario. */
const TAMANOS_PAGINA = [10, 25, 50, 100];

/**
 * Tabla de datos con ordenamiento, paginación, selección múltiple y estado
 * vacío.
 *
 * **Pagina por sí misma.** Recibe la colección completa y muestra solo la
 * página vigente; el contenedor no tiene que cortar nada. Es lo que permite
 * que las treinta tablas de la aplicación paginen sin que cada una repita la
 * misma lógica.
 *
 * Cuando llegue el backend y la paginación pase al servidor, se activa
 * `paginacionEnServidor`: entonces el componente deja de cortar, muestra lo
 * que reciba y se limita a emitir la página solicitada.
 */
@Component({
  selector: 'app-tabla-datos',
  imports: [CommonModule, PaginaVaciaComponent],
  templateUrl: './tabla-datos.component.html',
})
export class TablaDatosComponent implements OnChanges {
  @Input({ required: true }) columnas: ColumnaTabla[] = [];
  @Input({ required: true }) registros: Record<string, unknown>[] = [];

  /** Campo que identifica de forma única a cada fila. */
  @Input() campoClave = 'id';

  @Input() cargando = false;
  @Input() seleccionable = false;

  @Input() orden: OrdenTabla | null = null;

  /**
   * Con paginación en servidor, el contenedor entrega solo la página vigente
   * e informa `pagina` y `totalRegistros`. Sin ella, el componente se
   * encarga de todo y ambos se ignoran.
   */
  @Input() paginacionEnServidor = false;
  @Input() pagina = 1;
  @Input() tamanoPagina = 10;
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
  @Output() cambiarTamanoPagina = new EventEmitter<number>();
  @Output() seleccionCambio = new EventEmitter<unknown[]>();
  @Output() filaClic = new EventEmitter<Record<string, unknown>>();
  @Output() accionVacio = new EventEmitter<void>();

  readonly tamanosPagina = TAMANOS_PAGINA;
  seleccionados = new Set<unknown>();

  /** Página vigente cuando el componente pagina por su cuenta. */
  private paginaInterna = 1;

  /**
   * Tamaño elegido por el usuario. Se mantiene aparte del `@Input` para no
   * mutar una entrada: si el contenedor volviera a escribirla, se perdería
   * la elección del usuario sin motivo aparente.
   */
  private tamanoElegido: number | null = null;

  ngOnChanges(cambios: SimpleChanges): void {
    // Al cambiar los datos —normalmente por un filtro— hay que volver a la
    // primera página: quedarse en la cuarta con tres resultados mostraría
    // una tabla vacía sin explicación.
    if (cambios['registros'] && !cambios['registros'].firstChange) {
      this.paginaInterna = 1;
    }
  }

  get tamanoVigente(): number {
    return this.tamanoElegido ?? this.tamanoPagina;
  }

  get paginaVigente(): number {
    return this.paginacionEnServidor ? this.pagina : this.paginaInterna;
  }

  get total(): number {
    return this.paginacionEnServidor ? this.totalRegistros : this.registros.length;
  }

  /** Filas que se pintan: la página vigente, o todo si pagina el servidor. */
  get registrosVisibles(): Record<string, unknown>[] {
    if (this.paginacionEnServidor) {
      return this.registros;
    }
    const inicio = (this.paginaInterna - 1) * this.tamanoVigente;
    return this.registros.slice(inicio, inicio + this.tamanoVigente);
  }

  get totalPaginas(): number {
    return Math.max(1, Math.ceil(this.total / this.tamanoVigente));
  }

  get desde(): number {
    return this.total === 0 ? 0 : (this.paginaVigente - 1) * this.tamanoVigente + 1;
  }

  get hasta(): number {
    return Math.min(this.paginaVigente * this.tamanoVigente, this.total);
  }

  get todosSeleccionados(): boolean {
    const visibles = this.registrosVisibles;
    return (
      visibles.length > 0 && visibles.every((r) => this.seleccionados.has(r[this.campoClave]))
    );
  }

  alternarOrden(columna: ColumnaTabla): void {
    if (!columna.ordenable) {
      return;
    }
    const direccion =
      this.orden?.campo === columna.campo && this.orden.direccion === 'asc' ? 'desc' : 'asc';
    this.ordenarPor.emit({ campo: columna.campo, direccion });
  }

  /** Selecciona o deselecciona solo las filas visibles en la página actual. */
  alternarSeleccionTodos(): void {
    const visibles = this.registrosVisibles;
    if (this.todosSeleccionados) {
      visibles.forEach((r) => this.seleccionados.delete(r[this.campoClave]));
    } else {
      visibles.forEach((r) => this.seleccionados.add(r[this.campoClave]));
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
    if (numero < 1 || numero > this.totalPaginas || numero === this.paginaVigente) {
      return;
    }
    if (!this.paginacionEnServidor) {
      this.paginaInterna = numero;
    }
    this.cambiarPagina.emit(numero);
  }

  cambiarTamano(valor: string): void {
    const tamano = Number(valor);
    this.tamanoElegido = tamano;
    this.paginaInterna = 1;
    this.cambiarTamanoPagina.emit(tamano);
  }

  clasesAlineacion(columna: ColumnaTabla): string {
    // Importes y cantidades van a la derecha: es lo que permite comparar
    // cifras de un vistazo recorriendo la columna.
    if (
      columna.alineacion === 'derecha' ||
      columna.formato === 'importe' ||
      columna.formato === 'cantidad'
    ) {
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
    const inicio = Math.max(1, Math.min(this.paginaVigente - 2, total - 4));
    const fin = Math.min(total, inicio + 4);
    const paginas: number[] = [];
    for (let i = inicio; i <= fin; i++) {
      paginas.push(i);
    }
    return paginas;
  }
}
