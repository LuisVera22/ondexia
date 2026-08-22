import { Component } from '@angular/core';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, AccionDeFila,
  ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';

/**
 * Marcas de los productos.
 *
 * Catalogo interno: no viaja al comprobante electronico. Sirve para
 * organizar el inventario y filtrar los listados.
 */
@Component({
  selector: 'app-marcas',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent, ConfirmacionComponent],
  templateUrl: './marcas.component.html',
})
export class MarcasComponent {
  readonly accionesDeFila: AccionDeFila[] = [
    { id: 'editar', etiqueta: 'Editar', icono: 'editar' },
    { id: 'eliminar', etiqueta: 'Eliminar', icono: 'eliminar', peligrosa: true },
  ];

  columnas: ColumnaTabla[] = [
    { campo: 'nombre', titulo: 'Marca', ordenable: true, principal: true },
    { campo: 'modelos', titulo: 'Modelos', formato: 'cantidad', ancho: 'w-28' },
    { campo: 'productos', titulo: 'Productos', formato: 'cantidad', ancho: 'w-28' },
    {
      campo: 'estado',
      titulo: 'Estado',
      ancho: 'w-32',
      formato: 'insignia',
      tono: (registro) => (registro['estado'] === 'Activa' ? 'exito' : 'neutro'),
    },
  ];

  registros = [
    { id: 1, nombre: 'Pacasmayo', modelos: 6, productos: 42, estado: 'Activa' },
    { id: 2, nombre: 'Aceros Arequipa', modelos: 11, productos: 87, estado: 'Activa' },
    { id: 3, nombre: 'Sider Perú', modelos: 4, productos: 23, estado: 'Activa' },
    { id: 4, nombre: 'Sin marca', modelos: 0, productos: 156, estado: 'Activa' },
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
