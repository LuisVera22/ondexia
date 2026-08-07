import { Component } from '@angular/core';
import {
  EditorDocumentoComponent,
  ConfiguracionDocumento,
} from '../../../shared/components/comunes/editor-documento/editor-documento.component';

/**
 * Emision de guia de remision electronica.
 *
 * Es el unico editor con seccion de traslado: punto de partida, punto de
 * llegada y transportista. El detalle pasa a modo de solo cantidades
 * porque una guia traslada mercaderia, no la vende: no lleva precios ni
 * IGV.
 */
@Component({
  selector: 'app-emitir-guia-remision',
  imports: [EditorDocumentoComponent],
  template: `<app-editor-documento [configuracion]="configuracion" />`,
})
export class EmitirGuiaRemisionComponent {
  configuracion: ConfiguracionDocumento = {
    titulo: 'Emitir guía de remisión',
    rutaListado: '/almacen/guias-remision',
    tipoTercero: 'cliente',
    etiquetaTercero: 'Destinatario',
    esComprobanteElectronico: true,
    serie: 'T001',
    siguienteNumero: '000893',
    exigeRuc: false,
    textoAccion: 'Emitir guía de remisión',
    advertenciaAccion:
      'La guía se enviará a SUNAT y sustentará el traslado. No podrá editarse después de emitida.',
    usaVencimiento: false,
    usaReferencia: false,
    usaTraslado: true,
  };
}
