import { Component, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { EstadoComprobanteComponent, EstadoComprobante } from '../../../shared/components/comunes/estado-comprobante/estado-comprobante.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';

interface EventoTrazabilidad {
  momento: string;
  descripcion: string;
  detalle?: string;
}

/**
 * Detalle de un comprobante emitido — el patrón P4 del plan de vistas.
 *
 * Es la vista de solo lectura a la que se llega desde los listados de
 * facturas, boletas y notas de crédito. Muestra el estado ante SUNAT, los
 * archivos y la trazabilidad.
 *
 * También es el punto de partida de la nota de crédito: emitirla desde aquí
 * y no desde un formulario en blanco evita referenciar mal el documento de
 * origen (plan de vistas §8, Etapa 4).
 */
@Component({
  selector: 'app-detalle-comprobante',
  imports: [EncabezadoPaginaComponent, EstadoComprobanteComponent, ConfirmacionComponent, RouterModule],
  templateUrl: './detalle-comprobante.component.html',
})
export class DetalleComprobanteComponent {
  comprobante = {
    id: 302,
    tipo: 'Factura electrónica',
    numero: 'F001-000123',
    estado: 'ACEPTADO' as EstadoComprobante,
    fechaEmision: '05/08/2026',
    moneda: 'PEN',
    cliente: 'Distribuidora Andina S.A.C.',
    documentoCliente: 'RUC 20512345678',
    direccionCliente: 'Av. Colonial 2450, Cercado de Lima',
    formaPago: 'Crédito 30 días',
    gravado: 1088.56,
    igv: 195.94,
    total: 1284.5,
    tokenPublico: 'k7Qm2XpR9vLtNc4WbY8sJd',
  };

  lineas = [
    { id: 1, codigo: 'CEM-001', descripcion: 'Cemento Portland Tipo I 42.5 kg', unidad: 'BOL', cantidad: 30, valorUnitario: 27.54, total: 826.2 },
    { id: 2, codigo: 'FIE-038', descripcion: 'Fierro corrugado 3/8" x 9 m', unidad: 'UND', cantidad: 10, valorUnitario: 23.31, total: 233.1 },
    { id: 3, codigo: 'ALA-016', descripcion: 'Alambre negro nº 16', unidad: 'KG', cantidad: 5, valorUnitario: 5.76, total: 28.8 },
  ];

  trazabilidad: EventoTrazabilidad[] = [
    { momento: '05/08/2026 09:14', descripcion: 'Comprobante registrado', detalle: 'Por Carmen Rojas' },
    { momento: '05/08/2026 09:14', descripcion: 'XML generado y firmado digitalmente' },
    { momento: '05/08/2026 09:15', descripcion: 'Enviado a SUNAT' },
    { momento: '05/08/2026 09:15', descripcion: 'Aceptado por SUNAT', detalle: 'CDR 0 — La Factura numero F001-000123 ha sido aceptada' },
    { momento: '05/08/2026 09:16', descripcion: 'Enviado al cliente por correo', detalle: 'compras@andina.com' },
  ];

  confirmacionAbierta = false;

  get enlacePublico(): string {
    return `ver.ondexia.com/c/${this.comprobante.tokenPublico}`;
  }

  get puedeAnularse(): boolean {
    return this.comprobante.estado === 'ACEPTADO' || this.comprobante.estado === 'OBSERVADO';
  }

  formato(valor: number): string {
    return valor.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}
