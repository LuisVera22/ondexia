import { Component } from '@angular/core';
import {
  EditorDocumentoComponent,
  ConfiguracionDocumento,
} from '../../../shared/components/comunes/editor-documento/editor-documento.component';

/**
 * Editor de nota de preventa.
 *
 * Documento interno: reserva la venta pero no la declara ante SUNAT.
 */
@Component({
  selector: 'app-nueva-preventa',
  imports: [EditorDocumentoComponent],
  template: `<app-editor-documento [configuracion]="configuracion" />`,
})
export class NuevaPreventaComponent {
  configuracion: ConfiguracionDocumento = {
    titulo: 'Nueva nota de preventa',
    rutaListado: '/ventas/preventas',
    tipoTercero: 'cliente',
    etiquetaTercero: 'Cliente',
    esComprobanteElectronico: false,
    exigeRuc: false,
    textoAccion: 'Guardar preventa',
    usaVencimiento: false,
    usaReferencia: false,
  };
}
