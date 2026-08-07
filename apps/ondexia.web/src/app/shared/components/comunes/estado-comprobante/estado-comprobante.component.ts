import { Component, Input } from '@angular/core';

/**
 * Estados del ciclo de vida de un comprobante electrónico.
 * Corresponden a la máquina de estados definida en el DTE §5.5.
 */
export type EstadoComprobante =
  | 'BORRADOR'
  | 'PENDIENTE'
  | 'FIRMADO'
  | 'ENVIADO'
  | 'ACEPTADO'
  | 'OBSERVADO'
  | 'RECHAZADO'
  | 'ERROR_ENVIO'
  | 'ANULADO';

type Apariencia = { texto: string; clases: string; punto: string; ayuda: string };

/**
 * Insignia de estado de un comprobante ante SUNAT.
 *
 * La distinción que más importa en toda la interfaz es borrador contra
 * emitido (plan de vistas §9.6). Por eso BORRADOR es el único estado con
 * borde punteado: se reconoce como "todavía editable" sin leer el texto.
 */
@Component({
  selector: 'app-estado-comprobante',
  imports: [],
  templateUrl: './estado-comprobante.component.html',
})
export class EstadoComprobanteComponent {
  @Input({ required: true }) estado!: EstadoComprobante;

  /** Muestra la explicación del estado junto a la insignia. */
  @Input() conAyuda = false;

  private static readonly APARIENCIAS: Record<EstadoComprobante, Apariencia> = {
    BORRADOR: {
      texto: 'Borrador',
      clases:
        'border border-dashed border-gray-300 text-gray-600 dark:border-gray-600 dark:text-gray-300',
      punto: 'bg-gray-400',
      ayuda: 'Todavía se puede editar. No se ha enviado a SUNAT.',
    },
    PENDIENTE: {
      texto: 'Pendiente',
      clases: 'bg-gray-100 text-gray-700 dark:bg-white/[0.06] dark:text-gray-300',
      punto: 'bg-gray-400',
      ayuda: 'En cola para ser enviado a SUNAT.',
    },
    FIRMADO: {
      texto: 'Firmado',
      clases: 'bg-blue-light-50 text-blue-light-600 dark:bg-blue-light-500/15 dark:text-blue-light-400',
      punto: 'bg-blue-light-500',
      ayuda: 'Firmado digitalmente, aún sin enviar.',
    },
    ENVIADO: {
      texto: 'Enviado',
      clases: 'bg-blue-light-50 text-blue-light-600 dark:bg-blue-light-500/15 dark:text-blue-light-400',
      punto: 'bg-blue-light-500',
      ayuda: 'Enviado a SUNAT. Esperando respuesta.',
    },
    ACEPTADO: {
      texto: 'Aceptado',
      clases: 'bg-success-50 text-success-700 dark:bg-success-500/15 dark:text-success-400',
      punto: 'bg-success-500',
      ayuda: 'SUNAT lo aceptó. Tiene validez legal.',
    },
    OBSERVADO: {
      texto: 'Observado',
      clases: 'bg-warning-50 text-warning-700 dark:bg-warning-500/15 dark:text-warning-400',
      punto: 'bg-warning-500',
      ayuda: 'Aceptado con observaciones. Revise el detalle.',
    },
    RECHAZADO: {
      texto: 'Rechazado',
      clases: 'bg-error-50 text-error-700 dark:bg-error-500/15 dark:text-error-400',
      punto: 'bg-error-500',
      ayuda: 'SUNAT lo rechazó. Debe corregirse y emitirse de nuevo.',
    },
    ERROR_ENVIO: {
      texto: 'Error de envío',
      clases: 'bg-error-50 text-error-700 dark:bg-error-500/15 dark:text-error-400',
      punto: 'bg-error-500',
      ayuda: 'No se pudo comunicar con SUNAT. Se reintentará automáticamente.',
    },
    ANULADO: {
      texto: 'Anulado',
      clases: 'bg-gray-100 text-gray-500 line-through dark:bg-white/[0.06] dark:text-gray-400',
      punto: 'bg-gray-400',
      ayuda: 'Dado de baja ante SUNAT.',
    },
  };

  get apariencia(): Apariencia {
    return (
      EstadoComprobanteComponent.APARIENCIAS[this.estado] ??
      EstadoComprobanteComponent.APARIENCIAS.PENDIENTE
    );
  }
}
