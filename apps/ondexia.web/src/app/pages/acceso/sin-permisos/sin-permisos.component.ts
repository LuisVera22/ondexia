import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { PageBreadcrumbComponent } from '../../../shared/components/common/page-breadcrumb/page-breadcrumb.component';

/**
 * Acceso denegado por permisos insuficientes.
 *
 * Se muestra dentro del layout de la aplicación, no como pantalla de error
 * aislada: el usuario tiene sesión válida, solo le falta un permiso. Sacarlo
 * del layout le haría creer que perdió la sesión.
 */
@Component({
  selector: 'app-sin-permisos',
  imports: [RouterModule, PageBreadcrumbComponent],
  templateUrl: './sin-permisos.component.html',
})
export class SinPermisosComponent {}
