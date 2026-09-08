import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MarcoAccesoComponent } from '../../../shared/layout/marco-acceso/marco-acceso.component';
import { CONFIGURACION } from '../../../nucleo/configuracion';
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

  /**
   * Si se ofrece «Crear cuenta».
   *
   * Lo decide el despliegue, no el SPA: con el autoservicio cerrado —como está
   * desde el hallazgo C2— Cognito responde a /signup con un error, y un botón
   * que lleva a un error es peor que no tener botón. Ver `configuracion.ts`.
   */
  readonly autoservicio = inject(CONFIGURACION).autoservicio;

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
      await this.sesion.iniciar(destinoSeguro(this.ruta.snapshot.queryParamMap.get('volverA')));
    } catch {
      this.enviando.set(false);
      this.error.set('No se pudo contactar con el servicio de acceso. Conviene reintentar.');
    }
  }

  /**
   * Lleva al alta de Cognito. Al confirmar el correo, la vuelta es el mismo
   * retorno de siempre; el contexto responde «usuario sin registrar» y de ahí
   * se pasa a completar los datos de la empresa.
   */
  async crearCuenta(): Promise<void> {
    this.enviando.set(true);
    this.error.set(null);

    try {
      await this.sesion.registrarse();
    } catch {
      this.enviando.set(false);
      this.error.set('No se pudo contactar con el servicio de acceso. Conviene reintentar.');
    }
  }
}

/**
 * `volverA` solo puede ser una ruta interna.
 *
 * <p>Tabla de bajas de la auditoría 2026-09-01. El valor viene de la URL y se
 * usaba tal cual: `//ejemplo.mx` es una ruta relativa al protocolo, así que
 * navegar a ella sale del sitio. No era un redireccionamiento abierto de verdad
 * —el destino se guarda y se usa DESPUÉS de volver de Cognito, dentro del
 * enrutador de Angular— pero sí un aterrizaje forzado a una ruta elegida por
 * quien construyó el enlace. Una barra, y no dos.
 */
function destinoSeguro(volverA: string | null): string {
  return volverA && /^\/(?!\/)/.test(volverA) ? volverA : '/';
}
