import {
  Component,
  ElementRef,
  HostListener,
  Input,
  OnDestroy,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { PosicionFlotante, posicionFlotante } from '../panel-flotante/posicion-flotante';

/** Nombre visible de cada módulo, por su primer segmento de ruta. */
const MODULOS: Record<string, string> = {
  almacen: 'Almacén',
  compras: 'Compras',
  ventas: 'Ventas',
  configuracion: 'Configuración',
  perfil: 'Mi perfil',
};

/** Cuánto puede crecer el globo antes de desplazarse por dentro. */
const ALTO_MAXIMO = 280;

/**
 * Encabezado de vista: título, ruta de navegación y la explicación del módulo.
 *
 * <p>El módulo intermedio se deduce de la URL en lugar de recibirse como
 * parámetro. Con setenta vistas, pasarlo a mano garantiza que tarde o temprano
 * una diga «Ventas» estando en Compras; la URL, en cambio, no puede mentir.
 *
 * <h2>Por qué la descripción se esconde detrás de un botón</h2>
 *
 * <p>Estaba siempre visible, en un párrafo entre el título y la tabla. Explica
 * qué es el módulo, y eso se lee <strong>una vez</strong>: quien entra a
 * Establecimientos por décima vez ya sabe qué son, y lo que quiere es la tabla.
 * Cuatro renglones fijos la empujan hacia abajo todos los días para decir algo
 * que solo hizo falta el primero.
 *
 * <p>Detrás del signo de interrogación sigue estando entera, y ahora se puede
 * escribir más larga sin coste: ya no le quita sitio a nadie.
 *
 * <p>Es un botón y no un globo que salta al pasar el ratón. Con el ratón encima
 * se abre sin querer al mover el cursor, y en una pantalla táctil no hay «pasar
 * por encima» — la explicación no existiría en un teléfono.
 */
@Component({
  selector: 'app-encabezado-pagina',
  imports: [CommonModule, RouterModule],
  templateUrl: './encabezado-pagina.component.html',
})
export class EncabezadoPaginaComponent implements OnDestroy {
  @Input({ required: true }) titulo = '';

  /** Qué es este módulo. Sin ella no se pinta el botón. */
  @Input() descripcion = '';

  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly router = inject(Router);

  @ViewChild('disparador') disparador?: ElementRef<HTMLButtonElement>;

  readonly abierto = signal(false);
  readonly posicion = signal<PosicionFlotante>({});

  /**
   * Enfoca y recoloca el globo en cuanto aparece.
   *
   * <p>Con un temporizador tras abrir se llegaba unas veces antes del pintado y
   * otras después; el asignador se ejecuta justo cuando el elemento existe, que
   * es cuando hay algo que enfocar y algo que medir. Es el mismo motivo que en
   * el menú de acciones.
   */
  @ViewChild('globo') set globoAparecido(ref: ElementRef<HTMLElement> | undefined) {
    if (!ref) {
      return;
    }
    ref.nativeElement.focus();
    this.calcular();
  }

  alternar(): void {
    if (this.abierto()) {
      this.cerrar();
      return;
    }
    this.abierto.set(true);
    this.calcular();
    this.escuchar(true);
  }

  cerrar(devolverFoco = false): void {
    if (!this.abierto()) {
      return;
    }
    this.abierto.set(false);
    this.escuchar(false);
    if (devolverFoco) {
      this.disparador?.nativeElement.focus();
    }
  }

  /**
   * Escape cierra, y se detiene aquí.
   *
   * <p>Sin detenerlo, la misma pulsación cierra además el modal que pudiera
   * haber detrás: dos cosas de una tecla.
   */
  alTeclear(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      evento.stopPropagation();
      this.cerrar(true);
    }
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

  /** Módulo al que pertenece la vista, o cadena vacía si es el panel. */
  get modulo(): string {
    const segmento = this.router.url.split('?')[0].split('#')[0].split('/')[1] ?? '';
    return MODULOS[segmento] ?? '';
  }

  get rutaModulo(): string {
    const segmento = this.router.url.split('?')[0].split('/')[1] ?? '';
    return `/${segmento}`;
  }

  /**
   * El módulo solo se enlaza si es distinto del título. En el listado de
   * clientes, «Ventas / Clientes» informa; en la ficha de empresa, repetir
   * «Configuración / Configuración» solo ocupa espacio.
   */
  get muestraModulo(): boolean {
    return Boolean(this.modulo) && this.modulo !== this.titulo;
  }

  private calcular(): void {
    const boton = this.disparador?.nativeElement;
    if (boton) {
      this.posicion.set(
        posicionFlotante(boton, { altoMaximo: ALTO_MAXIMO, ancho: 'contenido' })
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
