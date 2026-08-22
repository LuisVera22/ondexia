import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { EncabezadoPaginaComponent } from '../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { EstadoComprobanteComponent, EstadoComprobante } from '../../shared/components/comunes/estado-comprobante/estado-comprobante.component';

interface Indicador {
  etiqueta: string;
  valor: string;
  detalle: string;
  tendencia?: 'sube' | 'baja';
  variacion?: string;
}

interface Pendiente {
  titulo: string;
  descripcion: string;
  cantidad: number;
  ruta: string;
  severidad: 'error' | 'advertencia' | 'informacion';
}

/**
 * Panel principal.
 *
 * Se construye al final del plan a propósito: diseñarlo primero habría
 * significado inventar métricas sobre entidades que aún no existían.
 *
 * Criterio de contenido: lo primero no son las ventas del mes sino lo que
 * exige atención. En un sistema de facturación, un comprobante rechazado o
 * un resumen sin declarar tiene consecuencias tributarias, y enterarse
 * tarde es el peor resultado posible.
 */
@Component({
  selector: 'app-panel',
  imports: [EncabezadoPaginaComponent, EstadoComprobanteComponent, RouterModule],
  templateUrl: './panel.component.html',
})
export class PanelComponent {
  /** Lo que exige atención hoy. Va primero, antes que cualquier métrica. */
  pendientes: Pendiente[] = [
    {
      titulo: 'Comprobantes rechazados por SUNAT',
      descripcion: 'Deben corregirse y volver a emitirse',
      cantidad: 1,
      ruta: '/ventas/facturas',
      severidad: 'error',
    },
    {
      titulo: 'Boletas sin declarar',
      descripcion: 'Pendientes de incluir en el resumen diario',
      cantidad: 2,
      ruta: '/ventas/resumen-diario',
      severidad: 'advertencia',
    },
    {
      titulo: 'Productos agotados',
      descripcion: 'No se pueden vender hasta registrar un ingreso',
      cantidad: 1,
      ruta: '/almacen/por-agotarse',
      severidad: 'advertencia',
    },
  ];

  indicadores: Indicador[] = [
    { etiqueta: 'Ventas del mes', valor: 'S/ 48 320.60', detalle: 'Agosto 2026', tendencia: 'sube', variacion: '12.4 %' },
    { etiqueta: 'Comprobantes emitidos', valor: '187', detalle: '124 boletas · 63 facturas', tendencia: 'sube', variacion: '8.1 %' },
    { etiqueta: 'Compras del mes', valor: 'S/ 29 340.00', detalle: '6 órdenes recibidas', tendencia: 'baja', variacion: '3.2 %' },
    { etiqueta: 'Aceptación en SUNAT', valor: '98.4 %', detalle: '3 observados de 187' },
  ];

  ultimosComprobantes = [
    { id: 401, numero: 'B001-008917', cliente: 'Rosa Quispe Mamani', total: 89.9, estado: 'PENDIENTE' as EstadoComprobante },
    { id: 301, numero: 'F001-000124', cliente: 'Comercial El Sol E.I.R.L.', total: 342.0, estado: 'ACEPTADO' as EstadoComprobante },
    { id: 302, numero: 'F001-000123', cliente: 'Distribuidora Andina S.A.C.', total: 1284.5, estado: 'ACEPTADO' as EstadoComprobante },
    { id: 303, numero: 'F001-000122', cliente: 'Constructora Pacífico S.A.', total: 8940.0, estado: 'OBSERVADO' as EstadoComprobante },
  ];

  get hayPendientes(): boolean {
    return this.pendientes.length > 0;
  }

  clasesSeveridad(severidad: Pendiente['severidad']): string {
    if (severidad === 'error') {
      return 'border-error-200 bg-error-50 dark:border-error-500/30 dark:bg-error-500/10';
    }
    if (severidad === 'advertencia') {
      return 'border-warning-200 bg-warning-50 dark:border-warning-500/30 dark:bg-warning-500/10';
    }
    return 'border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]';
  }

  clasesCantidad(severidad: Pendiente['severidad']): string {
    if (severidad === 'error') {
      return 'bg-error-500 text-white dark:bg-error-600';
    }
    if (severidad === 'advertencia') {
      return 'bg-warning-500 text-white';
    }
    return 'bg-gray-200 text-gray-700';
  }

  formato(valor: number): string {
    return valor.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}
