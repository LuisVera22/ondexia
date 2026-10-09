import { Component } from '@angular/core';
import {
  ListadoDocumentosComponent,
  ConfiguracionListado,
  RegistroDocumento,
} from '../../../shared/components/comunes/listado-documentos/listado-documentos.component';

/**
 * Listado de boletas emitidas.
 *
 * Comprobante electronico tipo 03. No se envia individualmente: se
 * declara mediante el resumen diario, que es asincrono.
 */
@Component({
  selector: 'app-boletas',
  imports: [ListadoDocumentosComponent],
  template: `<app-listado-documentos [configuracion]="configuracion" [documentos]="documentos" />`,
})
export class BoletasComponent {
  configuracion: ConfiguracionListado = {
    titulo: 'Boletas',
    descripcion: '',
    rutaNuevo: '/ventas/boletas/nueva',
    textoNuevo: 'Emitir boleta',
    esComprobanteElectronico: true,
    etiquetaTercero: 'Cliente',
    rutaDetalle: '/ventas/comprobantes',
    vacioTitulo: 'Aún no hay boletas emitidas',
    vacioDescripcion: 'La boleta se emite al consumidor final y se declara mediante el resumen diario.',
    nota: 'Las boletas se declaran ante SUNAT mediante el resumen diario, no una por una. Hasta enviarse el resumen del día permanecen en estado pendiente.',
  };

  documentos: RegistroDocumento[] = [
    { id: 401, numero: 'B001-008917', tercero: 'Rosa Quispe Mamani', documentoTercero: '45678912', fecha: '06/08/2026', total: 89.9, estado: 'PENDIENTE' },
    { id: 402, numero: 'B001-008916', tercero: 'Carlos Mendoza Ríos', documentoTercero: '09876543', fecha: '06/08/2026', total: 156.4, estado: 'PENDIENTE' },
    { id: 403, numero: 'B001-008915', tercero: 'Cliente varios', documentoTercero: '', fecha: '05/08/2026', total: 24.0, estado: 'ACEPTADO' },
  ];
}
