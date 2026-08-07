import { Component } from '@angular/core';
import { PageBreadcrumbComponent } from '../../shared/components/common/page-breadcrumb/page-breadcrumb.component';
import { TablaDatosComponent, ColumnaTabla, OrdenTabla } from '../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { PaginaVaciaComponent } from '../../shared/components/comunes/pagina-vacia/pagina-vacia.component';
import { ConfirmacionComponent } from '../../shared/components/comunes/confirmacion/confirmacion.component';
import { EstadoComprobanteComponent, EstadoComprobante } from '../../shared/components/comunes/estado-comprobante/estado-comprobante.component';
import { BuscadorEntidadComponent, OpcionEntidad } from '../../shared/components/comunes/buscador-entidad/buscador-entidad.component';
import { EditorLineasComponent, LineaDocumento } from '../../shared/components/comunes/editor-lineas/editor-lineas.component';
import { SelectorContextoComponent, OpcionContexto } from '../../shared/components/comunes/selector-contexto/selector-contexto.component';

/**
 * Catálogo de los componentes transversales de Ondexia.
 *
 * Referencia interna para consultar el comportamiento de cada componente
 * al construir las vistas. Se elimina antes de publicar, igual que el
 * resto del kit de la plantilla.
 */
@Component({
  selector: 'app-kit-ondexia',
  imports: [
    PageBreadcrumbComponent,
    TablaDatosComponent,
    PaginaVaciaComponent,
    ConfirmacionComponent,
    EstadoComprobanteComponent,
    BuscadorEntidadComponent,
    EditorLineasComponent,
    SelectorContextoComponent,
  ],
  templateUrl: './kit-ondexia.component.html',
})
export class KitOndexiaComponent {
  readonly estados: EstadoComprobante[] = [
    'BORRADOR',
    'PENDIENTE',
    'FIRMADO',
    'ENVIADO',
    'ACEPTADO',
    'OBSERVADO',
    'RECHAZADO',
    'ERROR_ENVIO',
    'ANULADO',
  ];

  columnas: ColumnaTabla[] = [
    { campo: 'serie', titulo: 'Comprobante', ordenable: true },
    { campo: 'cliente', titulo: 'Cliente', ordenable: true },
    { campo: 'fecha', titulo: 'Fecha', ordenable: true },
    { campo: 'total', titulo: 'Total', formato: 'importe', ordenable: true },
  ];

  registros = [
    { id: 1, serie: 'F001-000123', cliente: 'Distribuidora Andina S.A.C.', fecha: '05/08/2026', total: 1284.5 },
    { id: 2, serie: 'F001-000124', cliente: 'Comercial El Sol E.I.R.L.', fecha: '05/08/2026', total: 342.0 },
    { id: 3, serie: 'B001-000891', cliente: 'Rosa Quispe Mamani', fecha: '06/08/2026', total: 89.9 },
  ];

  orden: OrdenTabla | null = { campo: 'serie', direccion: 'asc' };
  pagina = 1;

  opcionesProducto: OpcionEntidad[] = [
    { id: 1, titulo: 'Cemento Portland Tipo I 42.5 kg', detalle: 'CEM-001 · Bolsa', extremo: 'S/ 32.50' },
    { id: 2, titulo: 'Fierro corrugado 1/2" x 9 m', detalle: 'FIE-012 · Varilla', extremo: 'S/ 48.00' },
    { id: 3, titulo: 'Ladrillo King Kong 18 huecos', detalle: 'LAD-018 · Unidad', extremo: 'S/ 1.20', deshabilitada: true },
  ];

  lineas: LineaDocumento[] = [
    {
      id: 'l1',
      codigo: 'CEM-001',
      descripcion: 'Cemento Portland Tipo I 42.5 kg',
      unidad: 'BOL',
      cantidad: 20,
      valorUnitario: 27.54,
      descuento: 0,
      afectacion: 'GRAVADO',
    },
    {
      id: 'l2',
      codigo: 'LIB-004',
      descripcion: 'Libro de actas (exonerado)',
      unidad: 'NIU',
      cantidad: 3,
      valorUnitario: 15.0,
      descuento: 0,
      afectacion: 'EXONERADO',
    },
  ];

  empresas: OpcionContexto[] = [
    { id: 1, nombre: 'Wirbi S.A.C.', detalle: 'RUC 20512345678' },
    { id: 2, nombre: 'Comercial Andina S.A.C.', detalle: 'RUC 20587654321' },
  ];

  establecimientos: OpcionContexto[] = [
    { id: 1, nombre: 'Principal', detalle: '0000 · Av. Javier Prado 1234' },
    { id: 2, nombre: 'Miraflores', detalle: '0001 · Av. Larco 456' },
  ];

  empresaActiva = this.empresas[0];
  establecimientoActivo = this.establecimientos[0];

  confirmacionAbierta = false;

  alOrdenar(nuevo: OrdenTabla): void {
    this.orden = nuevo;
  }

  alCambiarPagina(numero: number): void {
    this.pagina = numero;
  }
}
