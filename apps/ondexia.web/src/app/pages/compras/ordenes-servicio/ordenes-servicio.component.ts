import { Component } from '@angular/core';
import {
  ListadoDocumentosComponent,
  ConfiguracionListado,
  RegistroDocumento,
} from '../../../shared/components/comunes/listado-documentos/listado-documentos.component';

/**
 * Listado de ordenes de servicio.
 *
 * Recorre el mismo flujo que la orden de compra pero no genera movimiento
 * de existencias: se contrata un servicio, no se adquiere mercaderia.
 */
@Component({
  selector: 'app-ordenes-servicio',
  imports: [ListadoDocumentosComponent],
  template: `<app-listado-documentos [configuracion]="configuracion" [documentos]="documentos" />`,
})
export class OrdenesServicioComponent {
  configuracion: ConfiguracionListado = {
    titulo: 'Órdenes de servicio',
    descripcion: '',
    rutaNuevo: '/compras/ordenes-servicio/nueva',
    textoNuevo: 'Nueva orden de servicio',
    esComprobanteElectronico: false,
    etiquetaTercero: 'Proveedor',
    vacioTitulo: 'Aún no hay órdenes de servicio',
    vacioDescripcion: 'La orden de servicio contrata trabajos que no afectan las existencias.',
    nota:
      'Las órdenes de servicio no generan movimiento de existencias: contratan un trabajo, no adquieren mercadería.',
    rutaDetalle: '',
  };

  documentos: RegistroDocumento[] = [
    { id: 801, numero: 'OS-000037', tercero: 'Transportes del Norte E.I.R.L.', documentoTercero: '20601234567', fecha: '04/08/2026', total: 850.0, estado: 'ACEPTADO' },
  ];
}
