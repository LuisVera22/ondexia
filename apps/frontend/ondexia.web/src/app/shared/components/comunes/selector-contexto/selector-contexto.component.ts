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
}
