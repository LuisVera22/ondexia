import { Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * Estado visible de la acción.
 *
 * <p>{@code exito} y {@code error} son transitorios: los devuelve a
 * {@code reposo} quien los fijó. No lo hace el componente con un temporizador
 * propio porque entonces habría dos dueños del mismo dato —la entrada y el
 * temporizador interno— y bastaría que el padre volviera a pintar para
 * resucitar un estado ya caducado. Ver {@code EstadoAccion}, que es quien
 * gobierna esa secuencia.
 */
export type EstadoBoton = 'reposo' | 'cargando' | 'exito' | 'error';

export type VarianteBoton = 'primario' | 'secundario' | 'peligro' | 'icono';

/**
 * Botón con estado de la acción que dispara.
 *
 * <h2>Qué problema resuelve, además del aspecto</h2>
 *
 * <p>Bloquea el doble envío. Sin esto, dos clics rápidos en «Crear» mandan dos
 * peticiones, y en un sistema donde crear consume correlativos de comprobante
 * eso no es una molestia visual: es un documento duplicado ante SUNAT.
 *
 * <p>Y comunica la espera. Una petición que tarda un segundo sin ninguna señal
 * se interpreta como un botón que no funciona, y la reacción natural es
 * volver a pulsarlo.
 *
 * <h2>El ancho no cambia entre estados</h2>
 *
 * <p>La etiqueta no se sustituye por el icono: se mantiene en el DOM y se
 * vuelve invisible, con el icono superpuesto en el centro. Sustituyéndola, el
 * botón se encogería a la anchura del icono y todo lo que tiene al lado daría
 * un salto — justo en el instante en que el usuario está mirando ahí.
 *
 * <p>Por eso el resultado se dice con un icono y no con un texto
 * —«Guardado», «No se guardó»—. Un texto de estado obliga a elegir entre dos
 * males: o el botón cambia de ancho al cambiar de estado, o se reserva desde el
 * principio el ancho del texto más largo y entonces «Guardar» mide siempre lo
 * que mide «No se guardó». El icono no plantea ninguna de las dos. Lo que hay
 * que leer va en el aviso flotante, que tiene sitio para decirlo.
 *
 * <p>El estado sí se dice con palabras a quien usa un lector de pantalla, donde
 * no hay ancho que respetar y un icono no se anuncia.
 */
@Component({
  selector: 'app-boton',
  imports: [],
  templateUrl: './boton.component.html',
})
export class BotonComponent {
  @Input() estado: EstadoBoton = 'reposo';
  @Input() variante: VarianteBoton = 'primario';
  @Input() tipo: 'button' | 'submit' = 'button';

  /** Deshabilitado por reglas del formulario, no por la acción en curso. */
  @Input() deshabilitado = false;

  /** Obligatorio en la variante `icono`, donde no hay texto que leer. */
  @Input() etiquetaAccesible = '';

  /**
   * Id del formulario que envía, para cuando el botón vive fuera de él.
   *
   * <p>Lo necesitan los modales: el formulario va en el cuerpo y el botón en la
   * ranura de acciones, así que no son ascendiente y descendiente. Sin esto
   * habría que llamar a la acción con un clic, y entonces pulsar Enter en un
   * campo no enviaría — que es como la mitad de la gente rellena un formulario
   * corto.
   */
  @Input() formulario = '';

  @Output() accion = new EventEmitter<void>();

  get bloqueado(): boolean {
    return this.deshabilitado || this.estado === 'cargando';
  }

  get muestraIcono(): boolean {
    return this.estado !== 'reposo';
  }

  /**
   * El estado en palabras, solo para lectores de pantalla.
   *
   * <p>Sin esto, el resultado de la acción es un icono y un color: nada que se
   * pueda anunciar. {@code aria-busy} cubre la espera —y por eso {@code
   * cargando} no aparece aquí— pero no dice cómo acabó.
   *
   * <p>No es una región activa a propósito. El aviso flotante ya interrumpe con
   * el resultado, y dos regiones anunciando lo mismo a la vez se pisan. Esto se
   * lee cuando el foco vuelve al botón, que es cuando hace falta.
   */
  get estadoEnPalabras(): string {
    if (this.estado === 'exito') {
      return 'Listo';
    }
    return this.estado === 'error' ? 'No se pudo completar' : '';
  }

  get clases(): string {
    const base =
      'relative inline-flex items-center justify-center gap-2 rounded-lg text-sm font-medium' +
      ' transition disabled:cursor-not-allowed disabled:opacity-60';

    if (this.variante === 'icono') {
      return `${base} h-11 w-11 border border-gray-300 text-gray-500 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-400 dark:hover:bg-white/[0.03]`;
    }
    if (this.variante === 'secundario') {
      return `${base} px-4 py-2.5 border border-gray-300 text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-white/[0.03]`;
    }
    if (this.variante === 'peligro') {
      return `${base} px-4 py-2.5 bg-error-500 text-white hover:bg-error-600`;
    }
    return `${base} px-4 py-2.5 bg-brand-500 text-white hover:bg-brand-600`;
  }

  /**
   * El color del resultado depende de la variante.
   *
   * <p>Sobre un botón primario, que ya es de color, el icono va en blanco: un
   * check verde sobre azul se lee mal y en modo oscuro casi desaparece. Sobre
   * los claros sí se usa el color, que es donde aporta significado.
   */
  get clasesIcono(): string {
    const sobreColor = this.variante === 'primario' || this.variante === 'peligro';
    if (sobreColor) {
      return 'text-white';
    }
    if (this.estado === 'exito') {
      return 'text-success-500';
    }
    return this.estado === 'error' ? 'text-error-500' : 'text-brand-500';
  }
}
