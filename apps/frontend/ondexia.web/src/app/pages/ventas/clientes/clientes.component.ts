import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, AccionDeFila,
  ColumnaTabla, OrdenTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { DesplegableComponent, OpcionDesplegable } from '../../../shared/components/comunes/desplegable/desplegable.component';

/**
 * Listado de clientes.
 *
 * El tipo de documento determina qué comprobante se le puede emitir: para
 * factura se exige RUC, y con DNI solo procede boleta. Por eso la columna
 * de documento es la primera dato relevante del listado.
 */
@Component({
  selector: 'app-clientes',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent, FormsModule, DesplegableComponent],
  templateUrl: './clientes.component.html',
})
export class ClientesComponent {
  readonly opcionesTipo: OpcionDesplegable[] = [
    { valor: '', etiqueta: 'Todos' },
    { valor: 'RUC', etiqueta: 'RUC' },
    { valor: 'DNI', etiqueta: 'DNI' },
  ];

  private readonly router = inject(Router);

  termino = '';
  tipoFiltro = '';

  orden: OrdenTabla | null = { campo: 'razonSocial', direccion: 'asc' };
  pagina = 1;
  readonly tamanoPagina = 10;
  readonly accionesDeFila: AccionDeFila[] = [
    { id: 'ver', etiqueta: 'Ver', icono: 'ver' },
  ];


  columnas: ColumnaTabla[] = [
    { campo: 'tipoDocumento', titulo: 'Tipo', ancho: 'w-24' },
    { campo: 'numeroDocumento', titulo: 'Documento', ordenable: true, ancho: 'w-36' },
    { campo: 'razonSocial', titulo: 'Cliente', ordenable: true },
    { campo: 'tipoPrecio', titulo: 'Tipo de precio', ancho: 'w-36' },
    { campo: 'correo', titulo: 'Correo' },
  ];

  private readonly todos = [
    { id: 1, tipoDocumento: 'RUC', numeroDocumento: '20512345678', razonSocial: 'Distribuidora Andina S.A.C.', tipoPrecio: 'Mayorista', correo: 'compras@andina.com' },
    { id: 2, tipoDocumento: 'RUC', numeroDocumento: '20587654321', razonSocial: 'Comercial El Sol E.I.R.L.', tipoPrecio: 'Distribuidor', correo: 'ventas@elsol.pe' },
    { id: 3, tipoDocumento: 'DNI', numeroDocumento: '45678912', razonSocial: 'Rosa Quispe Mamani', tipoPrecio: 'Público', correo: '' },
    { id: 4, tipoDocumento: 'DNI', numeroDocumento: '09876543', razonSocial: 'Carlos Mendoza Ríos', tipoPrecio: 'Público', correo: 'cmendoza@correo.com' },
    { id: 5, tipoDocumento: 'RUC', numeroDocumento: '20456789123', razonSocial: 'Constructora Pacífico S.A.', tipoPrecio: 'Mayorista', correo: 'logistica@pacifico.com.pe' },
  ];

  get registros() {
    return this.todos.filter((c) => {
      const coincideTermino =
        !this.termino ||
        c.razonSocial.toLowerCase().includes(this.termino.toLowerCase()) ||
        c.numeroDocumento.includes(this.termino);
      const coincideTipo = !this.tipoFiltro || c.tipoDocumento === this.tipoFiltro;
      return coincideTermino && coincideTipo;
    });
  }

  get hayFiltrosActivos(): boolean {
    return Boolean(this.termino || this.tipoFiltro);
  }

  limpiarFiltros(): void {
    this.termino = '';
    this.tipoFiltro = '';
  }

  abrirFicha(registro: Record<string, unknown>): void {
    this.router.navigate(['/ventas/clientes', registro['id']]);
  }

  nuevo(): void {
    this.router.navigate(['/ventas/clientes', 'nuevo']);
  }

  ejecutarAccion(evento: { accion: string; registro: Record<string, unknown> }): void {
    if (evento.accion === 'ver') {
      this.abrirFicha(evento.registro);
    }
  }
}
