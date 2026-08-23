import { Component, Input, Output, EventEmitter, ElementRef, HostListener, inject } from '@angular/core';

export interface OpcionContexto {
  id: string | number;
  nombre: string;
  /** RUC en el caso de empresa, código de establecimiento en el otro. */
  detalle?: string;
}

/**
 * Contexto de trabajo activo: empresa y establecimiento.
 *
 * Regla del documento 04 §3: cada nivel se muestra como texto fijo cuando
 * hay una sola opción y como desplegable cuando hay dos o más. Así el plan
 * Base se ve limpio sin selector, y el plan con varias empresas lo activa
 * sin rehacer el encabezado.
 *
 * El establecimiento vive acá y no dentro de cada documento porque
 * determina la serie del comprobante y el almacén que descarga existencias
 * (documento 04 §3.1).
 */
@Component({
  selector: 'app-selector-contexto',
  imports: [],
  templateUrl: './selector-contexto.component.html',
})
export class SelectorContextoComponent {
  private readonly elemento = inject(ElementRef);

  /**
   * Forma en que se presenta.
   *
   * <p>`barra` es el control segmentado del encabezado. `lista` es el mismo
   * dato en vertical, para cuando esto vive dentro de otro menu y no puede
   * abrir desplegables propios — el boton compacto del movil.
   */
  @Input() disposicion: 'barra' | 'lista' = 'barra';

  @Input() empresas: OpcionContexto[] = [];
  @Input() establecimientos: OpcionContexto[] = [];
  @Input() empresaActiva: OpcionContexto | null = null;
  @Input() establecimientoActivo: OpcionContexto | null = null;

  @Output() cambiarEmpresa = new EventEmitter<OpcionContexto>();
  @Output() cambiarEstablecimiento = new EventEmitter<OpcionContexto>();

  desplegado: 'empresa' | 'establecimiento' | null = null;

  get empresaEsSeleccionable(): boolean {
    return this.empresas.length > 1;
  }

  get establecimientoEsSeleccionable(): boolean {
    return this.establecimientos.length > 1;
  }

  /**
   * Si nada se puede elegir, el contexto se pinta como texto y sin recuadro.
   *
   * El borde es la promesa de que ahi se pulsa. Con una sola empresa y un solo
   * establecimiento no hay nada que pulsar, y el recuadro invitaba a intentarlo
   * — que es la version silenciosa de un boton roto.
   */
  get hayAlgoQueElegir(): boolean {
    return this.empresaEsSeleccionable || this.establecimientoEsSeleccionable;
  }

  alternar(cual: 'empresa' | 'establecimiento'): void {
    this.desplegado = this.desplegado === cual ? null : cual;
  }

  elegirEmpresa(opcion: OpcionContexto): void {
    this.cambiarEmpresa.emit(opcion);
    this.desplegado = null;
  }

  elegirEstablecimiento(opcion: OpcionContexto): void {
    this.cambiarEstablecimiento.emit(opcion);
    this.desplegado = null;
  }

  @HostListener('document:click', ['$event'])
  alClicFuera(evento: MouseEvent): void {
    if (!this.elemento.nativeElement.contains(evento.target)) {
      this.desplegado = null;
    }
  }

  /**
   * Escape cierra la lista sin elegir.
   *
   * Faltaba, y es la unica salida de quien navega con el teclado: sin ella,
   * abierto el desplegable, la tabulacion recorre las empresas una por una
   * hasta salir por el final.
   */
  @HostListener('keydown.escape')
  alPulsarEscape(): void {
    this.desplegado = null;
  }
}
