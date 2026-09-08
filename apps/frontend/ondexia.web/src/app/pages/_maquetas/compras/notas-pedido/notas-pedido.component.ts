import { Component } from '@angular/core';
import {
  ListadoDocumentosComponent,
  ConfiguracionListado,
  RegistroDocumento,
} from '../../../shared/components/comunes/listado-documentos/listado-documentos.component';

/**
 * Listado de notas de pedido.
 *
 * Primer eslabon de la cadena de compras: registra lo que se necesita
 * antes de comprometer una orden con el proveedor.
 */
@Component({
  selector: 'app-notas-pedido',
  imports: [ListadoDocumentosComponent],
  template: `<app-listado-documentos [configuracion]="configuracion" [documentos]="documentos" />`,
})
export class NotasPedidoComponent {
  configuracion: ConfiguracionListado = {
    titulo: 'Notas de pedido',
    descripcion: '',
    rutaNuevo: '/compras/notas-pedido/nueva',
    textoNuevo: 'Nueva nota de pedido',
    esComprobanteElectronico: false,
    etiquetaTercero: 'Proveedor',
    vacioTitulo: 'Aún no hay notas de pedido',
    vacioDescripcion: 'La nota de pedido registra lo que se necesita antes de emitir una orden al proveedor.',
    rutaDetalle: '',
  };

  documentos: RegistroDocumento[] = [
    { id: 601, numero: 'NP-000112', tercero: 'Ferretería Central S.A.C.', documentoTercero: '20524089107', fecha: '06/08/2026', total: 2340.0, estado: 'BORRADOR' },
  ];
}
