import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { PageBreadcrumbComponent } from '../../../shared/components/common/page-breadcrumb/page-breadcrumb.component';

type Pestana = 'general' | 'presentaciones' | 'precios' | 'existencias';

/**
 * Ficha de producto.
 *
 * Es la vista más densa de la Etapa 2: reúne datos generales,
 * presentaciones, precios por tipo y existencias por almacén. Se resuelve
 * con pestañas porque en una sola columna el formulario obligaría a
 * desplazarse varias pantallas para encontrar un dato.
 */
@Component({
  selector: 'app-ficha-producto',
  imports: [PageBreadcrumbComponent, ReactiveFormsModule, RouterModule],
  templateUrl: './ficha-producto.component.html',
})
export class FichaProductoComponent {
  private readonly ruta = inject(ActivatedRoute);
  private readonly constructorFormulario = inject(FormBuilder);

  pestana: Pestana = 'general';
  esNuevo = false;

  formulario = this.constructorFormulario.nonNullable.group({
    codigo: ['CEM-001', [Validators.required]],
    nombre: ['Cemento Portland Tipo I 42.5 kg', [Validators.required]],
    descripcion: [''],
    marca: ['Pacasmayo'],
    modelo: ['Portland Tipo I'],
    unidad: ['BOL', [Validators.required]],
    afectacionIgv: ['GRAVADO', [Validators.required]],
    stockMinimo: [50],
    estado: ['Activo'],
  });

  presentaciones = [
    { id: 1, descripcion: 'Bolsa 42.5 kg', factor: 1, codigoBarras: '7750001000012', base: true },
    { id: 2, descripcion: 'Pallet 50 bolsas', factor: 50, codigoBarras: '7750001000029', base: false },
  ];

  precios = [
    { id: 1, tipo: 'Público', presentacion: 'Bolsa 42.5 kg', valor: 32.5, moneda: 'PEN' },
    { id: 2, tipo: 'Mayorista', presentacion: 'Bolsa 42.5 kg', valor: 30.2, moneda: 'PEN' },
    { id: 3, tipo: 'Distribuidor', presentacion: 'Pallet 50 bolsas', valor: 1420.0, moneda: 'PEN' },
  ];

  existencias = [
    { id: 1, almacen: 'Almacén Principal', establecimiento: 'Principal', cantidad: 312 },
    { id: 2, almacen: 'Tienda Miraflores', establecimiento: 'Miraflores', cantidad: 84 },
    { id: 3, almacen: 'Depósito Ate', establecimiento: 'Depósito Ate', cantidad: 32 },
  ];

  ngOnInit(): void {
    this.esNuevo = this.ruta.snapshot.paramMap.get('id') === 'nuevo';
    if (this.esNuevo) {
      this.formulario.reset({
        codigo: '',
        nombre: '',
        descripcion: '',
        marca: '',
        modelo: '',
        unidad: '',
        afectacionIgv: 'GRAVADO',
        stockMinimo: 0,
        estado: 'Activo',
      });
      this.presentaciones = [];
      this.precios = [];
      this.existencias = [];
    }
  }

  get titulo(): string {
    return this.esNuevo ? 'Nuevo producto' : this.formulario.controls.nombre.value;
  }

  get stockTotal(): number {
    return this.existencias.reduce((suma, e) => suma + e.cantidad, 0);
  }

  get bajoMinimo(): boolean {
    return !this.esNuevo && this.stockTotal <= this.formulario.controls.stockMinimo.value;
  }

  formatoImporte(valor: number): string {
    return valor.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}
