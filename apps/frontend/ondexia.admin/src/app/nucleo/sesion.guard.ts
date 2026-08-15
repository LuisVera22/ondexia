import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { SesionService } from './sesion.service';

/**
 * Impide entrar sin sesión, y además cierra el círculo del acceso.
 *
 * <h2>Aquí no hay pantalla de acceso propia</h2>
 *
 * <p>En el SPA de clientes el guardián lleva a {@code /acceso/ingresar}. La
 * consola no tiene esa ruta a propósito: quien no está autenticado va derecho a
 * la interfaz alojada de Cognito. Escribir la contraseña y resolver el segundo
 * factor ocurre en la pantalla de Cognito, no en la nuestra — no podemos filtrar
 * lo que nunca pasa por nuestro código.
 *
 * <p>El guardián hace también la vuelta: cuando Cognito devuelve al navegador con
 * {@code ?code=…&state=…}, canjea el código por los tokens y sigue al destino
 * original. Va aquí y no en un componente porque el guardián corre antes de
 * pintar nada, y así no se ve un parpadeo de pantalla vacía.
 *
 * <p>Sigue siendo comodidad y no seguridad: quien edite el JavaScript de su
 * navegador se lo salta y lo único que verá son pantallas vacías, porque los
 * datos los sirve una API que valida el token en cada petición.
 */
export const sesionGuard: CanActivateFn = async (_ruta, estado) => {
  const sesion = inject(SesionService);
  const router = inject(Router);

  const parametros = new URLSearchParams(window.location.search);
  const codigo = parametros.get('code');
  const estadoOauth = parametros.get('state');

  if (codigo && estadoOauth) {
    try {
      const destino = await sesion.completar(codigo, estadoOauth);
      return router.parseUrl(destino || '/cuentas');
    } catch {
      // Código caducado o reutilizado. Volver a empezar es lo único razonable:
      // insistir con el mismo código falla igual.
      void sesion.iniciar('/cuentas');
      return false;
    }
  }

  if (sesion.autenticado()) {
    return true;
  }

  void sesion.iniciar(estado.url);
  return false;
};
