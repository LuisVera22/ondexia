import { Component } from '@angular/core';
import {
  ListadoDocumentosComponent,
  ConfiguracionListado,
  RegistroDocumento,
} from '../../../shared/components/comunes/listado-documentos/listado-documentos.component';

/**
 * Listado de guias de ingreso.
 *
 * Documento interno de recepcion: es el que da entrada a la mercaderia en
 * un almacen. Sin guia de ingreso no hay existencias que descontar despues.
 */
@Component({
  selector: 'app-guias-ingreso',
  imports: [ListadoDocumentosComponent],
  template: `<app-listado-documentos [configuracion]="configuracion" [documentos]="documentos" />`,
})
export class GuiasIngresoComponent {
  configuracion: ConfiguracionListado = {
    titulo: 'Guías de ingreso',
    descripcion: '',
    rutaNuevo: '/almacen/guias-ingreso/nueva',
    textoNuevo: 'Nueva guía de ingreso',
    esComprobanteElectronico: false,
    etiquetaTercero: 'Proveedor',
    vacioTitulo: 'Aún no hay guías de ingreso',
    vacioDescripcion: 'La guía de ingreso da entrada a la mercadería en un almacén y es lo que genera existencias.',
    rutaDetalle: '',
  };

  documentos: RegistroDocumento[] = [
    { id: 1201, numero: 'GI-000347', tercero: 'Cementos Pacasmayo S.A.A.', documentoTercero: '20100113610', fecha: '06/08/2026', total: 18420.0, estado: 'BORRADOR' },
    { id: 1202, numero: 'GI-000346', tercero: 'Ferretería Central S.A.C.', documentoTercero: '20524089107', fecha: '02/08/2026', total: 1280.0, estado: 'BORRADOR' },
  ];
}
