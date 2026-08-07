import { Component } from '@angular/core';
import { PageBreadcrumbComponent } from '../../../shared/components/common/page-breadcrumb/page-breadcrumb.component';
import { TablaDatosComponent, ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';

/**
 * Establecimientos anexos de la empresa.
 *
 * No se limitan por plan (documento 04 §2): cobrar por local empujaría al
 * cliente a registrar uno solo y declarar mal el código de establecimiento
 * en comprobantes y guías de remisión.
 */
@Component({
  selector: 'app-establecimientos',
  imports: [PageBreadcrumbComponent, TablaDatosComponent, ConfirmacionComponent],
  templateUrl: './establecimientos.component.html',
})
export class EstablecimientosComponent {
  columnas: ColumnaTabla[] = [
    { campo: 'codigo', titulo: 'Código SUNAT', ordenable: true, ancho: 'w-32' },
    { campo: 'nombre', titulo: 'Establecimiento', ordenable: true },
    { campo: 'direccion', titulo: 'Dirección' },
    { campo: 'distrito', titulo: 'Distrito' },
    { campo: 'tipo', titulo: 'Tipo', ancho: 'w-28' },
  ];

  registros = [
    { id: 1, codigo: '0000', nombre: 'Principal', direccion: 'Av. Javier Prado Este 1234', distrito: 'San Isidro', tipo: 'Principal' },
    { id: 2, codigo: '0001', nombre: 'Miraflores', direccion: 'Av. Larco 456', distrito: 'Miraflores', tipo: 'Anexo' },
    { id: 3, codigo: '0002', nombre: 'Depósito Ate', direccion: 'Carretera Central Km 8', distrito: 'Ate', tipo: 'Anexo' },
  ];

  confirmacionAbierta = false;
  aEliminar: Record<string, unknown> | null = null;

  pedirEliminacion(registro: Record<string, unknown>): void {
    this.aEliminar = registro;
    this.confirmacionAbierta = true;
  }

  eliminar(): void {
    this.registros = this.registros.filter((r) => r.id !== this.aEliminar?.['id']);
    this.confirmacionAbierta = false;
    this.aEliminar = null;
  }
}
