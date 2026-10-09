import {
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  inject,
} from '@angular/core';

/** Anchos previstos. Se nombran por uso, no por píxeles. */
export type AnchoModal = 'estrecho' | 'medio' | 'ancho';

/**
 * Diálogo modal. La única implementación de diálogo del sistema.
 *
 * <h2>Para qué se usa y para qué no</h2>
 *
 * <p>Para <strong>crear</strong> un registro. Ver y editar uno existente va en
 * su propia vista, con su URL: un registro que ya existe se comparte por
 * enlace, se recarga y se abre en otra pestaña, y nada de eso funciona dentro
 * de un modal. Crear no tiene esa necesidad —no hay nada que enlazar todavía— y
 * gana en no perder el listado de vista.
 *
 * <h2>Lo que resuelve una vez, para que nadie lo repita</h2>
 *
 * <p>Un diálogo tiene más detalle del que parece: cerrar con {@code Escape},
 * cerrar al clicar el fondo pero no el contenido, no dejar que el foco del
 * teclado se escape a la página de detrás, devolver el foco a donde estaba al
 * cerrar, y bloquear el desplazamiento del documento. Cada pantalla que se lo
 * monte por su cuenta acertará en unas cosas y fallará en otras.
 *
 * <p>{@link ConfirmacionComponent} se construye sobre este, y por eso hay un
 * {@link #rol}: una confirmación es {@code alertdialog} y no {@code dialog}.
 * Tener dos implementaciones de diálogo era la alternativa, y significa que
 * cualquier arreglo hay que hacerlo dos veces.
 */
@Component({
  selector: 'app-modal',
  imports: [],
  templateUrl: './modal.component.html',
})
export class ModalComponent implements OnChanges, OnDestroy {
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);

  @Input() abierto = false;
  @Input() titulo = '';

  /** Debajo del título, en gris. Para explicar qué hace el formulario. */
  @Input() subtitulo = '';

  @Input() ancho: AnchoModal = 'medio';

  /**
   * Rol de accesibilidad. {@code alertdialog} solo para confirmaciones: le
   * indica al lector de pantalla que interrumpa, y usarlo en un formulario
   * cualquiera convierte esa interrupción en ruido que se aprende a ignorar.
   */
  @Input() rol: 'dialog' | 'alertdialog' = 'dialog';

  /**
   * Con una acción en curso no se puede cerrar, ni con {@code Escape} ni
   * clicando fuera. No es celo: cerrar a mitad de un guardado deja al usuario
   * sin saber si se guardó, y el aviso de resultado llegaría sin contexto.
   */
  @Input() procesando = false;

  /**
   * Si se pinta la cruz de cerrar.
   *
   * <p>Se apaga en las confirmaciones, que ya traen un «Cancelar» explícito:
   * dos controles para lo mismo obligan a decidir cuál usar, y en un diálogo sin
   * título dejaban una cabecera vacía con la cruz suelta.
   */
  @Input() cerrable = true;

  @Output() cerrar = new EventEmitter<void>();

  /** A dónde devolver el foco al cerrar. */
  private disparador: HTMLElement | null = null;

  private static abiertos = 0;

  get clasesAncho(): string {
    if (this.ancho === 'estrecho') {
      return 'max-w-[480px]';
    }
    return this.ancho === 'ancho' ? 'max-w-[840px]' : 'max-w-[640px]';
  }

  ngOnChanges(cambios: SimpleChanges): void {
    if (!cambios['abierto']) {
      return;
    }
    if (this.abierto) {
      this.alAbrir();
    } else if (!cambios['abierto'].firstChange) {
      this.alCerrar();
    }
  }

  ngOnDestroy(): void {
    // Un modal destruido sin pasar por `cerrar` —al navegar, por ejemplo—
    // dejaría el documento sin desplazamiento para siempre.
    if (this.abierto) {
      this.alCerrar();
    }
  }

  /** Cierra si se permite. Lo llaman el fondo, la cruz y {@code Escape}. */
  intentarCerrar(): void {
    if (!this.procesando) {
      this.cerrar.emit();
    }
  }

  @HostListener('document:keydown.escape')
  alPulsarEscape(): void {
    if (this.abierto) {
      this.intentarCerrar();
    }
  }

  /**
   * Mantiene el foco dentro del diálogo.
   *
   * <p>Sin esto, tabulando se llega a los campos y enlaces de la página de
   * detrás, que está visualmente tapada: el foco desaparece de la vista y quien
   * navega con teclado o lector de pantalla se queda sin saber dónde está.
   */
  @HostListener('document:keydown.tab', ['$event'])
  @HostListener('document:keydown.shift.tab', ['$event'])
  alTabular(bruto: Event): void {
    // Angular tipa los pseudo-eventos de teclado como Event; el `.tab`
    // garantiza que es de teclado, pero el tipo hay que estrecharlo aqui.
    const evento = bruto as KeyboardEvent;
    if (!this.abierto) {
      return;
    }

    const enfocables = this.enfocables();
    if (enfocables.length === 0) {
      return;
    }

    const primero = enfocables[0];
    const ultimo = enfocables[enfocables.length - 1];
    const activo = document.activeElement;

    if (evento.shiftKey && (activo === primero || !this.contieneAlFoco())) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && (activo === ultimo || !this.contieneAlFoco())) {
      evento.preventDefault();
      primero.focus();
    }
  }

  private alAbrir(): void {
    this.disparador = document.activeElement as HTMLElement | null;

    // Se cuentan los modales abiertos porque la confirmación puede abrirse
    // sobre otro modal. Con un booleano, al cerrar el de encima se devolvería
    // el desplazamiento al documento con uno todavía abierto detrás.
    ModalComponent.abiertos += 1;
    document.body.classList.add('sin-desplazamiento');

    // Tras pintar: los elementos del diálogo aún no existen en este momento.
    setTimeout(() => {
      const enfocables = this.enfocables();
      (enfocables[0] ?? this.anfitrion.nativeElement.querySelector('[role]'))?.focus?.();
    });
  }

  private alCerrar(): void {
    ModalComponent.abiertos = Math.max(0, ModalComponent.abiertos - 1);
    if (ModalComponent.abiertos === 0) {
      document.body.classList.remove('sin-desplazamiento');
    }
    this.disparador?.focus?.();
    this.disparador = null;
  }

  private contieneAlFoco(): boolean {
    return this.anfitrion.nativeElement.contains(document.activeElement);
  }

  private enfocables(): HTMLElement[] {
    const seleccion =
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]),' +
      ' textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

    return [...this.anfitrion.nativeElement.querySelectorAll<HTMLElement>(seleccion)].filter(
      (elemento) => elemento.offsetParent !== null
    );
  }
}
