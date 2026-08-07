import { Component } from '@angular/core';
import {
  ListadoDocumentosComponent,
  ConfiguracionListado,
  RegistroDocumento,
} from '../../../shared/components/comunes/listado-documentos/listado-documentos.component';

/**
 * Listado de cotizaciones.
 *
 * No es un comprobante electronico: no consume correlativo ni se envia a
 * SUNAT. Por eso no muestra columna de estado fiscal.
 */
@Component({
  selector: 'app-cotizaciones',
  imports: [ListadoDocumentosComponent],
  template: `<app-listado-documentos [configuracion]="configuracion" [documentos]="documentos" />`,
})
export class CotizacionesComponent {
  configuracion: ConfiguracionListado = {
    titulo: 'Cotizaciones',
    descripcion: '',
    rutaNuevo: '/ventas/cotizaciones/nueva',
    textoNuevo: 'Nueva cotización',
    esComprobanteElectronico: false,
    etiquetaTercero: 'Cliente',
    vacioTitulo: 'Aún no hay cotizaciones',
    vacioDescripcion: 'Una cotización permite proponer precios sin consecuencias fiscales.',
  };

  documentos: RegistroDocumento[] = [
    { id: 101, numero: 'COT-000045', tercero: 'Constructora Pacífico S.A.', documentoTercero: '20456789123', fecha: '05/08/2026', total: 12480.5, estado: 'BORRADOR' },
    { id: 102, numero: 'COT-000044', tercero: 'Distribuidora Andina S.A.C.', documentoTercero: '20512345678', fecha: '04/08/2026', total: 3820.0, estado: 'BORRADOR' },
  ];
}
