import { Component } from '@angular/core';
import {
  EditorDocumentoComponent,
  ConfiguracionDocumento,
} from '../../../shared/components/comunes/editor-documento/editor-documento.component';

/**
 * Editor de nota de compra.
 */
@Component({
  selector: 'app-nueva-nota-compra',
  imports: [EditorDocumentoComponent],
  template: `<app-editor-documento [configuracion]="configuracion" />`,
})
export class NuevaNotaCompraComponent {
  configuracion: ConfiguracionDocumento = {
    titulo: 'Nueva nota de compra',
    rutaListado: '/compras/notas-compra',
    tipoTercero: 'proveedor',
    etiquetaTercero: 'Proveedor',
    esComprobanteElectronico: false,
    exigeRuc: true,
    textoAccion: 'Guardar nota de compra',
    usaVencimiento: false,
    usaReferencia: false,
  };
}
