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

/**
 * Qué significa el valor de una insignia, no de qué color es.
 *
 * <p>Se nombran por lo que dicen y no por el color —`exito` y no `verde`— para
 * que cambiar la paleta no obligue a renombrar nada, que es el mismo criterio de
 * la escala de color del tema.
 */
export type TonoInsignia = 'exito' | 'neutro' | 'aviso' | 'error' | 'marca';

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
  /**
   * Formato de presentación del valor.
   *
   * <p>`insignia` pinta un punto de color delante del texto. Es para estados:
   * «Activo» como texto plano se lee igual que cualquier otro dato y obliga a ir
   * palabra por palabra, mientras que una columna de puntos se recorre de un
   * vistazo. Necesita {@link #tono}.
   */
  formato?: 'texto' | 'importe' | 'cantidad' | 'fecha' | 'insignia';

  /**
   * De qué color va el punto, según el valor de cada fila.
   *
   * <p>Lo decide la pantalla y no el componente: aquí no se sabe si «Anulado» es
   * un estado normal del ciclo de vida o un problema.
   */
  tono?: (registro: Record<string, unknown>) => TonoInsignia;

  /**
   * La columna que identifica la fila. Se pinta con más peso.
   *
   * <p><strong>Una por tabla.</strong> Si hay dos, la segunda se ignora: en el
   * momento en que todo destaca, nada destaca.
   *
   * <p>Destaca esta en lugar de apagar las demás. Apagarlas parece lo mismo y no
   * lo es — deja el resto de la tabla en gris sobre blanco, y son datos que hay
   * que leer, no decoración.
   */
  principal?: boolean;
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

  /** Para cuando hay uno solo: «1 empresa» y no «1 empresas». */
  @Input() nombreSingular = 'registro';

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

  /**
   * Qué se puede buscar, dicho con los nombres de las columnas.
   *
   * <p>«Buscar por ubigeo, distrito o provincia…» dice más que «Buscar…», y sale
   * de las columnas en lugar de escribirse a mano en cada pantalla: escrito a
   * mano se queda desfasado en cuanto alguien añade una columna, y nadie se
   * enteraría — el campo seguiría buscando en ella sin decirlo.
   *
   * <p>Se nombran tres como máximo. Con siete columnas el marcador no cabe en el
   * campo y se corta a media palabra.
   */
  get marcadorBusqueda(): string {
    const nombres = this.columnas.slice(0, 3).map((c) => enMinuscula(c.titulo));
    if (nombres.length === 0) {
      return 'Buscar…';
    }
    const ultimo = nombres.pop();
    const lista = nombres.length ? `${nombres.join(', ')} o ${ultimo}` : ultimo;
    return `Buscar por ${lista}…`;
  }

  /**
   * Si se pinta el campo de búsqueda. Siempre que la tabla sea buscable.
   *
   * <p>Se probó a esconderlo cuando todo cabía en una página, con el argumento
   * de que con menos filas que una página están todas a la vista y buscar no
   * puede revelar nada nuevo. Es cierto y da igual: al abrir una pantalla no se
   * sabe cuántas filas hay, y encontrarse con que el campo no está lleva a
   * pensar que esta tabla no busca — cuando la de al lado sí. Se aprende que el
   * buscador «a veces está», que es lo mismo que no poder contar con él.
   *
   * <p>Es el mismo motivo por el que el paginador no se esconde con una sola
   * página. Un control que aparece y desaparece deja de ser un control y pasa a
   * ser una sorpresa.
   */
  get muestraBuscador(): boolean {
    return this.buscable;
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

  /**
   * Cuántos hay, dicho arriba en la barra.
   *
   * <p>Sube desde el pie porque es lo primero que se quiere saber al abrir la
   * pantalla, y abajo obligaba a recorrer la tabla entera para encontrarlo. Al
   * filtrar dice «3 de 40», que es más útil que solo el resultado.
   */
  get recuento(): string {
    const uno = this.total === 1;
    if (this.hayBusqueda) {
      return `${this.total} de ${this.totalSinFiltrar} ${this.nombrePlural}`;
    }
    return `${this.total} ${uno ? this.nombreSingular : this.nombrePlural}`;
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

  /**
   * Si esta columna es la destacada.
   *
   * <p>Se compara con la primera marcada y no se lee `principal` a secas: así
   * una tabla con dos marcadas destaca una, en vez de las dos.
   */
  esPrincipal(columna: ColumnaTabla): boolean {
    return columna === this.columnas.find((c) => c.principal);
  }

  clasesTexto(columna: ColumnaTabla): string {
    if (this.esPrincipal(columna)) {
      return 'font-medium text-gray-800 dark:text-white/90';
    }
    return 'text-gray-700 dark:text-gray-300';
  }

  /** Clases del punto de la insignia. */
  clasesPunto(columna: ColumnaTabla, registro: Record<string, unknown>): string {
    switch (columna.tono?.(registro) ?? 'neutro') {
      case 'exito':
        return 'bg-success-500';
      case 'aviso':
        return 'bg-warning-500';
      case 'error':
        return 'bg-error-500';
      case 'marca':
        return 'bg-brand-500';
      default:
        return 'bg-gray-300 dark:bg-gray-600';
    }
  }

  /**
   * El texto de una insignia neutra se atenúa.
   *
   * <p>Un «Inactivo» es información de segundo orden —lo que se busca en esa
   * columna es lo que sí está en servicio— y atenuarlo hace que los activos
   * salten a la vista sin tener que leer la palabra.
   */
  esNeutra(columna: ColumnaTabla, registro: Record<string, unknown>): boolean {
    return (columna.tono?.(registro) ?? 'neutro') === 'neutro';
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

/**
 * Baja la inicial, salvo que el titulo entero sean siglas.
 *
 * <p>Pasarlo entero a minusculas destrozaba «Codigo SUNAT», que salia «codigo
 * sunat». Bajar solo la inicial arreglaba ese y rompia el siguiente: «RUC» se
 * convertia en «rUC». Aqui casi todos los titulos llevan una sigla —RUC, SUNAT,
 * CCI, IGV— asi que no es un caso raro, es el caso corriente.
 *
 * <p>Un titulo en mayusculas de principio a fin es una sigla y se deja como
 * esta; en cualquier otro solo baja la primera letra, y lo que venga detras
 * —«... SUNAT»— se conserva.
 */
function enMinuscula(titulo: string): string {
  if (titulo === titulo.toLocaleUpperCase('es')) {
    return titulo;
  }
  return titulo.charAt(0).toLocaleLowerCase('es') + titulo.slice(1);
}
