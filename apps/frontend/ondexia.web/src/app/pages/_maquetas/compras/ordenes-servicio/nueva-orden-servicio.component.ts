import { Component } from '@angular/core';
import {
  EditorDocumentoComponent,
  ConfiguracionDocumento,
} from '../../../shared/components/comunes/editor-documento/editor-documento.component';

/**
 * Editor de orden de servicio.
 *
 * No afecta existencias, por eso el detalle no descuenta stock ni exige
 * que las lineas correspondan a productos del catalogo.
 */
@Component({
  selector: 'app-nueva-orden-servicio',
  imports: [EditorDocumentoComponent],
  template: `<app-editor-documento [configuracion]="configuracion" />`,
})
export class NuevaOrdenServicioComponent {
  configuracion: ConfiguracionDocumento = {
    titulo: 'Nueva orden de servicio',
    rutaListado: '/compras/ordenes-servicio',
    tipoTercero: 'proveedor',
    etiquetaTercero: 'Proveedor',
    esComprobanteElectronico: false,
    exigeRuc: true,
    textoAccion: 'Emitir orden de servicio',
    usaVencimiento: true,
    usaReferencia: false,
  };
}
