import { Component } from '@angular/core';
import {
  EditorDocumentoComponent,
  ConfiguracionDocumento,
} from '../../../shared/components/comunes/editor-documento/editor-documento.component';

/**
 * Editor de guia de ingreso.
 *
 * Al confirmarse incrementa las existencias del almacen de destino. Es el
 * unico camino por el que entra mercaderia al inventario: por eso las
 * existencias de la ficha de producto son de solo consulta.
 */
@Component({
  selector: 'app-nueva-guia-ingreso',
  imports: [EditorDocumentoComponent],
  template: `<app-editor-documento [configuracion]="configuracion" />`,
})
export class NuevaGuiaIngresoComponent {
  configuracion: ConfiguracionDocumento = {
    titulo: 'Nueva guía de ingreso',
    rutaListado: '/almacen/guias-ingreso',
    tipoTercero: 'proveedor',
    etiquetaTercero: 'Proveedor',
    esComprobanteElectronico: false,
    exigeRuc: true,
    textoAccion: 'Registrar ingreso',
    advertenciaAccion:
      'Al registrar el ingreso se incrementarán las existencias del almacén de destino.',
    usaVencimiento: false,
    usaReferencia: false,
  };
}
