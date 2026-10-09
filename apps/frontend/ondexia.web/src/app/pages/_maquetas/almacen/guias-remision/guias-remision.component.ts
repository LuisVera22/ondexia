import { Component } from '@angular/core';
import {
  ListadoDocumentosComponent,
  ConfiguracionListado,
  RegistroDocumento,
} from '../../../shared/components/comunes/listado-documentos/listado-documentos.component';

/**
 * Listado de guias de remision electronicas.
 *
 * Se emiten por un canal distinto al del resto de comprobantes: API REST
 * con OAuth2 en lugar del canal CPE (DTE R-06). Para el usuario el flujo se
 * ve igual, pero el estado puede tardar mas en resolverse.
 */
@Component({
  selector: 'app-guias-remision',
  imports: [ListadoDocumentosComponent],
  template: `<app-listado-documentos [configuracion]="configuracion" [documentos]="documentos" />`,
})
export class GuiasRemisionComponent {
  configuracion: ConfiguracionListado = {
    titulo: 'Guías de remisión',
    descripcion: '',
    rutaNuevo: '/almacen/guias-remision/nueva',
    textoNuevo: 'Emitir guía de remisión',
    esComprobanteElectronico: true,
    etiquetaTercero: 'Destinatario',
    vacioTitulo: 'Aún no hay guías de remisión',
    vacioDescripcion: 'La guía de remisión sustenta el traslado de mercadería y se envía a SUNAT.',
    nota:
      'Las guías de remisión se emiten por un canal distinto al del resto de comprobantes, por lo que su estado puede tardar más en resolverse.',
    rutaDetalle: '',
  };

  documentos: RegistroDocumento[] = [
    { id: 1301, numero: 'T001-000892', tercero: 'Distribuidora Andina S.A.C.', documentoTercero: '20512345678', fecha: '06/08/2026', total: 0, estado: 'ENVIADO' },
    { id: 1302, numero: 'T001-000891', tercero: 'Constructora Pacífico S.A.', documentoTercero: '20456789123', fecha: '05/08/2026', total: 0, estado: 'ACEPTADO' },
  ];
}
