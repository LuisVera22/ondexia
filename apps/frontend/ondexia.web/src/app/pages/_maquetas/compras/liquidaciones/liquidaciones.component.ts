import { Component } from '@angular/core';
import {
  ListadoDocumentosComponent,
  ConfiguracionListado,
  RegistroDocumento,
} from '../../../shared/components/comunes/listado-documentos/listado-documentos.component';

/**
 * Listado de liquidaciones de compra.
 *
 * A diferencia del resto del modulo, la liquidacion la EMITE Ondexia
 * (comprobante tipo 04): se usa para adquisiciones a personas sin RUC, que
 * no pueden emitir factura.
 */
@Component({
  selector: 'app-liquidaciones',
  imports: [ListadoDocumentosComponent],
  template: `<app-listado-documentos [configuracion]="configuracion" [documentos]="documentos" />`,
})
export class LiquidacionesComponent {
  configuracion: ConfiguracionListado = {
    titulo: 'Liquidaciones de compra',
    descripcion: '',
    rutaNuevo: '/compras/liquidaciones/nueva',
    textoNuevo: 'Emitir liquidación',
    esComprobanteElectronico: true,
    etiquetaTercero: 'Proveedor',
    vacioTitulo: 'Aún no hay liquidaciones de compra',
    vacioDescripcion: 'La liquidación documenta compras a personas sin RUC, que no pueden emitir factura.',
    nota:
      'La liquidación de compra la emite usted, no el proveedor. Se usa cuando se adquiere a personas sin RUC y se envía a SUNAT como comprobante electrónico.',
    rutaDetalle: '',
  };

  documentos: RegistroDocumento[] = [
    { id: 1101, numero: 'LC01-000019', tercero: 'Máximo Huamán Ccahuana', documentoTercero: '41236589', fecha: '03/08/2026', total: 640.0, estado: 'ACEPTADO' },
  ];
}
