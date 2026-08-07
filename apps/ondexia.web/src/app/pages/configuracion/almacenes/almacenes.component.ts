import { Component } from '@angular/core';
import { PageBreadcrumbComponent } from '../../../shared/components/common/page-breadcrumb/page-breadcrumb.component';
import { TablaDatosComponent, ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';

/**
 * Almacenes donde se registran las existencias.
 *
 * Al crear un establecimiento se genera automaticamente su almacen
 * principal, de modo que un negocio de un solo local nunca necesita entrar
 * a esta vista (documento 05 seccion 4.2).
 */
@Component({
  selector: 'app-almacenes',
  imports: [PageBreadcrumbComponent, TablaDatosComponent, ConfirmacionComponent],
  templateUrl: './almacenes.component.html',
})
export class AlmacenesComponent {
  columnas: ColumnaTabla[] = [
    { campo: 'nombre', titulo: 'Almacén', ordenable: true },
    { campo: 'establecimiento', titulo: 'Establecimiento', ordenable: true },
    { campo: 'responsable', titulo: 'Responsable' },
    { campo: 'productos', titulo: 'Productos', formato: 'cantidad', ancho: 'w-28' },
  ];

  registros = [
    { id: 1, nombre: 'Almacén Principal', establecimiento: 'Principal', responsable: 'Carmen Rojas', productos: 428 },
    { id: 2, nombre: 'Tienda Miraflores', establecimiento: 'Miraflores', responsable: 'Luis Paredes', productos: 156 },
    { id: 3, nombre: 'Depósito Ate', establecimiento: 'Depósito Ate', responsable: 'Jorge Huamán', productos: 1204 },
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
