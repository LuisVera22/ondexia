import {
  Component,
  ElementRef,
  HostListener,
  Input,
  OnDestroy,
  ViewChild,
  forwardRef,
  inject,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface OpcionDesplegable {
  valor: string;
  etiqueta: string;
  /** Texto secundario, en gris, bajo la etiqueta. */
  detalle?: string;
  deshabilitada?: boolean;
}

export type AltoDesplegable = 'compacto' | 'normal' | 'formulario';

/** Alto máximo del panel; a partir de ahí la lista se desplaza. */
const ALTO_MAXIMO_PANEL = 288;

/** Milisegundos que se acumulan las teclas antes de reiniciar la búsqueda. */
const VENTANA_BUSQUEDA = 600;

/**
 * Desplegable con el estilo de la aplicación.
 *
 * Existe porque el `<select>` nativo no se puede estilar: el navegador
 * dibuja la lista desplegada con el aspecto del sistema operativo y ninguna
 * regla CSS la alcanza. Dentro de una interfaz que cuida el detalle, ese
 * recuadro gris del sistema es lo único que se ve fuera de lugar.
 *
 * Reimplementarlo obliga a reponer a mano lo que el nativo daba gratis, y
 * está repuesto: navegación con flechas, Inicio y Fin, búsqueda escribiendo,
 * Escape para cerrar, cierre al hacer clic fuera y los atributos ARIA que
 * necesita un lector de pantalla. Un desplegable a medida sin teclado es
 * inutilizable para quien no usa ratón.
 *
 * El panel se posiciona con `position: fixed` calculado desde el disparador,
 * no con `absolute`. Es lo que le permite salir de una tabla con
 * `overflow-x-auto`, que de otro modo lo recortaría.
 */
@Component({
  selector: 'app-desplegable',
  templateUrl: './desplegable.component.html',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DesplegableComponent),
      multi: true,
    },
  ],
})
export class DesplegableComponent implements ControlValueAccessor, OnDestroy {
  private readonly anfitrion = inject(ElementRef<HTMLElement>);

  @Input({ required: true }) opciones: OpcionDesplegable[] = [];
  @Input() marcador = 'Seleccione';
  @Input() deshabilitado = false;
  @Input() etiquetaAccesible = '';

  /**
   * Identificador del botón, para que el `<label for>` de la ficha lo alcance.
   *
   * No se llama `id` a propósito: Angular deja los atributos literales del
   * host en su sitio además de pasarlos como entrada, así que `id="marca"`
   * sobre `<app-desplegable>` acababa marcando también al host. Con el id
   * duplicado, `label[for]` resolvía al host —que no es un control— y hacer
   * clic en la etiqueta no enfocaba nada.
   */
  @Input() idCampo = '';

  /** Alineación del panel cuando es más ancho que el disparador. */
  @Input() ancho: 'disparador' | 'contenido' = 'disparador';

  /**
   * `compacto` para controles secundarios como el tamaño de página,
   * `formulario` para los campos de una ficha, que van más altos.
   */
  @Input() alto: AltoDesplegable = 'normal';

  @ViewChild('disparador') disparador?: ElementRef<HTMLButtonElement>;

  abierto = signal(false);
  resaltada = signal(-1);
  posicion = signal<Record<string, string>>({});

  private valor = '';
  private busqueda = '';
  private ultimaTecla = 0;

  private alCambiar: (valor: string) => void = () => undefined;
  private alTocar: () => void = () => undefined;

  // ControlValueAccessor ------------------------------------------------

  writeValue(valor: string | null): void {
    this.valor = valor ?? '';
  }

  registerOnChange(fn: (valor: string) => void): void {
    this.alCambiar = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.alTocar = fn;
  }

  setDisabledState(deshabilitado: boolean): void {
    this.deshabilitado = deshabilitado;
  }

  // Estado --------------------------------------------------------------

  get seleccionada(): OpcionDesplegable | undefined {
    return this.opciones.find((o) => o.valor === this.valor);
  }

  get textoVisible(): string {
    return this.seleccionada?.etiqueta ?? this.marcador;
  }

  get haySeleccion(): boolean {
    return Boolean(this.seleccionada);
  }

  get idOpcionResaltada(): string | null {
    const indice = this.resaltada();
    return indice >= 0 ? `${this.idBase}-opcion-${indice}` : null;
  }

  get idBase(): string {
    return this.idCampo || 'desplegable';
  }

  idOpcion(indice: number): string {
    return `${this.idBase}-opcion-${indice}`;
  }

  // Apertura ------------------------------------------------------------

  alternar(): void {
    if (this.deshabilitado) {
      return;
    }
    if (this.abierto()) {
      this.cerrar();
    } else {
      this.mostrar();
    }
  }

  private mostrar(): void {
    this.calcularPosicion();
    this.escuchar(true);
    this.abierto.set(true);
    const actual = this.opciones.findIndex((o) => o.valor === this.valor);
    this.resaltada.set(actual >= 0 ? actual : this.primeraHabilitada());
    queueMicrotask(() => this.desplazarHastaResaltada());
  }

  cerrar(devolverFoco = false): void {
    if (!this.abierto()) {
      return;
    }
    this.abierto.set(false);
    this.resaltada.set(-1);
    this.escuchar(false);
    this.alTocar();
    if (devolverFoco) {
      this.disparador?.nativeElement.focus();
    }
  }

  elegir(opcion: OpcionDesplegable): void {
    if (opcion.deshabilitada) {
      return;
    }
    this.valor = opcion.valor;
    this.alCambiar(opcion.valor);
    this.cerrar(true);
  }

  // Teclado -------------------------------------------------------------

  alTeclear(evento: KeyboardEvent): void {
    if (this.deshabilitado) {
      return;
    }

    const tecla = evento.key;

    if (!this.abierto()) {
      if (tecla === 'Enter' || tecla === ' ' || tecla === 'ArrowDown' || tecla === 'ArrowUp') {
        evento.preventDefault();
        this.mostrar();
      }
      return;
    }

    switch (tecla) {
      case 'Escape':
        evento.preventDefault();
        this.cerrar(true);
        return;
      case 'Tab':
        // Tab sí debe mover el foco: solo se cierra el panel.
        this.cerrar();
        return;
      case 'Enter':
      case ' ': {
        evento.preventDefault();
        const opcion = this.opciones[this.resaltada()];
        if (opcion) {
          this.elegir(opcion);
        }
        return;
      }
      case 'ArrowDown':
        evento.preventDefault();
        this.mover(1);
        return;
      case 'ArrowUp':
        evento.preventDefault();
        this.mover(-1);
        return;
      case 'Home':
        evento.preventDefault();
        this.resaltar(this.primeraHabilitada());
        return;
      case 'End':
        evento.preventDefault();
        this.resaltar(this.ultimaHabilitada());
        return;
    }

    if (tecla.length === 1) {
      this.buscar(tecla);
    }
  }

  /** Búsqueda escribiendo, como en el `<select>` nativo. */
  private buscar(tecla: string): void {
    const ahora = Date.now();
    this.busqueda = ahora - this.ultimaTecla > VENTANA_BUSQUEDA ? tecla : this.busqueda + tecla;
    this.ultimaTecla = ahora;

    const patron = this.busqueda.toLowerCase();
    const encontrada = this.opciones.findIndex(
      (o) => !o.deshabilitada && o.etiqueta.toLowerCase().startsWith(patron)
    );
    if (encontrada >= 0) {
      this.resaltar(encontrada);
    }
  }

  private mover(paso: number): void {
    const total = this.opciones.length;
    let indice = this.resaltada();
    for (let i = 0; i < total; i++) {
      indice = (indice + paso + total) % total;
      if (!this.opciones[indice].deshabilitada) {
        this.resaltar(indice);
        return;
      }
    }
  }

  private resaltar(indice: number): void {
    this.resaltada.set(indice);
    this.desplazarHastaResaltada();
  }

  private primeraHabilitada(): number {
    return this.opciones.findIndex((o) => !o.deshabilitada);
  }

  private ultimaHabilitada(): number {
    for (let i = this.opciones.length - 1; i >= 0; i--) {
      if (!this.opciones[i].deshabilitada) {
        return i;
      }
    }
    return -1;
  }

  private desplazarHastaResaltada(): void {
    const id = this.idOpcionResaltada;
    if (!id) {
      return;
    }
    document.getElementById(id)?.scrollIntoView({ block: 'nearest' });
  }

  // Posición ------------------------------------------------------------

  /**
   * `fixed` calculado desde el disparador. Se abre hacia abajo salvo que no
   * quepa y sí haya sitio arriba.
   */
  private calcularPosicion(): void {
    const boton = this.disparador?.nativeElement;
    if (!boton) {
      return;
    }
    const marco = boton.getBoundingClientRect();
    const debajo = window.innerHeight - marco.bottom;
    const haciaArriba = debajo < ALTO_MAXIMO_PANEL && marco.top > debajo;

    this.posicion.set({
      position: 'fixed',
      left: `${marco.left}px`,
      [haciaArriba ? 'bottom' : 'top']: haciaArriba
        ? `${window.innerHeight - marco.top + 4}px`
        : `${marco.bottom + 4}px`,
      [this.ancho === 'contenido' ? 'min-width' : 'width']: `${marco.width}px`,
      'max-height': `${Math.max(160, Math.min(ALTO_MAXIMO_PANEL, haciaArriba ? marco.top - 12 : debajo - 12))}px`,
    });
  }

  @HostListener('document:pointerdown', ['$event'])
  alPulsarFuera(evento: PointerEvent): void {
    if (this.abierto() && !this.anfitrion.nativeElement.contains(evento.target as Node)) {
      this.cerrar();
    }
  }

  /**
   * El panel es `fixed`: no acompaña al disparador cuando algo se desplaza.
   * Se escucha en fase de captura para enterarse también del desplazamiento
   * de contenedores internos, que no burbujea hasta `window`.
   */
  private readonly reposicionar = (): void => this.calcularPosicion();

  private escuchar(activar: boolean): void {
    const metodo = activar ? 'addEventListener' : 'removeEventListener';
    window[metodo]('scroll', this.reposicionar, true);
    window[metodo]('resize', this.reposicionar);
  }

  ngOnDestroy(): void {
    this.escuchar(false);
  }
}
