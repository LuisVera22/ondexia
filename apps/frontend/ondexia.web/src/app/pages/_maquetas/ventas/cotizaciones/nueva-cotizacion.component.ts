import { Component } from '@angular/core';
import {
  EditorDocumentoComponent,
  ConfiguracionDocumento,
} from '../../../shared/components/comunes/editor-documento/editor-documento.component';

/**
 * Editor de cotizacion.
 *
 * Se construye primero a proposito: usa el mismo editor que los
 * comprobantes pero sin consecuencias fiscales, asi el componente mas
 * dificil se pule sin riesgo (plan de vistas seccion 8, Etapa 4).
 */
@Component({
  selector: 'app-nueva-cotizacion',
  imports: [EditorDocumentoComponent],
  template: `<app-editor-documento [configuracion]="configuracion" />`,
})
export class NuevaCotizacionComponent {
  configuracion: ConfiguracionDocumento = {
    titulo: 'Nueva cotización',
    rutaListado: '/ventas/cotizaciones',
    tipoTercero: 'cliente',
    etiquetaTercero: 'Cliente',
    esComprobanteElectronico: false,
    exigeRuc: false,
    textoAccion: 'Guardar cotización',
    usaVencimiento: true,
    usaReferencia: false,
  };
}
