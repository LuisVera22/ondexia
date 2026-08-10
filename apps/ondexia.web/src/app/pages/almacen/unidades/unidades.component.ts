import { Component } from '@angular/core';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';

/**
 * Unidades de medida.
 *
 * Deben corresponder al catalogo numero 03 de SUNAT: el codigo viaja en
 * cada linea del comprobante y un valor inventado provoca rechazo
 * (DTE seccion 5.1).
 */
@Component({
  selector: 'app-unidades',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent, ConfirmacionComponent],
  templateUrl: './unidades.component.html',
})
export class UnidadesComponent {
  columnas: ColumnaTabla[] = [
    { campo: 'codigoSunat', titulo: 'Código SUNAT', ordenable: true, ancho: 'w-36' },
    { campo: 'nombre', titulo: 'Nombre', ordenable: true },
    { campo: 'abreviatura', titulo: 'Abreviatura', ancho: 'w-32' },
    { campo: 'productos', titulo: 'Productos', formato: 'cantidad', ancho: 'w-28' },
  ];

  registros = [
    { id: 1, codigoSunat: 'NIU', nombre: 'Unidad', abreviatura: 'UND', productos: 312 },
    { id: 2, codigoSunat: 'BG', nombre: 'Bolsa', abreviatura: 'BOL', productos: 48 },
    { id: 3, codigoSunat: 'KGM', nombre: 'Kilogramo', abreviatura: 'KG', productos: 96 },
    { id: 4, codigoSunat: 'MTR', nombre: 'Metro', abreviatura: 'M', productos: 27 },
    { id: 5, codigoSunat: 'BX', nombre: 'Caja', abreviatura: 'CJA', productos: 63 },
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
