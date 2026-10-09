import { Component } from '@angular/core';
import {
  ListadoDocumentosComponent,
  ConfiguracionListado,
  RegistroDocumento,
} from '../../../shared/components/comunes/listado-documentos/listado-documentos.component';

/**
 * Listado de ordenes de compra.
 *
 * A diferencia de la nota de pedido, la orden compromete al proveedor: es
 * el documento que respalda la adquisicion.
 */
@Component({
  selector: 'app-ordenes-compra',
  imports: [ListadoDocumentosComponent],
  template: `<app-listado-documentos [configuracion]="configuracion" [documentos]="documentos" />`,
})
export class OrdenesCompraComponent {
  configuracion: ConfiguracionListado = {
    titulo: 'Órdenes de compra',
    descripcion: '',
    rutaNuevo: '/compras/ordenes-compra/nueva',
    textoNuevo: 'Nueva orden de compra',
    esComprobanteElectronico: false,
    etiquetaTercero: 'Proveedor',
    vacioTitulo: 'Aún no hay órdenes de compra',
    vacioDescripcion: 'La orden de compra formaliza el pedido ante el proveedor.',
    rutaDetalle: '',
  };

  documentos: RegistroDocumento[] = [
    { id: 701, numero: 'OC-000284', tercero: 'Cementos Pacasmayo S.A.A.', documentoTercero: '20100113610', fecha: '05/08/2026', total: 18420.0, estado: 'ACEPTADO' },
    { id: 702, numero: 'OC-000283', tercero: 'Corporación Aceros Arequipa S.A.', documentoTercero: '20100136741', fecha: '03/08/2026', total: 9640.5, estado: 'ACEPTADO' },
    { id: 703, numero: 'OC-000282', tercero: 'Ferretería Central S.A.C.', documentoTercero: '20524089107', fecha: '01/08/2026', total: 1280.0, estado: 'BORRADOR' },
  ];
}
