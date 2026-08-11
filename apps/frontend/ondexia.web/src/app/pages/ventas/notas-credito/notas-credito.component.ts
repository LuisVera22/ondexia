import { Component } from '@angular/core';
import {
  ListadoDocumentosComponent,
  ConfiguracionListado,
  RegistroDocumento,
} from '../../../shared/components/comunes/listado-documentos/listado-documentos.component';

/**
 * Listado de notas de credito.
 *
 * Comprobante electronico tipo 07. Siempre nace de un comprobante
 * existente: el listado sirve para consultarlas, no para crearlas.
 */
@Component({
  selector: 'app-notas-credito',
  imports: [ListadoDocumentosComponent],
  template: `<app-listado-documentos [configuracion]="configuracion" [documentos]="documentos" />`,
})
export class NotasCreditoComponent {
  configuracion: ConfiguracionListado = {
    titulo: 'Notas de crédito',
    descripcion: '',
    rutaNuevo: '',
    textoNuevo: '',
    esComprobanteElectronico: true,
    etiquetaTercero: 'Cliente',
    rutaDetalle: '/ventas/comprobantes',
    vacioTitulo: 'Aún no hay notas de crédito',
    vacioDescripcion: 'Las notas de crédito anulan o corrigen un comprobante ya emitido.',
    nota: 'La nota de crédito se emite desde el detalle del comprobante que se quiere anular o corregir, con la acción «Emitir nota de crédito». Así no se referencia mal el documento de origen.',
  };

  documentos: RegistroDocumento[] = [
    { id: 501, numero: 'FC01-000037', tercero: 'Distribuidora Andina S.A.C.', documentoTercero: '20512345678', fecha: '04/08/2026', total: 560.2, estado: 'ACEPTADO' },
  ];
}
