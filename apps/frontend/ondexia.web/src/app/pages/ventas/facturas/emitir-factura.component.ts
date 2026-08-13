import { Component } from '@angular/core';
import {
  EditorDocumentoComponent,
  ConfiguracionDocumento,
} from '../../../shared/components/comunes/editor-documento/editor-documento.component';

/**
 * Emision de factura electronica (tipo 01).
 *
 * Exige cliente con RUC: con DNI corresponde boleta. La validacion vive
 * en el editor para que el error aparezca antes de emitir y no en el
 * rechazo de SUNAT.
 */
@Component({
  selector: 'app-emitir-factura',
  imports: [EditorDocumentoComponent],
  template: `<app-editor-documento [configuracion]="configuracion" />`,
})
export class EmitirFacturaComponent {
  configuracion: ConfiguracionDocumento = {
    titulo: 'Emitir factura',
    rutaListado: '/ventas/facturas',
    tipoTercero: 'cliente',
    etiquetaTercero: 'Cliente',
    esComprobanteElectronico: true,
    serie: 'F001',
    siguienteNumero: '000125',
    exigeRuc: true,
    textoAccion: 'Emitir factura',
    advertenciaAccion:
      'La factura se enviará a SUNAT y no podrá editarse. Para corregirla habrá que emitir una nota de crédito.',
    usaVencimiento: false,
    usaReferencia: false,
  };
}
