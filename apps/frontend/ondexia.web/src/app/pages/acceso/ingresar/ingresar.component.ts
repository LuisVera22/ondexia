import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MarcoAccesoComponent } from '../../../shared/layout/marco-acceso/marco-acceso.component';
import { SesionService } from '../../../nucleo/sesion.service';

/**
 * Puerta de entrada. Ya no pide credenciales: las pide Cognito.
 *
 * El formulario de correo y contraseña que había aquí se retiró al conectar el
 * backend, y no por falta de ganas de conservarlo. El pool tiene el segundo
 * factor en OPTIONAL, verificación de correo y recuperación de contraseña; cada
 * una es una negociación con retos —MFA_SETUP, SOFTWARE_TOKEN_MFA,
 * NEW_PASSWORD_REQUIRED— que habría que implementar aquí y mantener después.
 *
 * A cambio, la contraseña del usuario no pasa nunca por nuestro código, que es
 * la clase de garantía que no se consigue escribiendo con cuidado.
 */
@Component({
  selector: 'app-ingresar',
  imports: [MarcoAccesoComponent],
  templateUrl: './ingresar.component.html',
})
export class IngresarComponent {
  private readonly sesion = inject(SesionService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly enviando = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    // Si ya hay sesión, no tiene sentido enseñar esta pantalla.
    if (this.sesion.autenticado()) {
      void this.router.navigate(['/']);
    }
  }

  async entrar(): Promise<void> {
    this.enviando.set(true);
    this.error.set(null);

    try {
      // No retorna: la pestaña navega a Cognito.
      await this.sesion.iniciar(this.ruta.snapshot.queryParamMap.get('volverA') ?? '/');
    } catch {
      this.enviando.set(false);
      this.error.set('No se pudo contactar con el servicio de acceso. Inténtalo de nuevo.');
    }
  }
}
