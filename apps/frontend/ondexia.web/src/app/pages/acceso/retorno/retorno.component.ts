import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MarcoAccesoComponent } from '../../../shared/layout/marco-acceso/marco-acceso.component';
import { SesionService } from '../../../nucleo/sesion.service';
import { ContextoService } from '../../../shared/services/contexto.service';

/**
 * Aterrizaje del flujo de Cognito: `/acceso/retorno?code=…&state=…`.
 *
 * Dura lo que tarda una llamada. Aquí se canjea el código por tokens, se carga
 * el contexto y se devuelve al usuario a donde iba.
 *
 * Es también donde aparecen los errores de la negociación: si Cognito rechaza
 * algo, vuelve con `error` y `error_description` en la URL en vez de con un
 * código. Sin tratarlos, el usuario vería una pantalla en blanco eternamente
 * «cargando».
 */
@Component({
  selector: 'app-retorno',
  imports: [MarcoAccesoComponent, RouterModule],
  templateUrl: './retorno.component.html',
})
export class RetornoComponent {
  private readonly sesion = inject(SesionService);
  private readonly contexto = inject(ContextoService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly error = signal<string | null>(null);

  constructor() {
    void this.procesar();
  }

  private async procesar(): Promise<void> {
    const parametros = this.ruta.snapshot.queryParamMap;

    const fallo = parametros.get('error');
    if (fallo) {
      this.error.set(parametros.get('error_description') ?? fallo);
      return;
    }

    const codigo = parametros.get('code');
    const estado = parametros.get('state');

    if (!codigo || !estado) {
      // Alguien llegó aquí escribiendo la URL a mano, o volvió atrás después de
      // entrar. No es un error que merezca alarma.
      void this.router.navigate(['/acceso/ingresar']);
      return;
    }

    try {
      const destino = await this.sesion.completar(codigo, estado);

      // El contexto se carga ANTES de navegar. Si se dejara para el panel, la
      // primera pantalla se pintaría con el menú vacío y se rellenaría medio
      // segundo después, que se ve como un parpadeo.
      await this.contexto.cargar();

      void this.router.navigateByUrl(destino, { replaceUrl: true });
    } catch (fallo: unknown) {
      this.error.set(
        fallo instanceof Error
          ? fallo.message
          : 'No se pudo completar el inicio de sesión. Vuelva a intentarlo.'
      );
    }
  }
}
