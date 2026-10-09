import { DestroyRef, Signal, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormGroup } from '@angular/forms';
import { ConfirmacionesService } from '../services/confirmaciones.service';

/** Lo que hace falta saber sobre si el formulario tiene algo pendiente. */
export interface SeguimientoDeCambios {
  /** Si el contenido actual difiere del que se cargó. */
  readonly hayCambios: Signal<boolean>;

  /**
   * Fija el contenido actual como el nuevo punto de partida.
   *
   * <p>Se llama al cargar y después de guardar con éxito. Sin lo segundo, el
   * formulario seguiría pareciendo pendiente justo después de guardarse.
   */
  fijarBase(): void;

  /** Devuelve el formulario a lo último que se fijó como base. */
  descartar(): void;

  /**
   * Lo mismo, preguntando antes.
   *
   * <p>Es lo que usan las pantallas. Descartar es la única acción de un
   * formulario que destruye trabajo del usuario sin dejar rastro, y es
   * exactamente el caso para el que existe una confirmación.
   */
  descartarConConfirmacion(): Promise<void>;
}

/**
 * Compara el formulario con el estado en que se cargó.
 *
 * <h2>Por qué no basta {@code dirty}</h2>
 *
 * <p>{@code dirty} responde a «¿ha escrito el usuario en algún campo?», no a
 * «¿hay algo distinto?». Escribir una letra y borrarla deja el formulario
 * {@code dirty} con exactamente el mismo contenido que tenía: el botón de
 * guardar queda habilitado, la marca de pendiente aparece, y al abandonar la
 * ficha se pregunta por unos cambios que no existen. Se aprende a decir «sí» sin
 * leer, y entonces la pregunta ya no protege nada.
 *
 * <p>Aquí se compara el contenido. Deshacer lo que se acababa de escribir vuelve
 * a dejar el formulario en paz, que es lo que el usuario acaba de hacer.
 *
 * <h2>Por qué la comparación es un JSON</h2>
 *
 * <p>Porque los valores son primitivos —texto, números, booleanos y nulos— y con
 * {@code getRawValue} el orden de las claves lo fija la definición del grupo, no
 * el orden en que se escribió. Comparar cadenas es exacto para este caso y no
 * necesita recorrer nada a mano. Si algún día hubiera fechas o arreglos
 * anidados, este es el sitio a cambiar.
 *
 * <p>Debe llamarse en un contexto de inyección —al declarar un campo de la
 * clase— porque se da de baja de los cambios con el ciclo de vida del
 * componente.
 */
export function seguirCambios(formulario: FormGroup): SeguimientoDeCambios {
  const referenciaDestruccion = inject(DestroyRef);
  const confirmaciones = inject(ConfirmacionesService);

  const base = signal(instantanea(formulario));
  const actual = signal(instantanea(formulario));

  formulario.valueChanges
    .pipe(takeUntilDestroyed(referenciaDestruccion))
    .subscribe(() => actual.set(instantanea(formulario)));

  return {
    hayCambios: computed(() => actual() !== base()),

    fijarBase(): void {
      const foto = instantanea(formulario);
      base.set(foto);
      actual.set(foto);
      // Se sincroniza `pristine` con esto para que las dos formas de preguntar
      // lo mismo no se contradigan en pantalla.
      formulario.markAsPristine();
    },

    descartar(): void {
      formulario.reset(JSON.parse(base()));
      actual.set(base());
      formulario.markAsPristine();
    },

    async descartarConConfirmacion(): Promise<void> {
      const seguro = await confirmaciones.pedir({
        titulo: '¿Descartar los cambios?',
        mensaje:
          'Los campos vuelven a como estaban al abrir la ficha. Lo que hayas escrito se pierde.',
        textoConfirmar: 'Descartar',
        textoCancelar: 'Seguir editando',
        peligrosa: true,
      });

      if (seguro) {
        this.descartar();
      }
    },
  };
}

function instantanea(formulario: FormGroup): string {
  return JSON.stringify(formulario.getRawValue());
}
