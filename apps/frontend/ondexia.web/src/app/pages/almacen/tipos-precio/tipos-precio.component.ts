import { Component } from '@angular/core';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, AccionDeFila,
  ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';

/**
 * Listas de precio por segmento de cliente.
 *
 * Cada producto puede tener un precio distinto por tipo. Al emitir, el
 * tipo del cliente determina cual se aplica por defecto.
 */
@Component({
  selector: 'app-tipos-precio',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent, ConfirmacionComponent],
  templateUrl: './tipos-precio.component.html',
})
export class TiposPrecioComponent {
  readonly accionesDeFila: AccionDeFila[] = [
    { id: 'editar', etiqueta: 'Editar', icono: 'editar' },
    { id: 'eliminar', etiqueta: 'Eliminar', icono: 'eliminar', peligrosa: true },
  ];

  columnas: ColumnaTabla[] = [
    { campo: 'orden', titulo: 'Orden', ordenable: true, formato: 'cantidad', ancho: 'w-24' },
    { campo: 'nombre', titulo: 'Tipo de precio', ordenable: true },
    { campo: 'descripcion', titulo: 'Descripción' },
    { campo: 'predeterminado', titulo: 'Predeterminado', ancho: 'w-36' },
  ];

  registros = [
    { id: 1, orden: 1, nombre: 'Público', descripcion: 'Venta al cliente final', predeterminado: 'Sí' },
    { id: 2, orden: 2, nombre: 'Mayorista', descripcion: 'Compras por volumen', predeterminado: 'No' },
    { id: 3, orden: 3, nombre: 'Distribuidor', descripcion: 'Canal de reventa', predeterminado: 'No' },
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
