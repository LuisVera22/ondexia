import { Component } from '@angular/core';
import {
  EditorDocumentoComponent,
  ConfiguracionDocumento,
} from '../../../shared/components/comunes/editor-documento/editor-documento.component';

/**
 * Emision de boleta de venta (tipo 03).
 *
 * No exige RUC. La boleta no se envia individualmente: se declara en el
 * resumen diario, que es asincrono y devuelve un ticket.
 */
@Component({
  selector: 'app-emitir-boleta',
  imports: [EditorDocumentoComponent],
  template: `<app-editor-documento [configuracion]="configuracion" />`,
})
export class EmitirBoletaComponent {
  configuracion: ConfiguracionDocumento = {
    titulo: 'Emitir boleta',
    rutaListado: '/ventas/boletas',
    tipoTercero: 'cliente',
    etiquetaTercero: 'Cliente',
    esComprobanteElectronico: true,
    serie: 'B001',
    siguienteNumero: '008918',
    exigeRuc: false,
    textoAccion: 'Emitir boleta',
    advertenciaAccion:
      'La boleta quedará emitida y se declarará ante SUNAT en el resumen diario. No podrá editarse después.',
    usaVencimiento: false,
    usaReferencia: false,
  };
}
