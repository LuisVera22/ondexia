import { Component } from '@angular/core';
import {
  EditorDocumentoComponent,
  ConfiguracionDocumento,
} from '../../../shared/components/comunes/editor-documento/editor-documento.component';

/**
 * Emision de liquidacion de compra (tipo 04).
 *
 * Es el unico documento del modulo de compras que Ondexia emite y envia a
 * SUNAT. Procede solo para adquisiciones a personas sin RUC.
 */
@Component({
  selector: 'app-emitir-liquidacion',
  imports: [EditorDocumentoComponent],
  template: `<app-editor-documento [configuracion]="configuracion" />`,
})
export class EmitirLiquidacionComponent {
  configuracion: ConfiguracionDocumento = {
    titulo: 'Emitir liquidación de compra',
    rutaListado: '/compras/liquidaciones',
    tipoTercero: 'proveedor',
    etiquetaTercero: 'Proveedor',
    esComprobanteElectronico: true,
    serie: 'LC01',
    siguienteNumero: '000020',
    exigeRuc: false,
    textoAccion: 'Emitir liquidación',
    advertenciaAccion:
      'La liquidación se enviará a SUNAT como comprobante electrónico y no podrá editarse.',
    usaVencimiento: false,
    usaReferencia: false,
  };
}
