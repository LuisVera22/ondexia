import { Component } from '@angular/core';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, AccionDeFila,
  ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';

/**
 * Formas de pago.
 *
 * Es un catalogo del editor de documento: determina si la venta es al
 * contado o genera cuenta por cobrar, y con cuantos dias de vencimiento.
 */
@Component({
  selector: 'app-formas-pago',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent, ConfirmacionComponent],
  templateUrl: './formas-pago.component.html',
})
export class FormasPagoComponent {
  readonly accionesDeFila: AccionDeFila[] = [
    { id: 'editar', etiqueta: 'Editar', icono: 'editar' },
    { id: 'eliminar', etiqueta: 'Eliminar', icono: 'eliminar', peligrosa: true },
  ];

  columnas: ColumnaTabla[] = [
    { campo: 'nombre', titulo: 'Forma de pago', ordenable: true, principal: true },
    { campo: 'condicion', titulo: 'Condición', ancho: 'w-32' },
    { campo: 'dias', titulo: 'Días', formato: 'cantidad', ancho: 'w-24' },
    { campo: 'predeterminada', titulo: 'Predeterminada', ancho: 'w-40' },
  ];

  registros = [
    { id: 1, nombre: 'Efectivo', condicion: 'Contado', dias: 0, predeterminada: 'Sí' },
    { id: 2, nombre: 'Transferencia bancaria', condicion: 'Contado', dias: 0, predeterminada: 'No' },
    { id: 3, nombre: 'Tarjeta de crédito', condicion: 'Contado', dias: 0, predeterminada: 'No' },
    { id: 4, nombre: 'Crédito 15 días', condicion: 'Crédito', dias: 15, predeterminada: 'No' },
    { id: 5, nombre: 'Crédito 30 días', condicion: 'Crédito', dias: 30, predeterminada: 'No' },
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
