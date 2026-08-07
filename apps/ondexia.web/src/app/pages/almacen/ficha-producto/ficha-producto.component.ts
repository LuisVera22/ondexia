import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { PageBreadcrumbComponent } from '../../../shared/components/common/page-breadcrumb/page-breadcrumb.component';
import { KardexComponent, MovimientoKardex } from '../../../shared/components/comunes/kardex/kardex.component';

type Pestana = 'general' | 'presentaciones' | 'precios' | 'existencias' | 'kardex';

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
  imports: [PageBreadcrumbComponent, ReactiveFormsModule, RouterModule, KardexComponent],
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

  /**
   * Movimientos que alimentan el kardex. Van en orden cronológico: el saldo
   * es acumulado y reordenarlos cambiaría los costos calculados.
   */
  movimientos: MovimientoKardex[] = [
    { fecha: '01/07/2026', codigoOperacion: '01', documento: 'Inventario inicial', almacen: 'Almacén Principal', tipo: 'entrada', cantidad: 200, costoUnitario: 26.9 },
    { fecha: '08/07/2026', codigoOperacion: '02', documento: 'GI-000331', almacen: 'Almacén Principal', tipo: 'entrada', cantidad: 500, costoUnitario: 27.4 },
    { fecha: '12/07/2026', codigoOperacion: '10', documento: 'F001-000098', almacen: 'Almacén Principal', tipo: 'salida', cantidad: 120 },
    { fecha: '15/07/2026', codigoOperacion: '16', documento: 'T001-000870', almacen: 'Almacén Principal', tipo: 'salida', cantidad: 80 },
    // Entra al mismo costo con que salió: un traslado mueve existencias, no las revaloriza.
    { fecha: '15/07/2026', codigoOperacion: '05', documento: 'T001-000870', almacen: 'Tienda Miraflores', tipo: 'entrada', cantidad: 80, costoTotal: 2180.57 },
    { fecha: '22/07/2026', codigoOperacion: '02', documento: 'GI-000339', almacen: 'Almacén Principal', tipo: 'entrada', cantidad: 300, costoUnitario: 28.1 },
    { fecha: '28/07/2026', codigoOperacion: '10', documento: 'F001-000114', almacen: 'Almacén Principal', tipo: 'salida', cantidad: 250 },
    { fecha: '02/08/2026', codigoOperacion: '10', documento: 'B001-008902', almacen: 'Tienda Miraflores', tipo: 'salida', cantidad: 16 },
    { fecha: '05/08/2026', codigoOperacion: '10', documento: 'F001-000123', almacen: 'Almacén Principal', tipo: 'salida', cantidad: 30 },
    { fecha: '06/08/2026', codigoOperacion: '04', documento: 'FC01-000037', almacen: 'Almacén Principal', tipo: 'entrada', cantidad: 12, costoUnitario: 27.66 },
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
      this.movimientos = [];
    }
  }

  get titulo(): string {
    return this.esNuevo ? 'Nuevo producto' : this.formulario.controls.nombre.value;
  }

  get nombresAlmacenes(): string[] {
    return [...new Set(this.movimientos.map((m) => m.almacen))];
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
