import { Component } from '@angular/core';
import {
  ListadoDocumentosComponent,
  ConfiguracionListado,
  RegistroDocumento,
} from '../../../shared/components/comunes/listado-documentos/listado-documentos.component';

/**
 * Listado de notas de preventa.
 *
 * Documento interno previo al comprobante: reserva la venta pero no la
 * declara. Se convierte en boleta o factura al confirmarse.
 */
@Component({
  selector: 'app-preventas',
  imports: [ListadoDocumentosComponent],
  template: `<app-listado-documentos [configuracion]="configuracion" [documentos]="documentos" />`,
})
export class PreventasComponent {
  configuracion: ConfiguracionListado = {
    titulo: 'Notas de preventa',
    descripcion: '',
    rutaNuevo: '/ventas/preventas/nueva',
    textoNuevo: 'Nueva preventa',
    esComprobanteElectronico: false,
    etiquetaTercero: 'Cliente',
    rutaDetalle: '/ventas/comprobantes',
    vacioTitulo: 'Aún no hay notas de preventa',
    vacioDescripcion: 'La preventa reserva la venta antes de emitir el comprobante definitivo.',
  };

  documentos: RegistroDocumento[] = [
    { id: 201, numero: 'PRE-000318', tercero: 'Comercial El Sol E.I.R.L.', documentoTercero: '20587654321', fecha: '06/08/2026', total: 945.8, estado: 'BORRADOR' },
  ];
}
