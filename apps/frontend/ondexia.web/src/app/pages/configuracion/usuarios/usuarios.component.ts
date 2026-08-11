import { Component } from '@angular/core';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';

/**
 * Usuarios con acceso a la cuenta.
 *
 * El limite lo fija el plan contratado y se cuenta por cuenta, no por
 * empresa: el contador que opera dos RUC es una sola persona
 * (documento 04 seccion 2.3).
 */
@Component({
  selector: 'app-usuarios',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent, ConfirmacionComponent],
  templateUrl: './usuarios.component.html',
})
export class UsuariosComponent {
  columnas: ColumnaTabla[] = [
    { campo: 'nombre', titulo: 'Usuario', ordenable: true },
    { campo: 'correo', titulo: 'Correo', ordenable: true },
    { campo: 'rol', titulo: 'Rol', ancho: 'w-40' },
    { campo: 'empresas', titulo: 'Empresas', ancho: 'w-32' },
    { campo: 'estado', titulo: 'Estado', ancho: 'w-28' },
  ];

  registros = [
    { id: 1, nombre: 'Luis Vera', correo: 'luis.vera@wirbi.com', rol: 'Administrador', empresas: 'Todas', estado: 'Activo' },
    { id: 2, nombre: 'Carmen Rojas', correo: 'carmen.rojas@wirbi.com', rol: 'Ventas', empresas: 'Wirbi S.A.C.', estado: 'Activo' },
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
