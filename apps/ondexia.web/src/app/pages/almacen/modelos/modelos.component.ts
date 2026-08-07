import { Component } from '@angular/core';
import { PageBreadcrumbComponent } from '../../../shared/components/common/page-breadcrumb/page-breadcrumb.component';
import { TablaDatosComponent, ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';

/**
 * Modelos, subordinados a una marca.
 *
 * Catalogo interno, como las marcas: no viaja al comprobante.
 */
@Component({
  selector: 'app-modelos',
  imports: [PageBreadcrumbComponent, TablaDatosComponent, ConfirmacionComponent],
  templateUrl: './modelos.component.html',
})
export class ModelosComponent {
  columnas: ColumnaTabla[] = [
    { campo: 'nombre', titulo: 'Modelo', ordenable: true },
    { campo: 'marca', titulo: 'Marca', ordenable: true },
    { campo: 'productos', titulo: 'Productos', formato: 'cantidad', ancho: 'w-28' },
    { campo: 'estado', titulo: 'Estado', ancho: 'w-28' },
  ];

  registros = [
    { id: 1, nombre: 'Portland Tipo I', marca: 'Pacasmayo', productos: 8, estado: 'Activo' },
    { id: 2, nombre: 'Portland Tipo V', marca: 'Pacasmayo', productos: 5, estado: 'Activo' },
    { id: 3, nombre: 'Corrugado ASTM A615', marca: 'Aceros Arequipa', productos: 24, estado: 'Activo' },
    { id: 4, nombre: 'Alambre negro', marca: 'Aceros Arequipa', productos: 6, estado: 'Activo' },
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
