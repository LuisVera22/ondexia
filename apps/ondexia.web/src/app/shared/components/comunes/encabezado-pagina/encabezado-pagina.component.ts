import { Component, Input, inject } from '@angular/core';
import { Router, RouterModule } from '@angular/router';

/** Nombre visible de cada módulo, por su primer segmento de ruta. */
const MODULOS: Record<string, string> = {
  almacen: 'Almacén',
  compras: 'Compras',
  ventas: 'Ventas',
  configuracion: 'Configuración',
  perfil: 'Mi perfil',
  componentes: 'Componentes',
};

/**
 * Encabezado de vista: título y ruta de navegación.
 *
 * El módulo intermedio se deduce de la URL en lugar de recibirse como
 * parámetro. Con setenta vistas, pasarlo a mano garantiza que tarde o temprano
 * una diga «Ventas» estando en Compras; la URL, en cambio, no puede mentir.
 */
@Component({
  selector: 'app-encabezado-pagina',
  imports: [RouterModule],
  templateUrl: './encabezado-pagina.component.html',
})
export class EncabezadoPaginaComponent {
  @Input({ required: true }) titulo = '';

  private readonly router = inject(Router);

  /** Módulo al que pertenece la vista, o cadena vacía si es el panel. */
  get modulo(): string {
    const segmento = this.router.url.split('?')[0].split('#')[0].split('/')[1] ?? '';
    return MODULOS[segmento] ?? '';
  }

  get rutaModulo(): string {
    const segmento = this.router.url.split('?')[0].split('/')[1] ?? '';
    return `/${segmento}`;
  }

  /**
   * El módulo solo se enlaza si es distinto del título. En el listado de
   * clientes, «Ventas / Clientes» informa; en la ficha de empresa, repetir
   * «Configuración / Configuración» solo ocupa espacio.
   */
  get muestraModulo(): boolean {
    return Boolean(this.modulo) && this.modulo !== this.titulo;
  }
}
