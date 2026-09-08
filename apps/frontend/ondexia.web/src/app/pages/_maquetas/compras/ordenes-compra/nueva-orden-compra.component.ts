import { Component } from '@angular/core';
import {
  EditorDocumentoComponent,
  ConfiguracionDocumento,
} from '../../../shared/components/comunes/editor-documento/editor-documento.component';

/**
 * Editor de orden de compra.
 *
 * Compromete al proveedor, asi que la confirmacion es explicita: una vez
 * enviada, el proveedor la toma como pedido en firme.
 */
@Component({
  selector: 'app-nueva-orden-compra',
  imports: [EditorDocumentoComponent],
  template: `<app-editor-documento [configuracion]="configuracion" />`,
})
export class NuevaOrdenCompraComponent {
  configuracion: ConfiguracionDocumento = {
    titulo: 'Nueva orden de compra',
    rutaListado: '/compras/ordenes-compra',
    tipoTercero: 'proveedor',
    etiquetaTercero: 'Proveedor',
    esComprobanteElectronico: false,
    exigeRuc: true,
    textoAccion: 'Emitir orden de compra',
    advertenciaAccion:
      'La orden se enviará al proveedor y quedará registrada como pedido en firme.',
    usaVencimiento: true,
    usaReferencia: false,
  };
}
