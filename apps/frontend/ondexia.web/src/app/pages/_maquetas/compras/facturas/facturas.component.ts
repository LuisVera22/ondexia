import { Component } from '@angular/core';
import {
  ListadoDocumentosComponent,
  ConfiguracionListado,
  RegistroDocumento,
} from '../../../shared/components/comunes/listado-documentos/listado-documentos.component';

/**
 * Listado de facturas de compra.
 *
 * Son comprobantes RECIBIDOS, no emitidos: se registran para el credito
 * fiscal y el control de cuentas por pagar. El estado que se muestra es el
 * de validacion del comprobante ante SUNAT, no el de una emision propia.
 */
@Component({
  selector: 'app-facturas-compra',
  imports: [ListadoDocumentosComponent],
  template: `<app-listado-documentos [configuracion]="configuracion" [documentos]="documentos" />`,
})
export class FacturasCompraComponent {
  configuracion: ConfiguracionListado = {
    titulo: 'Facturas de compra',
    descripcion: '',
    rutaNuevo: '/compras/facturas/nueva',
    textoNuevo: 'Registrar factura',
    esComprobanteElectronico: true,
    etiquetaTercero: 'Proveedor',
    vacioTitulo: 'Aún no hay facturas de compra registradas',
    vacioDescripcion: 'Registre las facturas recibidas de sus proveedores para el control de compras y crédito fiscal.',
    nota:
      'Estas facturas las emite el proveedor, no Ondexia. Aquí se registran para el control de compras y el crédito fiscal.',
    rutaDetalle: '',
  };

  documentos: RegistroDocumento[] = [
    { id: 1001, numero: 'F520-004821', tercero: 'Cementos Pacasmayo S.A.A.', documentoTercero: '20100113610', fecha: '05/08/2026', total: 18420.0, estado: 'ACEPTADO' },
    { id: 1002, numero: 'F001-000934', tercero: 'Ferretería Central S.A.C.', documentoTercero: '20524089107', fecha: '02/08/2026', total: 1280.0, estado: 'ACEPTADO' },
  ];
}
