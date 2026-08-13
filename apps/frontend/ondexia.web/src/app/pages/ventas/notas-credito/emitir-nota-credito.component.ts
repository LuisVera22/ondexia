import { Component } from '@angular/core';
import {
  EditorDocumentoComponent,
  ConfiguracionDocumento,
} from '../../../shared/components/comunes/editor-documento/editor-documento.component';

/**
 * Emision de nota de credito (tipo 07).
 *
 * Exige documento de referencia y motivo del catalogo SUNAT numero 09.
 * Sin ambos el comprobante es rechazado, asi que se validan antes de
 * permitir la emision.
 */
@Component({
  selector: 'app-emitir-nota-credito',
  imports: [EditorDocumentoComponent],
  template: `<app-editor-documento [configuracion]="configuracion" />`,
})
export class EmitirNotaCreditoComponent {
  configuracion: ConfiguracionDocumento = {
    titulo: 'Emitir nota de crédito',
    rutaListado: '/ventas/notas-credito',
    tipoTercero: 'cliente',
    etiquetaTercero: 'Cliente',
    esComprobanteElectronico: true,
    serie: 'FC01',
    siguienteNumero: '000038',
    exigeRuc: false,
    textoAccion: 'Emitir nota de crédito',
    advertenciaAccion:
      'La nota de crédito se enviará a SUNAT y modificará el comprobante referenciado. No podrá revertirse.',
    usaVencimiento: false,
    usaReferencia: true,
    motivos: [
      { codigo: '01', nombre: 'Anulación de la operación' },
      { codigo: '02', nombre: 'Anulación por error en el RUC' },
      { codigo: '03', nombre: 'Corrección por error en la descripción' },
      { codigo: '04', nombre: 'Descuento global' },
      { codigo: '05', nombre: 'Descuento por ítem' },
      { codigo: '06', nombre: 'Devolución total' },
      { codigo: '07', nombre: 'Devolución por ítem' },
    ],
  };
}
