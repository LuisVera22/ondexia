import { Component } from '@angular/core';
import { PageBreadcrumbComponent } from '../../../shared/components/common/page-breadcrumb/page-breadcrumb.component';

interface PermisoModulo {
  modulo: string;
  acciones: { clave: string; nombre: string; concedido: boolean }[];
}

interface Rol {
  id: number;
  nombre: string;
  descripcion: string;
  usuarios: number;
  fijo: boolean;
}

/**
 * Roles y permisos.
 *
 * El permiso es por módulo y por acción, no un rol global: quien registra
 * una compra no necesariamente aprueba la orden, y quien emite un
 * comprobante no necesariamente puede anularlo (DTE §8.1).
 */
@Component({
  selector: 'app-roles',
  imports: [PageBreadcrumbComponent],
  templateUrl: './roles.component.html',
})
export class RolesComponent {
  roles: Rol[] = [
    { id: 1, nombre: 'Administrador', descripcion: 'Acceso total, incluida la configuración', usuarios: 1, fijo: true },
    { id: 2, nombre: 'Ventas', descripcion: 'Emite comprobantes y gestiona clientes', usuarios: 1, fijo: true },
    { id: 3, nombre: 'Almacén', descripcion: 'Gestiona productos y existencias', usuarios: 0, fijo: true },
    { id: 4, nombre: 'Solo lectura', descripcion: 'Consulta sin modificar', usuarios: 0, fijo: true },
  ];

  rolSeleccionado: Rol = this.roles[1];

  permisos: PermisoModulo[] = [
    {
      modulo: 'Almacén',
      acciones: [
        { clave: 'almacen.ver', nombre: 'Consultar', concedido: true },
        { clave: 'almacen.registrar', nombre: 'Registrar y editar', concedido: false },
        { clave: 'almacen.eliminar', nombre: 'Eliminar', concedido: false },
      ],
    },
    {
      modulo: 'Compras',
      acciones: [
        { clave: 'compras.ver', nombre: 'Consultar', concedido: true },
        { clave: 'compras.registrar', nombre: 'Registrar', concedido: false },
        { clave: 'compras.aprobar', nombre: 'Aprobar órdenes', concedido: false },
      ],
    },
    {
      modulo: 'Ventas',
      acciones: [
        { clave: 'ventas.ver', nombre: 'Consultar', concedido: true },
        { clave: 'ventas.registrar', nombre: 'Registrar y cotizar', concedido: true },
        { clave: 'ventas.emitir', nombre: 'Emitir comprobantes', concedido: true },
        { clave: 'ventas.anular', nombre: 'Anular comprobantes', concedido: false },
      ],
    },
    {
      modulo: 'Configuración',
      acciones: [
        { clave: 'config.ver', nombre: 'Consultar', concedido: false },
        { clave: 'config.editar', nombre: 'Modificar', concedido: false },
      ],
    },
  ];

  get esRolFijo(): boolean {
    return this.rolSeleccionado.fijo;
  }

  seleccionar(rol: Rol): void {
    this.rolSeleccionado = rol;
  }

  alternar(accion: { concedido: boolean }): void {
    if (this.esRolFijo) {
      return;
    }
    accion.concedido = !accion.concedido;
  }
}
