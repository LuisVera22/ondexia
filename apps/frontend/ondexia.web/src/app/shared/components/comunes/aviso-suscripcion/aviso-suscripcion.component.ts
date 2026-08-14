import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { ContextoService } from '../../../services/contexto.service';

/**
 * Correo de contacto que aparece en el aviso.
 *
 * <p>Es un Gmail personal, y es lo que hay hoy. Vive aquí y no repartido por las
 * tres cadenas para que cambiarlo por un `soporte@ondexia.com` sea una línea.
 */
const CORREO_CONTACTO = 'luis26.ml143@gmail.com';

interface Aviso {
  readonly tono: 'informativo' | 'atencion';
  readonly titulo: string;
  readonly cuerpo: string;
}

/**
 * El anuncio del estado de la suscripción.
 *
 * <h2>Por qué está redactado así y no como un aviso de cobro</h2>
 *
 * Un mensaje de «tu suscripción venció → pulsa aquí → introduce tu tarjeta» es la
 * silueta exacta de la estafa de suplantación más común. Un producto que entrena
 * a sus clientes a seguir ese flujo los deja indefensos el día que alguien nos
 * suplante. De ahí tres reglas, del doc 09 §5.1:
 *
 * 1. **Sin urgencia fabricada.** Ni cuentas atrás ni «actúa ahora».
 * 2. **Sin gancho.** Ninguna promoción dentro de un aviso de estado.
 * 3. **Decir primero qué sigue funcionando**, que además es la frase que ningún
 *    fraude escribe.
 *
 * Y ningún botón que lleve a un formulario de pago: el aviso informa y remite a
 * un correo nuestro. Cobrar no ocurre dentro de la aplicación.
 */
@Component({
  selector: 'app-aviso-suscripcion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (aviso(); as a) {
      <div
        class="mb-4 rounded-lg border px-4 py-3"
        [class]="
          a.tono === 'atencion'
            ? 'border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-200'
            : 'border-sky-300 bg-sky-50 text-sky-900 dark:border-sky-500/40 dark:bg-sky-500/10 dark:text-sky-200'
        "
        role="status"
      >
        <p class="text-sm font-semibold">{{ a.titulo }}</p>
        <p class="mt-1 text-sm">{{ a.cuerpo }}</p>
      </div>
    }
  `,
})
export class AvisoSuscripcionComponent {
  private readonly contexto = inject(ContextoService);

  /**
   * El estado manda, no `soloLectura`.
   *
   * Son dos campos porque responden a preguntas distintas: `soloLectura` decide
   * qué se puede hacer —y esa regla vive en el servidor, no aquí— y el estado
   * decide qué se dice. Una cuenta cancelada y una suspendida no escriben las
   * dos, pero no reciben el mismo mensaje.
   */
  readonly aviso = computed<Aviso | null>(() => {
    switch (this.contexto.cuenta()?.estadoSuscripcion) {
      case 'SUSPENDIDA':
        return {
          tono: 'atencion',
          titulo: 'Tu suscripción está suspendida',
          cuerpo:
            'Puedes seguir consultando y descargando todos tus comprobantes y reportes. ' +
            'Mientras esté suspendida no se pueden registrar ni emitir documentos nuevos. ' +
            `Escríbenos a ${CORREO_CONTACTO} y lo regularizamos.`,
        };

      case 'CANCELADA':
        return {
          tono: 'atencion',
          titulo: 'Tu cuenta está cerrada',
          cuerpo:
            'Tus datos siguen aquí: puedes consultarlos y exportarlos cuando quieras. ' +
            'Los comprobantes electrónicos deben conservarse cinco años y son tuyos. ' +
            'No se pueden registrar ni emitir documentos nuevos. ' +
            `Si quieres reactivarla o necesitas ayuda para exportar, escríbenos a ${CORREO_CONTACTO}.`,
        };

      case 'EN_PRUEBA':
        return {
          tono: 'informativo',
          titulo: 'Estás en el periodo de prueba',
          cuerpo:
            'Puedes configurar tu empresa y recorrer el sistema completo. ' +
            'Durante la prueba no se emiten comprobantes hacia SUNAT. ' +
            `Cuando quieras activar tu cuenta, escríbenos a ${CORREO_CONTACTO}.`,
        };

      default:
        return null;
    }
  });
}
