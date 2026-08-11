import { Component } from '@angular/core';
import {
  EditorDocumentoComponent,
  ConfiguracionDocumento,
} from '../../../shared/components/comunes/editor-documento/editor-documento.component';

/**
 * Editor de nota de pedido.
 *
 * Documento interno: no compromete al proveedor ni genera obligacion de
 * pago. Se convierte en orden de compra al aprobarse.
 */
@Component({
  selector: 'app-nueva-nota-pedido',
  imports: [EditorDocumentoComponent],
  template: `<app-editor-documento [configuracion]="configuracion" />`,
})
export class NuevaNotaPedidoComponent {
  configuracion: ConfiguracionDocumento = {
    titulo: 'Nueva nota de pedido',
    rutaListado: '/compras/notas-pedido',
    tipoTercero: 'proveedor',
    etiquetaTercero: 'Proveedor',
    esComprobanteElectronico: false,
    exigeRuc: true,
    textoAccion: 'Guardar nota de pedido',
    usaVencimiento: false,
    usaReferencia: false,
  };
}
