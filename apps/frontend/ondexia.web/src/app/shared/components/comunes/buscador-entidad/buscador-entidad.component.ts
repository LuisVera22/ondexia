import { Component, Input, Output, EventEmitter, ElementRef, HostListener, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';

export interface OpcionEntidad {
  id: string | number;
  /** Línea principal: nombre del producto, razón social del cliente. */
  titulo: string;
  /** Línea secundaria: código, RUC, unidad de medida. */
  detalle?: string;
  /** Dato alineado a la derecha, normalmente el precio o el stock. */
  extremo?: string;
  deshabilitada?: boolean;
}

/**
 * Campo de búsqueda con autocompletado para elegir una entidad.
 *
 * Es lo que permite agregar una línea a un comprobante sin salir del
 * teclado: se escribe parte del nombre o del código, se recorre con las
 * flechas y se confirma con Enter. En un punto de venta esa diferencia es
 * la que separa una venta de treinta segundos de una de tres minutos.
 */
@Component({
  selector: 'app-buscador-entidad',
  imports: [FormsModule],
  templateUrl: './buscador-entidad.component.html',
})
export class BuscadorEntidadComponent {
  private readonly elemento = inject(ElementRef);

  @Input() etiqueta = '';
  @Input() marcador = 'Buscar…';
  @Input() opciones: OpcionEntidad[] = [];
  @Input() cargando = false;
  @Input() deshabilitado = false;
  @Input() requerido = false;
  /** Mensaje de error bajo el campo. Vacío significa sin error. */
  @Input() error = '';

  @Output() buscar = new EventEmitter<string>();
  @Output() seleccionar = new EventEmitter<OpcionEntidad>();

  termino = '';
  desplegado = false;
  indiceResaltado = -1;

  get opcionesHabilitadas(): OpcionEntidad[] {
    return this.opciones.filter((o) => !o.deshabilitada);
  }

  alEscribir(valor: string): void {
    this.termino = valor;
    this.indiceResaltado = -1;
    this.desplegado = valor.trim().length > 0;
    this.buscar.emit(valor.trim());
  }

  elegir(opcion: OpcionEntidad): void {
    if (opcion.deshabilitada) {
      return;
    }
    this.seleccionar.emit(opcion);
    // Se limpia porque el uso habitual es agregar una línea tras otra:
    // dejar el texto obligaría a borrarlo a mano en cada iteración.
    this.termino = '';
    this.desplegado = false;
    this.indiceResaltado = -1;
  }

  @HostListener('keydown', ['$event'])
  alPresionarTecla(evento: KeyboardEvent): void {
    if (!this.desplegado) {
      return;
    }
    const total = this.opciones.length;

    if (evento.key === 'ArrowDown') {
      evento.preventDefault();
      this.indiceResaltado = (this.indiceResaltado + 1) % total;
    } else if (evento.key === 'ArrowUp') {
      evento.preventDefault();
      this.indiceResaltado = (this.indiceResaltado - 1 + total) % total;
    } else if (evento.key === 'Enter' && this.indiceResaltado >= 0) {
      evento.preventDefault();
      this.elegir(this.opciones[this.indiceResaltado]);
    } else if (evento.key === 'Escape') {
      this.desplegado = false;
      this.indiceResaltado = -1;
    }
  }

  @HostListener('document:click', ['$event'])
  alClicFuera(evento: MouseEvent): void {
    if (!this.elemento.nativeElement.contains(evento.target)) {
      this.desplegado = false;
    }
  }
}
