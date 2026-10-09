import { Component } from '@angular/core';
import {
  EditorDocumentoComponent,
  ConfiguracionDocumento,
} from '../../../shared/components/comunes/editor-documento/editor-documento.component';

/**
 * Registro de una factura recibida del proveedor.
 *
 * No se emite nada: se transcribe el comprobante que el proveedor ya
 * emitio, para el control de compras y el credito fiscal.
 */
@Component({
  selector: 'app-registrar-factura-compra',
  imports: [EditorDocumentoComponent],
  template: `<app-editor-documento [configuracion]="configuracion" />`,
})
export class RegistrarFacturaCompraComponent {
  configuracion: ConfiguracionDocumento = {
    titulo: 'Registrar factura de compra',
    rutaListado: '/compras/facturas',
    tipoTercero: 'proveedor',
    etiquetaTercero: 'Proveedor',
    esComprobanteElectronico: true,
    exigeRuc: true,
    textoAccion: 'Registrar factura',
    usaVencimiento: true,
    usaReferencia: false,
  };
}
