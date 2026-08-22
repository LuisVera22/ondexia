import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, AccionDeFila,
  ColumnaTabla, OrdenTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { DesplegableComponent, OpcionDesplegable } from '../../../shared/components/comunes/desplegable/desplegable.component';

/**
 * Listado de productos.
 *
 * Es el catálogo del que dependen ventas, compras y existencias, así que
 * incorpora filtros desde el inicio: sobre unos cientos de productos, una
 * tabla sin filtros deja de ser usable.
 */
@Component({
  selector: 'app-productos',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent, FormsModule, DesplegableComponent],
  templateUrl: './productos.component.html',
})
export class ProductosComponent {
  private readonly router = inject(Router);

  termino = '';
  marcaFiltro = '';
  estadoFiltro = '';

  orden: OrdenTabla | null = { campo: 'nombre', direccion: 'asc' };
  pagina = 1;
  readonly tamanoPagina = 10;

  marcas = ['Pacasmayo', 'Aceros Arequipa', 'Sider Perú', 'Sin marca'];

  get opcionesMarca(): OpcionDesplegable[] {
    return [
      { valor: '', etiqueta: 'Todas' },
      ...this.marcas.map((m) => ({ valor: m, etiqueta: m })),
    ];
  }

  readonly opcionesEstado: OpcionDesplegable[] = [
    { valor: '', etiqueta: 'Todos' },
    { valor: 'Activo', etiqueta: 'Activo' },
    { valor: 'Descontinuado', etiqueta: 'Descontinuado' },
  ];
  readonly accionesDeFila: AccionDeFila[] = [
    { id: 'ver', etiqueta: 'Ver', icono: 'ver' },
  ];


  columnas: ColumnaTabla[] = [
    { campo: 'codigo', titulo: 'Código', ordenable: true, ancho: 'w-32' },
    { campo: 'nombre', titulo: 'Producto', ordenable: true, principal: true },
    { campo: 'marca', titulo: 'Marca', ordenable: true, ancho: 'w-40' },
    { campo: 'unidad', titulo: 'Unidad', ancho: 'w-24' },
    { campo: 'stock', titulo: 'Stock', formato: 'cantidad', ordenable: true, ancho: 'w-28' },
    { campo: 'precio', titulo: 'Precio', formato: 'importe', ordenable: true, ancho: 'w-32' },
  ];

  private readonly todos = [
    { id: 1, codigo: 'CEM-001', nombre: 'Cemento Portland Tipo I 42.5 kg', marca: 'Pacasmayo', unidad: 'BOL', stock: 428, precio: 32.5, estado: 'Activo' },
    { id: 2, codigo: 'CEM-005', nombre: 'Cemento Portland Tipo V 42.5 kg', marca: 'Pacasmayo', unidad: 'BOL', stock: 96, precio: 38.9, estado: 'Activo' },
    { id: 3, codigo: 'FIE-012', nombre: 'Fierro corrugado 1/2" x 9 m', marca: 'Aceros Arequipa', unidad: 'UND', stock: 1204, precio: 48.0, estado: 'Activo' },
    { id: 4, codigo: 'FIE-038', nombre: 'Fierro corrugado 3/8" x 9 m', marca: 'Aceros Arequipa', unidad: 'UND', stock: 8, precio: 27.5, estado: 'Activo' },
    { id: 5, codigo: 'LAD-018', nombre: 'Ladrillo King Kong 18 huecos', marca: 'Sin marca', unidad: 'UND', stock: 15600, precio: 1.2, estado: 'Activo' },
    { id: 6, codigo: 'ALA-016', nombre: 'Alambre negro nº 16', marca: 'Aceros Arequipa', unidad: 'KG', stock: 0, precio: 6.8, estado: 'Activo' },
    { id: 7, codigo: 'CLA-025', nombre: 'Clavo 2 1/2" con cabeza', marca: 'Sider Perú', unidad: 'KG', stock: 240, precio: 5.9, estado: 'Activo' },
    { id: 8, codigo: 'ARE-001', nombre: 'Arena gruesa', marca: 'Sin marca', unidad: 'M3', stock: 62, precio: 45.0, estado: 'Descontinuado' },
  ];

  get registros() {
    return this.todos.filter((p) => {
      const coincideTermino =
        !this.termino ||
        p.nombre.toLowerCase().includes(this.termino.toLowerCase()) ||
        p.codigo.toLowerCase().includes(this.termino.toLowerCase());
      const coincideMarca = !this.marcaFiltro || p.marca === this.marcaFiltro;
      const coincideEstado = !this.estadoFiltro || p.estado === this.estadoFiltro;
      return coincideTermino && coincideMarca && coincideEstado;
    });
  }

  get hayFiltrosActivos(): boolean {
    return Boolean(this.termino || this.marcaFiltro || this.estadoFiltro);
  }

  limpiarFiltros(): void {
    this.termino = '';
    this.marcaFiltro = '';
    this.estadoFiltro = '';
  }

  abrirFicha(registro: Record<string, unknown>): void {
    this.router.navigate(['/almacen/productos', registro['id']]);
  }

  nuevo(): void {
    this.router.navigate(['/almacen/productos', 'nuevo']);
  }

  ejecutarAccion(evento: { accion: string; registro: Record<string, unknown> }): void {
    if (evento.accion === 'ver') {
      this.abrirFicha(evento.registro);
    }
  }
}
