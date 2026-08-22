import { Component } from '@angular/core';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, AccionDeFila,
  ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';

/**
 * Presentaciones o formas de empaque de un producto.
 *
 * El factor de conversion expresa cuantas unidades base contiene la
 * presentacion. La presentacion base tiene factor 1 y es la que sostiene
 * el control de existencias.
 */
@Component({
  selector: 'app-presentaciones',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent, ConfirmacionComponent],
  templateUrl: './presentaciones.component.html',
})
export class PresentacionesComponent {
  readonly accionesDeFila: AccionDeFila[] = [
    { id: 'editar', etiqueta: 'Editar', icono: 'editar' },
    { id: 'eliminar', etiqueta: 'Eliminar', icono: 'eliminar', peligrosa: true },
  ];

  columnas: ColumnaTabla[] = [
    { campo: 'producto', titulo: 'Producto', ordenable: true },
    { campo: 'descripcion', titulo: 'Presentación', ordenable: true, principal: true },
    { campo: 'factor', titulo: 'Factor', formato: 'cantidad', ancho: 'w-28' },
    { campo: 'codigoBarras', titulo: 'Código de barras', ancho: 'w-44' },
    { campo: 'base', titulo: 'Base', ancho: 'w-24' },
  ];

  registros = [
    { id: 1, producto: 'Cemento Portland Tipo I', descripcion: 'Bolsa 42.5 kg', factor: 1, codigoBarras: '7750001000012', base: 'Sí' },
    { id: 2, producto: 'Cemento Portland Tipo I', descripcion: 'Pallet 50 bolsas', factor: 50, codigoBarras: '7750001000029', base: 'No' },
    { id: 3, producto: 'Ladrillo King Kong', descripcion: 'Unidad', factor: 1, codigoBarras: '7750002000015', base: 'Sí' },
    { id: 4, producto: 'Ladrillo King Kong', descripcion: 'Millar', factor: 1000, codigoBarras: '', base: 'No' },
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

  ejecutarAccion(evento: { accion: string; registro: Record<string, unknown> }): void {
    if (evento.accion === 'eliminar') {
      this.pedirEliminacion(evento.registro);
    }
    // «Editar» todavia no hace nada: esta pantalla trabaja con datos de ejemplo.
  }
}
