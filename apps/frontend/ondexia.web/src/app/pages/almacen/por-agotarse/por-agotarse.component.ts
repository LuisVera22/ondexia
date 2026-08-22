import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { DesplegableComponent, OpcionDesplegable } from '../../../shared/components/comunes/desplegable/desplegable.component';

interface ProductoPorAgotarse extends Record<string, unknown> {
  id: number;
  codigo: string;
  nombre: string;
  almacen: string;
  stock: number;
  stockMinimo: number;
  cobertura: string;
}

/**
 * Productos por agotarse.
 *
 * No es una entidad sino una vista derivada: compara las existencias contra
 * el stock mínimo configurado en cada producto (DTE §5.3). Construirla como
 * tabla propia duplicaría estado que se desincroniza.
 *
 * Va al final del plan porque solo tiene sentido cuando ya existe
 * movimiento de existencias que la alimente.
 */
@Component({
  selector: 'app-por-agotarse',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent, FormsModule, RouterModule, DesplegableComponent],
  templateUrl: './por-agotarse.component.html',
})
export class PorAgotarseComponent {
  get opcionesAlmacen(): OpcionDesplegable[] {
    return [
      { valor: '', etiqueta: 'Todos' },
      ...this.almacenes.map((a) => ({ valor: a, etiqueta: a })),
    ];
  }

  almacenFiltro = '';
  soloAgotados = false;

  almacenes = ['Almacén Principal', 'Tienda Miraflores', 'Depósito Ate'];

  columnas: ColumnaTabla[] = [
    { campo: 'codigo', titulo: 'Código', ordenable: true, ancho: 'w-32' },
    { campo: 'nombre', titulo: 'Producto', ordenable: true, principal: true },
    { campo: 'almacen', titulo: 'Almacén', ordenable: true, ancho: 'w-44' },
    { campo: 'stock', titulo: 'Existencias', formato: 'cantidad', ordenable: true, ancho: 'w-32' },
    { campo: 'stockMinimo', titulo: 'Mínimo', formato: 'cantidad', ancho: 'w-28' },
    { campo: 'cobertura', titulo: 'Cobertura', ancho: 'w-32' },
  ];

  private readonly todos: ProductoPorAgotarse[] = [
    { id: 6, codigo: 'ALA-016', nombre: 'Alambre negro nº 16', almacen: 'Almacén Principal', stock: 0, stockMinimo: 20, cobertura: 'Agotado' },
    { id: 4, codigo: 'FIE-038', nombre: 'Fierro corrugado 3/8" x 9 m', almacen: 'Almacén Principal', stock: 8, stockMinimo: 50, cobertura: '2 días' },
    { id: 2, codigo: 'CEM-005', nombre: 'Cemento Portland Tipo V 42.5 kg', almacen: 'Tienda Miraflores', stock: 12, stockMinimo: 30, cobertura: '5 días' },
    { id: 7, codigo: 'CLA-025', nombre: 'Clavo 2 1/2" con cabeza', almacen: 'Depósito Ate', stock: 18, stockMinimo: 20, cobertura: '9 días' },
  ];

  get registros(): ProductoPorAgotarse[] {
    return this.todos.filter((p) => {
      const coincideAlmacen = !this.almacenFiltro || p.almacen === this.almacenFiltro;
      const coincideAgotado = !this.soloAgotados || p.stock === 0;
      return coincideAlmacen && coincideAgotado;
    });
  }

  get hayFiltrosActivos(): boolean {
    return Boolean(this.almacenFiltro || this.soloAgotados);
  }

  get totalAgotados(): number {
    return this.todos.filter((p) => p.stock === 0).length;
  }
}
