import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, ColumnaTabla, OrdenTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';

/**
 * Listado de proveedores.
 *
 * Se mantiene separado de clientes pese a compartir forma: sus ciclos de
 * vida, validaciones y permisos difieren, y un mismo RUC puede ser ambos
 * con datos distintos (DTE §5.4).
 *
 * La condición de pago se muestra en el listado porque determina si la
 * compra genera cuenta por pagar.
 */
@Component({
  selector: 'app-proveedores',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent, FormsModule],
  templateUrl: './proveedores.component.html',
})
export class ProveedoresComponent {
  private readonly router = inject(Router);

  termino = '';
  orden: OrdenTabla | null = { campo: 'razonSocial', direccion: 'asc' };
  pagina = 1;
  readonly tamanoPagina = 10;

  columnas: ColumnaTabla[] = [
    { campo: 'numeroDocumento', titulo: 'RUC', ordenable: true, ancho: 'w-36' },
    { campo: 'razonSocial', titulo: 'Proveedor', ordenable: true },
    { campo: 'contacto', titulo: 'Contacto' },
    { campo: 'condicionPago', titulo: 'Condición de pago', ancho: 'w-40' },
    { campo: 'estado', titulo: 'Estado', ancho: 'w-28' },
  ];

  private readonly todos = [
    { id: 1, numeroDocumento: '20100113610', razonSocial: 'Cementos Pacasmayo S.A.A.', contacto: 'Ana Torres', condicionPago: 'Crédito 30 días', estado: 'Activo' },
    { id: 2, numeroDocumento: '20100136741', razonSocial: 'Corporación Aceros Arequipa S.A.', contacto: 'Miguel Salas', condicionPago: 'Crédito 45 días', estado: 'Activo' },
    { id: 3, numeroDocumento: '20524089107', razonSocial: 'Ferretería Central S.A.C.', contacto: 'Julia Ramos', condicionPago: 'Contado', estado: 'Activo' },
    { id: 4, numeroDocumento: '20601234567', razonSocial: 'Transportes del Norte E.I.R.L.', contacto: 'Pedro Chávez', condicionPago: 'Contado', estado: 'Inactivo' },
  ];

  get registros() {
    return this.todos.filter(
      (p) =>
        !this.termino ||
        p.razonSocial.toLowerCase().includes(this.termino.toLowerCase()) ||
        p.numeroDocumento.includes(this.termino)
    );
  }

  get hayFiltrosActivos(): boolean {
    return Boolean(this.termino);
  }

  abrirFicha(registro: Record<string, unknown>): void {
    this.router.navigate(['/compras/proveedores', registro['id']]);
  }

  nuevo(): void {
    this.router.navigate(['/compras/proveedores', 'nuevo']);
  }
}
