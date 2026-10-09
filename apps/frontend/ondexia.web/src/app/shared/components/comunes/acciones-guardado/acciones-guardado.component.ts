import { Component, EventEmitter, Input, Output } from '@angular/core';
import { BotonComponent, EstadoBoton } from '../boton/boton.component';

/**
 * El pie de un formulario de edición: qué está pendiente y qué se puede hacer.
 *
 * <h2>Por qué las tres cosas van juntas</h2>
 *
 * <p>La marca de pendiente, el botón de descartar y el de guardar responden a la
 * misma pregunta —¿hay algo sin guardar?— y antes cada ficha la contestaba por
 * su cuenta con un botón siempre habilitado. Un «Guardar cambios» activo en un
 * formulario intacto invita a pulsarlo para comprobar si había algo, y manda una
 * petición que no cambia nada.
 *
 * <p>Juntas, además, no pueden contradecirse: no hay forma de que aparezca la
 * marca de pendiente con el botón apagado.
 *
 * <h2>El botón se apaga, no desaparece</h2>
 *
 * <p>Que se vaya el control con el que se guarda deja al usuario buscando cómo
 * guardar. Apagado dice lo mismo —no hay nada que guardar— sin mover nada de
 * sitio.
 */
@Component({
  selector: 'app-acciones-guardado',
  imports: [BotonComponent],
  template: `
    <div class="flex items-center justify-end gap-3">
      @if (hayCambios) {
      <span
        class="inline-flex items-center gap-1.5 rounded-full bg-warning-50 px-2.5 py-1 text-xs font-medium text-warning-700 dark:bg-warning-500/15 dark:text-warning-400"
      >
        <span class="h-1.5 w-1.5 rounded-full bg-warning-500" aria-hidden="true"></span>
        Sin guardar
      </span>

      <app-boton variante="secundario" [deshabilitado]="procesando" (accion)="descartar.emit()">
        {{ textoDescartar }}
      </app-boton>
      }

      <!--
        El formulario se pasa por id, no se envuelve: este componente vive en el
        pie y el formulario está arriba, y en las fichas largas hay más de una
        sección. Sin esto, Enter en un campo no enviaría nada.
      -->
      <app-boton
        tipo="submit"
        [estado]="estado"
        [deshabilitado]="!hayCambios"
        [formulario]="formulario"
      >
        {{ textoGuardar }}
      </app-boton>
    </div>
  `,
})
export class AccionesGuardadoComponent {
  /** Si hay algo distinto de lo que se cargó. Ver {@code seguirCambios}. */
  @Input() hayCambios = false;

  @Input() estado: EstadoBoton = 'reposo';

  /** Id del formulario que envía el botón. */
  @Input() formulario = '';

  @Input() textoGuardar = 'Guardar cambios';
  @Input() textoDescartar = 'Descartar';

  /** Bloquea descartar mientras se guarda. */
  @Input() procesando = false;

  @Output() descartar = new EventEmitter<void>();
}
