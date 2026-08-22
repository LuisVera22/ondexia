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

  /**
   * Que la tabla esté vacía por un filtro y no por falta de registros.
   *
   * <p>Cambia el icono —una lupa en vez de una caja— y el aspecto de la acción,
   * que pasa a ser secundaria: quitar el filtro es volver atrás, no el siguiente
   * paso. Los textos los sigue poniendo quien llama, porque solo él sabe qué se
   * filtró.
   */
  @Input() porFiltros = false;

  @Output() accion = new EventEmitter<void>();
}
