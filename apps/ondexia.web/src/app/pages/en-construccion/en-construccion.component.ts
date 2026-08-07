import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { PageBreadcrumbComponent } from '../../shared/components/common/page-breadcrumb/page-breadcrumb.component';

/**
 * Marcador de posición para las vistas que aún no se construyen.
 *
 * Existe para que todo el menú sea navegable desde la Etapa 0: el recorrido
 * cognitivo se puede validar antes de que exista una sola pantalla real.
 * Cada ruta pendiente declara su título y su etapa en `data`.
 */
@Component({
  selector: 'app-en-construccion',
  imports: [PageBreadcrumbComponent],
  templateUrl: './en-construccion.component.html',
})
export class EnConstruccionComponent {
  private readonly ruta = inject(ActivatedRoute);

  titulo = '';
  etapa = '';
  modulo = '';

  ngOnInit(): void {
    const datos = this.ruta.snapshot.data;
    this.titulo = datos['titulo'] ?? 'Vista pendiente';
    this.etapa = datos['etapa'] ?? '';
    this.modulo = datos['modulo'] ?? '';
  }
}
