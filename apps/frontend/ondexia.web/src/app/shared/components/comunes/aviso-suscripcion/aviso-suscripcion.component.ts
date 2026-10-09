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

  /**
   * Lo primero que se lee, en negrita y en la misma línea que el resto.
   *
   * <p>Lleva punto final: no es un encabezado, es la primera frase. Separarlo en
   * su propio renglón hacía que se leyera como el titular de una alerta, y esto
   * no alerta de nada — dice en qué estado está la cuenta.
   */
  readonly titulo: string;

  /** Hasta el correo, que se pinta aparte porque es un enlace. */
  readonly cuerpo: string;

  /** Lo que va después del correo. Casi siempre un punto. */
  readonly cierre: string;
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
 *
 * <h2>Sobre el aspecto</h2>
 *
 * <p>Usa los tokens del tema y no colores de Tailwind a pelo. Antes escribía
 * `bg-amber-50` y `bg-sky-50`, que no existen en nuestra escala: el aviso era lo
 * único de la aplicación que no cambiaba al cambiar la paleta, y nadie se habría
 * enterado hasta verlo desentonar.
 *
 * <p>El informativo va en el color de marca. No es decoración: un aviso ámbar
 * dice «algo va mal», y estar en el periodo de prueba no es que algo vaya mal.
 * El ámbar se reserva para los dos estados en los que de verdad hay algo que
 * atender.
 */
@Component({
  selector: 'app-aviso-suscripcion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (aviso(); as a) {
      <div
        class="mb-5 flex items-start gap-3 rounded-base border px-4 py-3.5"
        [class]="
          a.tono === 'atencion'
            ? 'border-warning-200 bg-warning-50 dark:border-warning-500/30 dark:bg-warning-500/10'
            : 'border-brand-100 bg-brand-50 dark:border-brand-500/30 dark:bg-brand-500/10'
        "
        role="status"
      >
        <!--
          El icono va en un círculo del color del tono y no dentro del texto:
          marca dónde empieza el aviso cuando la vista recorre la página de
          arriba abajo. «aria-hidden» porque no dice nada que el texto no diga.
        -->
        <span
          class="mt-0.5 shrink-0"
          [class]="
            a.tono === 'atencion'
              ? 'text-warning-600 dark:text-warning-400'
              : 'text-brand-500 dark:text-brand-400'
          "
          aria-hidden="true"
        >
          <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg">
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7.5v5.5M12 16.5h.01" />
          </svg>
        </span>

        <!--
          Un solo párrafo. El estado y lo que se puede hacer son la misma frase,
          y partirlos en dos renglones daba al aviso la forma de una alerta con
          titular — que es justo la silueta que este texto evita a propósito.
        -->
        <p class="text-sm leading-relaxed text-gray-700 dark:text-gray-300">
          <strong class="font-semibold text-gray-800 dark:text-white/90">{{ a.titulo }}</strong>
          {{ a.cuerpo }}<a
            [href]="'mailto:' + correo"
            class="font-medium underline-offset-2 hover:underline"
            [class]="
              a.tono === 'atencion'
                ? 'text-warning-700 dark:text-warning-400'
                : 'text-brand-600 dark:text-brand-400'
            "
            >{{ correo }}</a
          >{{ a.cierre }}
        </p>
      </div>
    }
  `,
})
export class AvisoSuscripcionComponent {
  private readonly contexto = inject(ContextoService);

  readonly correo = CORREO_CONTACTO;

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
          titulo: 'La suscripción está suspendida.',
          cuerpo:
            'Los comprobantes y los reportes se pueden seguir consultando y descargando; ' +
            'mientras esté suspendida no se pueden registrar ni emitir documentos nuevos. ' +
            'Para reactivarla, escribir a ',
          cierre: '.',
        };

      case 'CANCELADA':
        return {
          tono: 'atencion',
          titulo: 'La cuenta está cerrada.',
          cuerpo:
            'Los datos siguen aquí y se pueden consultar y exportarlos en cualquier ' +
            'momento: los comprobantes electrónicos deben conservarse cinco años y son ' +
            'del cliente. No se pueden registrar ni emitir documentos nuevos. Para ' +
            'reactivar la cuenta o pedir ayuda con la exportación, escribir a ',
          cierre: '.',
        };

      case 'EN_PRUEBA':
        return {
          tono: 'informativo',
          titulo: 'Periodo de prueba en curso.',
          cuerpo:
            'La empresa se puede configurar y el sistema se recorre entero; durante la prueba ' +
            'no se emiten comprobantes hacia SUNAT. Para activar la cuenta, escribir a ',
          cierre: '.',
        };

      default:
        return null;
    }
  });
}
