import { Component } from '@angular/core';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';

/** Acciones base, disponibles en todo submódulo. */
type AccionBase = 'consultar' | 'editar' | 'eliminar';

/** Acciones que solo existen donde el dominio las admite. */
interface AccionEspecial {
  clave: string;
  nombre: string;
  concedida: boolean;
}

interface SubmoduloPermisos {
  clave: string;
  nombre: string;
  consultar: boolean;
  editar: boolean;
  eliminar: boolean;
  /** Submódulos derivados, como «Productos por agotarse», solo se consultan. */
  soloConsulta?: boolean;
  especiales?: AccionEspecial[];
}

interface ModuloPermisos {
  nombre: string;
  abierto: boolean;
  submodulos: SubmoduloPermisos[];
}

interface Rol {
  id: number;
  nombre: string;
  descripcion: string;
  usuarios: number;
  fijo: boolean;
}

const sub = (
  clave: string,
  nombre: string,
  extra: Partial<SubmoduloPermisos> = {}
): SubmoduloPermisos => ({
  clave,
  nombre,
  consultar: false,
  editar: false,
  eliminar: false,
  ...extra,
});

/**
 * Roles y permisos.
 *
 * El permiso es por **submódulo** y por acción, no por módulo: quien
 * gestiona productos no necesariamente gestiona guías de remisión, y quien
 * registra una compra no necesariamente aprueba la orden.
 *
 * Las acciones especiales solo aparecen donde el dominio las admite —emitir
 * y anular en comprobantes, aprobar en órdenes— porque un permiso que no
 * significa nada en su contexto solo agrega ruido a una matriz que ya es
 * grande.
 */
@Component({
  selector: 'app-roles',
  imports: [EncabezadoPaginaComponent],
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

  modulos: ModuloPermisos[] = [
    {
      nombre: 'Almacén',
      abierto: true,
      submodulos: [
        sub('almacen.productos', 'Productos', { consultar: true }),
        sub('almacen.presentaciones', 'Presentaciones', { consultar: true }),
        sub('almacen.por-agotarse', 'Productos por agotarse', { consultar: true, soloConsulta: true }),
        sub('almacen.guias-remision', 'Guías de remisión', {
          especiales: [
            { clave: 'emitir', nombre: 'Emitir', concedida: false },
            { clave: 'anular', nombre: 'Anular', concedida: false },
          ],
        }),
        sub('almacen.guias-ingreso', 'Guías de ingreso'),
        sub('almacen.tipos-precio', 'Tipos de precio', { consultar: true }),
        sub('almacen.marcas', 'Marcas', { consultar: true }),
        sub('almacen.modelos', 'Modelos', { consultar: true }),
        sub('almacen.unidades', 'Unidades', { consultar: true }),
        sub('almacen.almacenes', 'Almacenes', { consultar: true }),
      ],
    },
    {
      nombre: 'Compras',
      abierto: false,
      submodulos: [
        sub('compras.proveedores', 'Proveedores', { consultar: true }),
        sub('compras.notas-pedido', 'Notas de pedido'),
        sub('compras.ordenes-compra', 'Órdenes de compra', {
          especiales: [{ clave: 'aprobar', nombre: 'Aprobar', concedida: false }],
        }),
        sub('compras.ordenes-servicio', 'Órdenes de servicio', {
          especiales: [{ clave: 'aprobar', nombre: 'Aprobar', concedida: false }],
        }),
        sub('compras.notas-compra', 'Notas de compra'),
        sub('compras.facturas', 'Facturas de compra'),
        sub('compras.liquidaciones', 'Liquidaciones de compra', {
          especiales: [{ clave: 'emitir', nombre: 'Emitir', concedida: false }],
        }),
      ],
    },
    {
      nombre: 'Ventas',
      abierto: false,
      submodulos: [
        sub('ventas.clientes', 'Clientes', { consultar: true, editar: true }),
        sub('ventas.cotizaciones', 'Cotizaciones', { consultar: true, editar: true }),
        sub('ventas.preventas', 'Notas de preventa', { consultar: true, editar: true }),
        sub('ventas.facturas', 'Facturas', {
          consultar: true,
          editar: true,
          especiales: [
            { clave: 'emitir', nombre: 'Emitir', concedida: true },
            { clave: 'anular', nombre: 'Anular', concedida: false },
          ],
        }),
        sub('ventas.boletas', 'Boletas', {
          consultar: true,
          editar: true,
          especiales: [
            { clave: 'emitir', nombre: 'Emitir', concedida: true },
            { clave: 'anular', nombre: 'Anular', concedida: false },
          ],
        }),
        sub('ventas.notas-credito', 'Notas de crédito', {
          consultar: true,
          especiales: [{ clave: 'emitir', nombre: 'Emitir', concedida: false }],
        }),
        sub('ventas.comunicacion-baja', 'Comunicación de baja', {
          consultar: true,
          especiales: [{ clave: 'emitir', nombre: 'Comunicar', concedida: false }],
        }),
        sub('ventas.resumen-diario', 'Resumen diario', {
          consultar: true,
          especiales: [{ clave: 'emitir', nombre: 'Enviar', concedida: false }],
        }),
        sub('ventas.formas-pago', 'Formas de pago', { consultar: true }),
      ],
    },
    {
      nombre: 'Configuración',
      abierto: false,
      submodulos: [
        sub('config.empresa', 'Empresa'),
        sub('config.identidad', 'Identidad visual'),
        sub('config.establecimientos', 'Establecimientos'),
        sub('config.series', 'Series y correlativos'),
        sub('config.usuarios', 'Usuarios'),
        sub('config.roles', 'Roles y permisos'),
        sub('config.comprobantes', 'Comprobantes'),
        sub('config.suscripcion', 'Suscripción'),
      ],
    },
  ];

  get esRolFijo(): boolean {
    return this.rolSeleccionado.fijo;
  }

  seleccionar(rol: Rol): void {
    this.rolSeleccionado = rol;
  }

  alternarModulo(modulo: ModuloPermisos): void {
    modulo.abierto = !modulo.abierto;
  }

  /**
   * Alterna una acción base respetando la dependencia entre permisos:
   * no se puede editar ni eliminar sin poder consultar. Al conceder editar
   * se concede consultar; al quitar consultar se quita todo lo demás.
   */
  alternar(submodulo: SubmoduloPermisos, accion: AccionBase): void {
    if (this.esRolFijo || (submodulo.soloConsulta && accion !== 'consultar')) {
      return;
    }

    submodulo[accion] = !submodulo[accion];

    if (accion === 'consultar' && !submodulo.consultar) {
      submodulo.editar = false;
      submodulo.eliminar = false;
      (submodulo.especiales ?? []).forEach((e) => (e.concedida = false));
    } else if (accion !== 'consultar' && submodulo[accion]) {
      submodulo.consultar = true;
    }
  }

  alternarEspecial(submodulo: SubmoduloPermisos, especial: AccionEspecial): void {
    if (this.esRolFijo) {
      return;
    }
    especial.concedida = !especial.concedida;
    if (especial.concedida) {
      submodulo.consultar = true;
    }
  }

  /** Concede o quita una acción en todo el módulo de una vez. */
  alternarColumna(modulo: ModuloPermisos, accion: AccionBase): void {
    if (this.esRolFijo) {
      return;
    }
    const conceder = !this.columnaCompleta(modulo, accion);
    modulo.submodulos.forEach((s) => {
      if (s.soloConsulta && accion !== 'consultar') {
        return;
      }
      s[accion] = conceder;
      if (conceder && accion !== 'consultar') {
        s.consultar = true;
      }
      if (!conceder && accion === 'consultar') {
        s.editar = false;
        s.eliminar = false;
        (s.especiales ?? []).forEach((e) => (e.concedida = false));
      }
    });
  }

  columnaCompleta(modulo: ModuloPermisos, accion: AccionBase): boolean {
    const aplicables = modulo.submodulos.filter((s) => !(s.soloConsulta && accion !== 'consultar'));
    return aplicables.length > 0 && aplicables.every((s) => s[accion]);
  }

  concedidosEnModulo(modulo: ModuloPermisos): number {
    return modulo.submodulos.reduce((total, s) => {
      const base = [s.consultar, s.editar, s.eliminar].filter(Boolean).length;
      const especiales = (s.especiales ?? []).filter((e) => e.concedida).length;
      return total + base + especiales;
    }, 0);
  }

  totalEnModulo(modulo: ModuloPermisos): number {
    return modulo.submodulos.reduce((total, s) => {
      const base = s.soloConsulta ? 1 : 3;
      return total + base + (s.especiales ?? []).length;
    }, 0);
  }

  get totalConcedidos(): number {
    return this.modulos.reduce((t, m) => t + this.concedidosEnModulo(m), 0);
  }
}
