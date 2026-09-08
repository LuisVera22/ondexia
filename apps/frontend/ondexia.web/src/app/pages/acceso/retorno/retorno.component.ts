import { HttpErrorResponse } from '@angular/common/http';
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

  // El backend lo marca con un código estable, no con el estado HTTP a secas:
  // un 404 cualquiera no debe mandar a nadie a registrarse.
  private static readonly SIN_REGISTRAR = 'usuario_no_registrado';

  /**
   * Mensajes FIJOS por código, nunca el texto que venga en la URL.
   *
   * <p>Hallazgo M10 de la auditoría 2026-09-01. Se pintaba
   * `error_description` tal cual, y ese parámetro lo pone quien construye el
   * enlace: bastaba mandarle a alguien
   * `…/acceso/retorno?error=x&error_description=Tu+sesión+caducó,+entra+en+…`
   * para poner un texto elegido por el atacante dentro de nuestra pantalla de
   * acceso, con nuestro dominio en la barra y nuestro diseño alrededor.
   *
   * <p>No es XSS —Angular escapa el texto— y por eso es fácil de pasar por alto:
   * lo que se inyecta no es código, es CONFIANZA. La pantalla de acceso es
   * justamente donde eso vale más.
   *
   * <p>Tampoco se comprobaba `state` en esta rama, así que el error ni siquiera
   * tenía que venir de un flujo iniciado por nosotros.
   *
   * <p>Los códigos son los de OAuth 2.0 y los de Cognito. Lo que no esté en la
   * tabla cae en un mensaje genérico: añadir el texto del servidor «solo para
   * los casos raros» reabre esto entero.
   */
  private static mensajePara(codigo: string): string {
    switch (codigo) {
      case 'access_denied':
        return 'Se canceló el acceso. Puedes intentarlo de nuevo.';
      case 'invalid_request':
      case 'invalid_client':
      case 'unauthorized_client':
        return 'No se pudo completar el acceso por un problema de configuración. Conviene avisar al soporte.';
      case 'temporarily_unavailable':
      case 'server_error':
        return 'El servicio de acceso no responde ahora mismo. Conviene reintentar en unos minutos.';
      default:
        return 'No se pudo iniciar sesión. Conviene reintentar.';
    }
  }

  private async procesar(): Promise<void> {
    const parametros = this.ruta.snapshot.queryParamMap;

    const fallo = parametros.get('error');
    if (fallo) {
      this.error.set(RetornoComponent.mensajePara(fallo));
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
      /*
       * «Usuario sin registrar» no es un fallo: es el estado normal de quien
       * acaba de crear su cuenta en Cognito. La sesión quedó bien abierta; lo
       * que falta son los datos de la empresa. Sin este desvío, el recién
       * llegado vería «no se pudo iniciar sesión» justo después de verificar
       * su correo con éxito — el mensaje más desmoralizador posible.
       */
      if (
        fallo instanceof HttpErrorResponse &&
        fallo.error?.codigo === RetornoComponent.SIN_REGISTRAR
      ) {
        void this.router.navigate(['/acceso/registro'], { replaceUrl: true });
        return;
      }

      this.error.set(
        fallo instanceof Error
          ? fallo.message
          : 'No se pudo completar el inicio de sesión. Vuelva a intentarlo.'
      );
    }
  }
}
