import { Component, Input, inject } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { PageBreadcrumbComponent } from '../../common/page-breadcrumb/page-breadcrumb.component';
import { TablaDatosComponent, ColumnaTabla, OrdenTabla } from '../tabla-datos/tabla-datos.component';
import { EstadoComprobanteComponent, EstadoComprobante } from '../estado-comprobante/estado-comprobante.component';

export interface RegistroDocumento extends Record<string, unknown> {
  id: number;
  numero: string;
  tercero: string;
  documentoTercero: string;
  fecha: string;
  total: number;
  estado: EstadoComprobante;
}

export interface ConfiguracionListado {
  titulo: string;
  descripcion: string;
  /** Ruta del editor. Vacía cuando el documento no se crea desde el listado. */
  rutaNuevo: string;
  textoNuevo: string;
  /** Muestra la columna de estado ante SUNAT. */
  esComprobanteElectronico: boolean;
  etiquetaTercero: string;
  vacioTitulo: string;
  vacioDescripcion: string;
  /** Aviso permanente sobre el flujo correcto, cuando lo hay. */
  nota?: string;
}

/**
 * Listado de documentos — el patrón P1 aplicado a comprobantes.
 *
 * Lo comparten cotizaciones, preventas, boletas, facturas, notas de crédito
 * y los documentos de compra. Cambian las etiquetas y si hay estado ante
 * SUNAT; la estructura es la misma.
 */
@Component({
  selector: 'app-listado-documentos',
  imports: [
    FormsModule,
    RouterModule,
    PageBreadcrumbComponent,
    TablaDatosComponent,
    EstadoComprobanteComponent,
  ],
  templateUrl: './listado-documentos.component.html',
})
export class ListadoDocumentosComponent {
  private readonly router = inject(Router);

  @Input({ required: true }) configuracion!: ConfiguracionListado;
  @Input() documentos: RegistroDocumento[] = [];

  termino = '';
  estadoFiltro = '';
  desde = '';
  hasta = '';

  orden: OrdenTabla | null = { campo: 'numero', direccion: 'desc' };
  pagina = 1;
  readonly tamanoPagina = 10;

  get columnas(): ColumnaTabla[] {
    const base: ColumnaTabla[] = [
      { campo: 'numero', titulo: 'Número', ordenable: true, ancho: 'w-40' },
      { campo: 'tercero', titulo: this.configuracion.etiquetaTercero, ordenable: true },
      { campo: 'fecha', titulo: 'Fecha', ordenable: true, ancho: 'w-32' },
      { campo: 'total', titulo: 'Total', formato: 'importe', ordenable: true, ancho: 'w-36' },
    ];
    return base;
  }

  get registros(): RegistroDocumento[] {
    return this.documentos.filter((d) => {
      const coincideTermino =
        !this.termino ||
        d.numero.toLowerCase().includes(this.termino.toLowerCase()) ||
        d.tercero.toLowerCase().includes(this.termino.toLowerCase()) ||
        d.documentoTercero.includes(this.termino);
      const coincideEstado = !this.estadoFiltro || d.estado === this.estadoFiltro;
      return coincideTermino && coincideEstado;
    });
  }

  get hayFiltrosActivos(): boolean {
    return Boolean(this.termino || this.estadoFiltro || this.desde || this.hasta);
  }

  limpiarFiltros(): void {
    this.termino = '';
    this.estadoFiltro = '';
    this.desde = '';
    this.hasta = '';
  }

  nuevo(): void {
    if (this.configuracion.rutaNuevo) {
      this.router.navigate([this.configuracion.rutaNuevo]);
    }
  }

  abrirDetalle(registro: Record<string, unknown>): void {
    this.router.navigate(['/ventas/comprobantes', registro['id']]);
  }
}
