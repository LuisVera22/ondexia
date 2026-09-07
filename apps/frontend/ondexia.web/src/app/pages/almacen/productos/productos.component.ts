import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import {
  AccionDeFila,
  ColumnaTabla,
  OrdenTabla,
  TablaDatosComponent,
} from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import {
  DesplegableComponent,
  OpcionDesplegable,
} from '../../../shared/components/comunes/desplegable/desplegable.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { AlmacenApiService, ProductoApi } from '../../../nucleo/almacen.api.service';
import { mensajeDeError } from '../../../nucleo/errores';
import { ContextoService } from '../../../shared/services/contexto.service';

/**
 * El catálogo de la empresa (doc 12 §3.5).
 *
 * <p>Se trae entero y se filtra aquí: un catálogo de mostrador son cientos de
 * filas, no miles, y el filtro instantáneo vale más que una petición por tecla.
 * El servidor tiene búsqueda propia ({@code ?q=}) para el punto de venta, que
 * es donde el catálogo se consulta a cada línea.
 *
 * <p>Sin marca ni stock en la tabla: la marca no está en el primer producto y
 * las existencias son por almacén, así que un número suelto en la fila diría
 * poco. Se ven en la ficha.
 */
@Component({
  selector: 'app-productos',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent, FormsModule, DesplegableComponent, BotonComponent],
  templateUrl: './productos.component.html',
})
export class ProductosComponent {
  private readonly api = inject(AlmacenApiService);
  private readonly contexto = inject(ContextoService);
  private readonly router = inject(Router);

  termino = '';
  estadoFiltro = '';

  orden: OrdenTabla | null = { campo: 'nombre', direccion: 'asc' };
  pagina = 1;
  readonly tamanoPagina = 10;

  readonly opcionesEstado: OpcionDesplegable[] = [
    { valor: '', etiqueta: 'Todos' },
    { valor: 'activo', etiqueta: 'Activos' },
    { valor: 'inactivo', etiqueta: 'Inactivos' },
  ];

  readonly accionesDeFila: AccionDeFila[] = [{ id: 'ver', etiqueta: 'Ver', icono: 'ver' }];

  readonly columnas: ColumnaTabla[] = [
    { campo: 'codigo', titulo: 'Código', ordenable: true, ancho: 'w-36' },
    { campo: 'nombre', titulo: 'Producto', ordenable: true, principal: true },
    { campo: 'unidad', titulo: 'Unidad', ancho: 'w-32' },
    { campo: 'igv', titulo: 'IGV', ancho: 'w-28' },
    { campo: 'precioLista', titulo: 'Precio de lista', formato: 'importe', ordenable: true, ancho: 'w-36' },
    {
      campo: 'estado',
      titulo: 'Estado',
      ancho: 'w-28',
      formato: 'insignia',
      tono: (registro) => (registro['activo'] === true ? 'exito' : 'neutro'),
    },
  ];

  readonly puedeRegistrar = computed(() => this.contexto.puede('almacen.producto:registrar'));

  private readonly todos = signal<ProductoApi[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.todos.set(await this.api.productos());
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudo cargar el catálogo.'));
    } finally {
      this.cargando.set(false);
    }
  }

  recargar(): void {
    void this.cargar();
  }

  get registros(): Record<string, unknown>[] {
    const termino = this.termino.trim().toLowerCase();
    return this.todos()
      .filter((p) => {
        const coincideTermino =
          !termino || p.nombre.toLowerCase().includes(termino) || p.codigo.toLowerCase().includes(termino);
        const coincideEstado =
          !this.estadoFiltro || (this.estadoFiltro === 'activo' ? p.activo : !p.activo);
        return coincideTermino && coincideEstado;
      })
      .map((p) => ({
        id: p.id,
        codigo: p.codigo,
        nombre: p.nombre,
        unidad: p.unidadNombre,
        igv: p.afectacionNombre,
        precioLista: p.precioLista,
        estado: p.activo ? 'Activo' : 'Inactivo',
        activo: p.activo,
      }));
  }

  get hayFiltrosActivos(): boolean {
    return Boolean(this.termino || this.estadoFiltro);
  }

  limpiarFiltros(): void {
    this.termino = '';
    this.estadoFiltro = '';
  }

  abrirFicha(registro: Record<string, unknown>): void {
    void this.router.navigate(['/almacen/productos', registro['id']]);
  }

  nuevo(): void {
    void this.router.navigate(['/almacen/productos', 'nuevo']);
  }

  ejecutarAccion(evento: { accion: string; registro: Record<string, unknown> }): void {
    if (evento.accion === 'ver') {
      this.abrirFicha(evento.registro);
    }
  }
}
