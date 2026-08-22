import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnChanges,
  SimpleChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PaginaVaciaComponent } from '../pagina-vacia/pagina-vacia.component';
import {
  MenuAccionesComponent,
  OpcionDeMenu,
} from '../menu-acciones/menu-acciones.component';
import { DesplegableComponent, OpcionDesplegable } from '../desplegable/desplegable.component';

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

/**
 * Una acción que se puede hacer sobre una fila.
 *
 * <p>Se declara como dato y no como plantilla porque el componente necesita
 * decidir cómo presentarlas —hoy en un menú— y con una plantilla suelta no
 * puede: recibiría marcas ya dibujadas. Es lo que permitió pasar de una hilera
 * de iconos a un menú sin tocar las quince pantallas que las ofrecen.
 */
export interface AccionDeFila extends OpcionDeMenu {
  /**
   * Si esta fila la admite. Sin esto, se ofrece siempre.
   *
   * <p>Es lo que distingue «Desactivar» de «Reactivar»: son dos acciones con la
   * misma casilla, cada una con su condición, y nunca aparecen juntas.
   */
  readonly disponible?: (registro: Record<string, unknown>) => boolean;
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
  imports: [
    CommonModule,
    FormsModule,
    PaginaVaciaComponent,
    DesplegableComponent,
    MenuAccionesComponent,
  ],
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

  /**
   * Si se ofrece el buscador.
   *
   * <p>Encendido por defecto y <strong>siempre visible</strong> cuando lo está,
   * por el mismo motivo que el paginador: un control que aparece y desaparece
   * según cuántas filas haya obliga a preguntarse si la tabla no busca o si el
   * campo se rompió. Se apaga solo donde buscar no significa nada.
   */
  @Input() buscable = true;

  /**
   * Cómo se llama en plural lo que hay en la tabla: «empresas», «series».
   *
   * <p>Va en el marcador del buscador y en la etiqueta del botón de actualizar,
   * siempre sin artículo —«Buscar empresas…»—. Con artículo habría que saber el
   * género de cada palabra, y «Buscar entre los empresas» es exactamente lo que
   * salía antes de quitarlo.
   */
  @Input() nombrePlural = 'registros';

  /** Textos del estado vacío. */
  @Input() vacioTitulo = 'No hay registros';
  @Input() vacioDescripcion = '';
  @Input() vacioAccion = '';
  @Input() vacioPorFiltros = false;

  /** Lo que se puede hacer con una fila. Sin acciones, no hay columna. */
  @Input() acciones: AccionDeFila[] = [];

  /**
   * Campo del que sale el nombre de la fila para el lector de pantalla.
   *
   * <p>Sin él, veinte filas ofrecen veinte botones que se anuncian «Acciones» y
   * no hay forma de saber de cuál es cada uno.
   */
  @Input() campoDescripcion = '';

  @Output() ordenarPor = new EventEmitter<OrdenTabla>();
  @Output() cambiarPagina = new EventEmitter<number>();
  @Output() cambiarTamanoPagina = new EventEmitter<number>();
  @Output() seleccionCambio = new EventEmitter<unknown[]>();
  @Output() filaClic = new EventEmitter<Record<string, unknown>>();
  @Output() accionVacio = new EventEmitter<void>();

  /** Qué se eligió y sobre qué fila. */
  @Output() accionElegida = new EventEmitter<{
    accion: string;
    registro: Record<string, unknown>;
  }>();

  /**
   * Pide al contenedor que vuelva a traer los datos.
   *
   * <p>El botón solo se pinta si alguien escucha —ver {@link #recargable}—. Un
   * botón de actualizar en una tabla que no tiene de dónde traer nada es un
   * control que miente: se pulsa, no pasa nada, y a partir de ahí no se sabe si
   * está roto o si es que no había novedades.
   */
  @Output() actualizar = new EventEmitter<void>();

  readonly tamanosPagina = TAMANOS_PAGINA;
  readonly opcionesTamano: OpcionDesplegable[] = TAMANOS_PAGINA.map((t) => ({
    valor: String(t),
    etiqueta: String(t),
  }));

  seleccionados = new Set<unknown>();

  /** Lo que hay escrito en el buscador. */
  busqueda = '';

  /**
   * El resultado del filtro, calculado y guardado.
   *
   * <p>No es un getter: se lee en cada ciclo de detección de cambios —varias
   * veces por interacción— y recorrer unos miles de filas cada vez se nota al
   * escribir. Se recalcula cuando cambia una de las dos cosas de las que
   * depende: los registros o el texto.
   */
  private filtrados: Record<string, unknown>[] = [];

  /**
   * Si alguna vez llegaron filas.
   *
   * <p>Distingue la primera carga de una recarga, que no son lo mismo: la
   * primera no tiene nada que enseñar y merece el esqueleto, mientras que
   * cambiar una tabla con datos por cinco barras grises hace parpadear lo que el
   * usuario estaba mirando para devolverle casi lo mismo.
   */
  private tuvoFilas = false;

  /** Página vigente cuando el componente pagina por su cuenta. */
  private paginaInterna = 1;

  /**
   * Tamaño elegido por el usuario. Se mantiene aparte del `@Input` para no
   * mutar una entrada: si el contenedor volviera a escribirla, se perdería
   * la elección del usuario sin motivo aparente.
   */
  private tamanoElegido: number | null = null;

  ngOnChanges(cambios: SimpleChanges): void {
    if (!cambios['registros']) {
      return;
    }

    // Al cambiar los datos —normalmente por un filtro— hay que volver a la
    // primera página: quedarse en la cuarta con tres resultados mostraría
    // una tabla vacía sin explicación.
    //
    // No se vuelve al principio al RECARGAR los mismos datos: quien está en la
    // página cuatro y pulsa actualizar espera seguir en la cuatro.
    if (!cambios['registros'].firstChange && this.paginaInterna > 1 && this.cambioElConjunto(cambios)) {
      this.paginaInterna = 1;
    }

    if (this.registros.length > 0) {
      this.tuvoFilas = true;
    }
    this.recalcularFiltro();
  }

  /** Si lo que llegó es otro conjunto y no el mismo recargado. */
  private cambioElConjunto(cambios: SimpleChanges): boolean {
    const antes = (cambios['registros'].previousValue ?? []) as Record<string, unknown>[];
    const ahora = this.registros;
    if (antes.length !== ahora.length) {
      return true;
    }
    return antes.some((fila, i) => fila?.[this.campoClave] !== ahora[i]?.[this.campoClave]);
  }

  // ── Buscador ──────────────────────────────────────────────────────────────

  /**
   * Filtra por lo que se ve, no por lo que hay debajo.
   *
   * <p>Se compara contra {@link #valor}, que es el texto que aparece en la
   * celda. Es deliberado: la columna «Estado» de una empresa muestra «Activa»
   * mientras el dato es un booleano, y buscar «activa» sin encontrar nada —
   * teniéndolo delante en la pantalla— hace que el buscador parezca roto.
   *
   * <p>Cada palabra se busca por separado y todas deben aparecer, en la columna
   * que sea. Así «lima demo» encuentra la fila aunque las dos palabras estén en
   * columnas distintas y en otro orden.
   *
   * <p>Se compara además contra una versión sin puntuación. Aquí casi toda razón
   * social termina en «S.A.C.» o «E.I.R.L.», y quien busca teclea «sac»: sin
   * esto no encuentra nada, porque los puntos se interponen entre las letras.
   * Lo mismo con una serie escrita «F001-00000012» y buscada sin el guion.
   */
  filtrar(texto: string): void {
    this.busqueda = texto;
    this.paginaInterna = 1;
    this.recalcularFiltro();
  }

  limpiarBusqueda(): void {
    this.filtrar('');
  }

  get hayBusqueda(): boolean {
    return this.buscable && this.busqueda.trim().length > 0;
  }

  private recalcularFiltro(): void {
    const terminos = normalizar(this.busqueda).split(/\s+/).filter(Boolean);

    if (!this.buscable || terminos.length === 0) {
      this.filtrados = this.registros;
      return;
    }

    const compactos = terminos.map(compactar);

    this.filtrados = this.registros.filter((registro) => {
      const texto = normalizar(
        this.columnas.map((columna) => this.valor(registro, columna)).join(' ')
      );
      const compacto = compactar(texto);

      return terminos.every(
        (termino, i) => texto.includes(termino) || compacto.includes(compactos[i])
      );
    });
  }

  // ── Carga ─────────────────────────────────────────────────────────────────

  /** Barras grises en lugar de la tabla. Solo la primera vez. */
  get muestraEsqueleto(): boolean {
    return this.cargando && !this.tuvoFilas;
  }

  /** Recargando con datos ya en pantalla: la tabla se queda, gira el icono. */
  get refrescando(): boolean {
    return this.cargando && this.tuvoFilas;
  }

  /**
   * Si se pinta el botón de actualizar.
   *
   * <p>Se mira si alguien escucha la salida en lugar de pedir una entrada más.
   * Una entrada se puede quedar en `false` con el `(actualizar)` puesto, o al
   * revés, y entonces el botón falta o no hace nada; esto no se puede
   * desincronizar porque es la misma cosa preguntada de un solo modo.
   */
  get recargable(): boolean {
    return this.actualizar.observed;
  }

  get tamanoVigente(): number {
    return this.tamanoElegido ?? this.tamanoPagina;
  }

  get tamanoVigenteTexto(): string {
    return String(this.tamanoVigente);
  }

  get paginaVigente(): number {
    return this.paginacionEnServidor ? this.pagina : this.paginaInterna;
  }

  /** Cuántos hay tras el buscador. Es lo que cuenta el pie y lo que pagina. */
  get total(): number {
    return this.paginacionEnServidor ? this.totalRegistros : this.filtrados.length;
  }

  /** Cuántos hay en total, se busque o no. Para decir «3 de 40». */
  get totalSinFiltrar(): number {
    return this.paginacionEnServidor ? this.totalRegistros : this.registros.length;
  }

  /** Filas que se pintan: la página vigente, o todo si pagina el servidor. */
  get registrosVisibles(): Record<string, unknown>[] {
    if (this.paginacionEnServidor) {
      return this.registros;
    }
    const inicio = (this.paginaInterna - 1) * this.tamanoVigente;
    return this.filtrados.slice(inicio, inicio + this.tamanoVigente);
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

  /** Las acciones que esta fila admite, ya filtradas. */
  opcionesDeFila(registro: Record<string, unknown>): OpcionDeMenu[] {
    return this.acciones.filter((accion) => !accion.disponible || accion.disponible(registro));
  }

  descripcionDeFila(registro: Record<string, unknown>): string {
    const bruto = this.campoDescripcion ? registro[this.campoDescripcion] : null;
    return bruto === null || bruto === undefined ? '' : String(bruto);
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

/**
 * Deja el texto en minúsculas y sin tildes, para comparar.
 *
 * <p>Sin quitar las tildes, «Cañete» no se encuentra escribiendo «canete» y
 * «Áncash» no se encuentra escribiendo «ancash» — y en un teclado con prisa eso
 * es la mitad de las búsquedas. La descomposición NFD separa la letra de su
 * acento y el rango de marcas combinantes se descarta.
 */
function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}

/**
 * Solo letras y números, sin separadores de ninguna clase.
 *
 * <p>Es la segunda forma contra la que se compara. Junta lo que la puntuación
 * separa —«S.A.C.» pasa a «sac», «F001-00000012» a «f00100000012»— para que se
 * encuentre escribiéndolo del tirón.
 *
 * <p>Al pegar todo, dos palabras contiguas quedan unidas y pueden producir
 * alguna coincidencia que nadie buscaba. Se acepta: en un buscador de tabla,
 * equivocarse enseñando una fila de más es mucho menos grave que esconder la
 * que se estaba buscando.
 */
function compactar(texto: string): string {
  return texto.replace(/[^a-z0-9]/g, '');
}
