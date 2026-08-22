import {
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnDestroy,
  Output,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { PosicionFlotante, posicionFlotante } from '../panel-flotante/posicion-flotante';

/**
 * Los iconos que puede llevar una acción.
 *
 * <p>Es una lista cerrada y no una ruta SVG suelta a propósito: así «desactivar»
 * se dibuja igual en las seis pantallas que lo ofrecen, y añadir una acción no
 * obliga a buscar de dónde copiar el icono.
 */
export type IconoAccion =
  | 'abrir'
  | 'editar'
  | 'desactivar'
  | 'reactivar'
  | 'retirar'
  | 'eliminar'
  | 'duplicar';

export interface OpcionDeMenu {
  /** Lo que se emite al elegirla. */
  readonly id: string;
  readonly etiqueta: string;
  readonly icono?: IconoAccion;

  /**
   * Que destruya o corte algo. Se pinta en rojo y baja al final, separada.
   *
   * <p>Separada porque la lista se recorre de arriba abajo y con prisa: tener
   * «Eliminar» pegado a «Abrir» es invitar a pulsarlo de más.
   */
  readonly peligrosa?: boolean;

  /** Frase completa para el lector de pantalla. Por defecto, la etiqueta. */
  readonly etiquetaAccesible?: string;
}

/** Cuánto puede crecer el panel antes de desplazarse por dentro. */
const ALTO_MAXIMO_PANEL = 320;

/**
 * De dónde salen los identificadores de las opciones.
 *
 * <p>Hacen falta porque `aria-activedescendant` señala a la opción resaltada por
 * su `id`, y en una tabla hay tantos menús como filas: sin un contador, los
 * veinte menús repetirían los mismos identificadores y el lector de pantalla
 * seguiría al primero que encontrara en el documento.
 */
let siguienteMenu = 0;

/**
 * Menú de acciones de una fila, colgado de un botón de tres puntos.
 *
 * <h2>Por qué un menú y no los iconos sueltos</h2>
 *
 * <p>Porque no escalan. Con una acción cabía; con las cuatro de Usuarios, la
 * columna es una hilera de iconos grises sin etiqueta donde hay que acertar cuál
 * es cuál, y cada fila repite la hilera: en una tabla de veinte filas son
 * ochenta controles compitiendo por la mirada con los datos, que es lo que se
 * venía a leer.
 *
 * <p>En el menú cada acción lleva su nombre escrito. Cuesta un clic más y a
 * cambio no hay que adivinar, ni pasar el ratón por encima esperando el
 * globito.
 *
 * <h2>Es un menú, no un desplegable</h2>
 *
 * <p>{@link DesplegableComponent} elige un valor y lo conserva: es un campo de
 * formulario y por eso es {@code listbox}. Esto dispara una acción y no recuerda
 * nada, así que es {@code menu}. La diferencia no es de forma: al lector de
 * pantalla se le anuncian de otra manera, y un {@code listbox} donde se espera un
 * menú hace que lea «seleccionado» de algo que no se selecciona.
 *
 * <p>Comparten la colocación del panel, que vive en {@code posicionFlotante}.
 */
@Component({
  selector: 'app-menu-acciones',
  imports: [CommonModule],
  templateUrl: './menu-acciones.component.html',
})
export class MenuAccionesComponent implements OnDestroy {
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);

  @ViewChild('disparador') disparador?: ElementRef<HTMLButtonElement>;

  private panel?: ElementRef<HTMLElement>;

  /**
   * Se hace con un asignador y no con un {@code setTimeout} tras abrir.
   *
   * <p>El panel vive dentro de un {@code @if}, así que en el momento de abrir
   * todavía no existe. Un temporizador llegaba unas veces antes que el pintado y
   * otras después —Angular lo programa por su cuenta—, y cuando llegaba antes el
   * foco no se movía y las flechas se las quedaba el botón, que volvía a abrir
   * el menú en lugar de recorrerlo.
   *
   * <p>El asignador se ejecuta justo cuando el elemento aparece, que es
   * exactamente el momento en que hay algo que enfocar y algo que medir.
   */
  @ViewChild('panel') set panelAparecido(ref: ElementRef<HTMLElement> | undefined) {
    this.panel = ref;
    if (!ref) {
      return;
    }
    ref.nativeElement.focus();
    this.calcular(ref.nativeElement.offsetWidth);
  }

  @Input() opciones: OpcionDeMenu[] = [];

  /** De qué fila es este menú. Se dice al lector de pantalla y en el título. */
  @Input() descripcionFila = '';

  @Output() elegir = new EventEmitter<string>();

  private readonly identificador = siguienteMenu++;

  readonly abierto = signal(false);
  readonly posicion = signal<PosicionFlotante>({});

  /** Cuál está resaltada por teclado. −1 mientras no se ha usado el teclado. */
  readonly resaltada = signal(-1);

  /** Las corrientes primero y las peligrosas al final. */
  get ordenadas(): OpcionDeMenu[] {
    return [
      ...this.opciones.filter((o) => !o.peligrosa),
      ...this.opciones.filter((o) => o.peligrosa),
    ];
  }

  /** Dónde empieza el bloque de las peligrosas, para pintar el separador. */
  get primeraPeligrosa(): number {
    return this.opciones.filter((o) => !o.peligrosa).length;
  }

  alternar(): void {
    this.abierto() ? this.cerrar() : this.abrir();
  }

  abrir(resaltar = -1): void {
    if (this.opciones.length === 0) {
      return;
    }
    this.abierto.set(true);
    this.resaltada.set(resaltar);
    this.calcular();
    this.escuchar(true);
  }

  cerrar(devolverFoco = false): void {
    if (!this.abierto()) {
      return;
    }
    this.abierto.set(false);
    this.resaltada.set(-1);
    this.escuchar(false);

    // Solo cuando el cierre lo provocó el teclado. Devolverlo también al cerrar
    // con el ratón arrastraría la vista de vuelta a una fila que quizá ya no es
    // la que se está mirando.
    if (devolverFoco) {
      this.disparador?.nativeElement.focus();
    }
  }

  ejecutar(opcion: OpcionDeMenu): void {
    this.cerrar();
    this.elegir.emit(opcion.id);
  }

  alTeclearEnDisparador(evento: KeyboardEvent): void {
    if (evento.key === 'ArrowDown' || evento.key === 'Enter' || evento.key === ' ') {
      evento.preventDefault();
      this.abrir(0);
      return;
    }
    // Arriba abre por el final: es lo que hace cualquier menú, y ahorra
    // recorrer la lista entera para llegar a la última.
    if (evento.key === 'ArrowUp') {
      evento.preventDefault();
      this.abrir(this.opciones.length - 1);
    }
  }

  alTeclearEnPanel(evento: KeyboardEvent): void {
    const ultima = this.ordenadas.length - 1;

    switch (evento.key) {
      case 'ArrowDown':
        evento.preventDefault();
        this.resaltada.update((i) => (i >= ultima ? 0 : i + 1));
        break;
      case 'ArrowUp':
        evento.preventDefault();
        this.resaltada.update((i) => (i <= 0 ? ultima : i - 1));
        break;
      case 'Home':
        evento.preventDefault();
        this.resaltada.set(0);
        break;
      case 'End':
        evento.preventDefault();
        this.resaltada.set(ultima);
        break;
      case 'Enter':
      case ' ': {
        evento.preventDefault();
        const elegida = this.ordenadas[this.resaltada()];
        if (elegida) {
          this.ejecutar(elegida);
          this.disparador?.nativeElement.focus();
        }
        break;
      }
      case 'Escape':
        // Se detiene aquí: sin esto la misma tecla cierra además el modal o la
        // pantalla que hubiera detrás, y se pierden dos cosas de una pulsación.
        evento.preventDefault();
        evento.stopPropagation();
        this.cerrar(true);
        break;
      case 'Tab':
        // Tabular sale del menú, así que el menú se va con él.
        this.cerrar();
        break;
    }
  }

  idOpcion(indice: number): string {
    return `menu-accion-${this.identificador}-${indice}`;
  }

  get idOpcionResaltada(): string | null {
    return this.resaltada() >= 0 ? this.idOpcion(this.resaltada()) : null;
  }

  @HostListener('document:pointerdown', ['$event'])
  alPulsarFuera(evento: PointerEvent): void {
    if (this.abierto() && !this.anfitrion.nativeElement.contains(evento.target as Node)) {
      this.cerrar();
    }
  }

  ngOnDestroy(): void {
    this.escuchar(false);
  }

  private calcular(anchoPanel?: number): void {
    const boton = this.disparador?.nativeElement;
    if (boton) {
      this.posicion.set(
        posicionFlotante(boton, {
          altoMaximo: ALTO_MAXIMO_PANEL,
          alineacion: 'derecha',
          ancho: 'contenido',
          anchoPanel: anchoPanel ?? this.panel?.nativeElement.offsetWidth,
        })
      );
    }
  }

  private readonly recolocar = (): void => this.calcular();

  private escuchar(activar: boolean): void {
    const metodo = activar ? 'addEventListener' : 'removeEventListener';
    window[metodo]('scroll', this.recolocar, true);
    window[metodo]('resize', this.recolocar);
  }
}
