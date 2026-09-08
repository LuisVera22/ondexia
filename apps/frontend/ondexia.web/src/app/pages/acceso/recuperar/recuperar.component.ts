import { Component, inject, signal } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { MarcoAccesoComponent } from '../../../shared/layout/marco-acceso/marco-acceso.component';
import { SesionService } from '../../../nucleo/sesion.service';

/**
 * Recuperación de contraseña.
 *
 * <p>El formulario de correo que había aquí venía de la maqueta y no enviaba
 * nada: mostraba «revisa tu bandeja» sin que ningún correo saliera. Un control
 * que confirma algo que no ocurrió es el peor tipo de control.
 *
 * <p>Quien de verdad sabe si el correo existe, manda el código y valida la
 * contraseña nueva es Cognito, así que esta pantalla explica y lleva ahí. Al
 * terminar, Cognito devuelve a la aplicación con la sesión ya abierta.
 */
@Component({
  selector: 'app-recuperar',
  imports: [MarcoAccesoComponent, RouterModule],
  templateUrl: './recuperar.component.html',
})
export class RecuperarComponent {
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);

  readonly enviando = signal(false);
  readonly error = signal<string | null>(null);

  async recuperar(): Promise<void> {
    this.enviando.set(true);
    this.error.set(null);

    try {
      // No retorna: la pestaña navega a Cognito.
      await this.sesion.recuperar();
    } catch {
      this.enviando.set(false);
      this.error.set('No se pudo contactar con el servicio de acceso. Conviene reintentar.');
    }
  }

  volver(): void {
    void this.router.navigate(['/acceso/ingresar']);
  }
}
