import { Component } from '@angular/core';
import {
  ListadoDocumentosComponent,
  ConfiguracionListado,
  RegistroDocumento,
} from '../../../shared/components/comunes/listado-documentos/listado-documentos.component';

/**
 * Listado de notas de compra.
 *
 * Documento interno que registra la recepcion conforme antes de que llegue
 * la factura del proveedor.
 */
@Component({
  selector: 'app-notas-compra',
  imports: [ListadoDocumentosComponent],
  template: `<app-listado-documentos [configuracion]="configuracion" [documentos]="documentos" />`,
})
export class NotasCompraComponent {
  configuracion: ConfiguracionListado = {
    titulo: 'Notas de compra',
    descripcion: '',
    rutaNuevo: '/compras/notas-compra/nueva',
    textoNuevo: 'Nueva nota de compra',
    esComprobanteElectronico: false,
    etiquetaTercero: 'Proveedor',
    vacioTitulo: 'Aún no hay notas de compra',
    vacioDescripcion: 'La nota de compra registra la recepción conforme antes de la factura del proveedor.',
    rutaDetalle: '',
  };

  documentos: RegistroDocumento[] = [
    { id: 901, numero: 'NC-000198', tercero: 'Cementos Pacasmayo S.A.A.', documentoTercero: '20100113610', fecha: '06/08/2026', total: 18420.0, estado: 'BORRADOR' },
  ];
}
