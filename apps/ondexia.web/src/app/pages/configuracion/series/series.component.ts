import { Component } from '@angular/core';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';

/**
 * Series de comprobante por establecimiento y tipo de documento.
 *
 * El correlativo no se edita a mano: lo asigna el sistema al emitir, dentro
 * de la transaccion, para que no existan numeros duplicados ni saltos
 * (DTE seccion 6, flujo F-02).
 */
@Component({
  selector: 'app-series',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent, ConfirmacionComponent],
  templateUrl: './series.component.html',
})
export class SeriesComponent {
  columnas: ColumnaTabla[] = [
    { campo: 'serie', titulo: 'Serie', ordenable: true, ancho: 'w-28' },
    { campo: 'tipoDocumento', titulo: 'Tipo de comprobante', ordenable: true },
    { campo: 'establecimiento', titulo: 'Establecimiento', ordenable: true },
    { campo: 'ultimoNumero', titulo: 'Último número', formato: 'cantidad', ancho: 'w-36' },
    { campo: 'estado', titulo: 'Estado', ancho: 'w-28' },
  ];

  registros = [
    { id: 1, serie: 'F001', tipoDocumento: 'Factura', establecimiento: 'Principal', ultimoNumero: 1284, estado: 'Activa' },
    { id: 2, serie: 'B001', tipoDocumento: 'Boleta', establecimiento: 'Principal', ultimoNumero: 8917, estado: 'Activa' },
    { id: 3, serie: 'F002', tipoDocumento: 'Factura', establecimiento: 'Miraflores', ultimoNumero: 342, estado: 'Activa' },
    { id: 4, serie: 'B002', tipoDocumento: 'Boleta', establecimiento: 'Miraflores', ultimoNumero: 2105, estado: 'Activa' },
    { id: 5, serie: 'FC01', tipoDocumento: 'Nota de crédito', establecimiento: 'Principal', ultimoNumero: 37, estado: 'Activa' },
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
