import { Component, Input, Output, EventEmitter } from '@angular/core';

/**
 * Estado vacío de un listado.
 *
 * Criterio del plan de vistas §9.3: el estado vacío enseña. La primera vez
 * que se entra a un módulo, la pantalla explica qué es y ofrece la acción
 * para empezar, en lugar de mostrar una tabla vacía sin contexto.
 */
@Component({
  selector: 'app-pagina-vacia',
  imports: [],
  templateUrl: './pagina-vacia.component.html',
})
export class PaginaVaciaComponent {
  /** Qué no hay. Ejemplo: "Aún no hay productos registrados". */
  @Input() titulo = 'No hay información para mostrar';

  /** Para qué sirve esta sección, en una frase. */
  @Input() descripcion = '';

  /** Texto del botón. Si queda vacío, no se muestra la acción. */
  @Input() textoAccion = '';

  /** Mensaje distinto cuando el listado está vacío por los filtros aplicados. */
  @Input() porFiltros = false;

  @Output() accion = new EventEmitter<void>();
}
