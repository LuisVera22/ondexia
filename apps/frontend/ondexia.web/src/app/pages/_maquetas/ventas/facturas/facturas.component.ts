import { Component } from '@angular/core';
import {
  ListadoDocumentosComponent,
  ConfiguracionListado,
  RegistroDocumento,
} from '../../../shared/components/comunes/listado-documentos/listado-documentos.component';

/**
 * Listado de facturas emitidas.
 *
 * Comprobante electronico tipo 01. Solo se emite a clientes con RUC.
 */
@Component({
  selector: 'app-facturas',
  imports: [ListadoDocumentosComponent],
  template: `<app-listado-documentos [configuracion]="configuracion" [documentos]="documentos" />`,
})
export class FacturasComponent {
  configuracion: ConfiguracionListado = {
    titulo: 'Facturas',
    descripcion: '',
    rutaNuevo: '/ventas/facturas/nueva',
    textoNuevo: 'Emitir factura',
    esComprobanteElectronico: true,
    etiquetaTercero: 'Cliente',
    rutaDetalle: '/ventas/comprobantes',
    vacioTitulo: 'Aún no hay facturas emitidas',
    vacioDescripcion: 'La factura se emite a clientes con RUC y se envía a SUNAT.',
  };

  documentos: RegistroDocumento[] = [
    { id: 301, numero: 'F001-000124', tercero: 'Comercial El Sol E.I.R.L.', documentoTercero: '20587654321', fecha: '05/08/2026', total: 342.0, estado: 'ACEPTADO' },
    { id: 302, numero: 'F001-000123', tercero: 'Distribuidora Andina S.A.C.', documentoTercero: '20512345678', fecha: '05/08/2026', total: 1284.5, estado: 'ACEPTADO' },
    { id: 303, numero: 'F001-000122', tercero: 'Constructora Pacífico S.A.', documentoTercero: '20456789123', fecha: '04/08/2026', total: 8940.0, estado: 'OBSERVADO' },
    { id: 304, numero: 'F001-000121', tercero: 'Distribuidora Andina S.A.C.', documentoTercero: '20512345678', fecha: '03/08/2026', total: 560.2, estado: 'ANULADO' },
  ];
}
