import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { SesionService } from './sesion.service';

/**
 * Impide entrar sin sesión, y cierra el círculo del acceso.
 *
 * <h2>Aquí no hay pantalla de acceso propia</h2>
 *
 * <p>En el SPA de clientes el guardián lleva a {@code /acceso/ingresar}. La
 * consola no tiene esa ruta a propósito: quien no está autenticado va derecho a
 * la interfaz alojada de Cognito. Escribir la contraseña y resolver el segundo
 * factor ocurre en la pantalla de Cognito, no en la nuestra — no podemos filtrar
 * lo que nunca pasa por nuestro código.
 *
 * <h2>El orden de las tres comprobaciones importa</h2>
 *
 * <p>La sesión se mira <strong>primero</strong>, y no es un detalle de estilo:
 * con el canje del código antes, el guardián entraba en bucle. Ocurría así —el
 * canje funcionaba, se navegaba a {@code /cuentas}, el guardián volvía a correr
 * sobre la nueva ruta, la barra de direcciones todavía llevaba el
 * {@code ?code=…}, se intentaba canjear el mismo código por segunda vez, Cognito
 * lo rechazaba por usado y el {@code catch} mandaba otra vez a la interfaz
 * alojada. Un ciclo de recargas del que solo se sale cerrando la pestaña.
 *
 * <p>Comprobando la sesión primero, la segunda vuelta sale por la puerta buena.
 * Y se limpia además la barra de direcciones: un código de autorización usado no
 * sirve para nada, pero tampoco tiene por qué quedarse a la vista ni viajar en el
 * historial ni en un enlace compartido por descuido.
 *
 * <p>Sigue siendo comodidad y no seguridad: quien edite el JavaScript de su
 * navegador se lo salta, y lo único que verá son pantallas vacías, porque los
 * datos los sirve una API que valida el token en cada petición.
 */
export const sesionGuard: CanActivateFn = async (_ruta, estado) => {
  const sesion = inject(SesionService);
  const router = inject(Router);

  if (sesion.autenticado()) {
    return true;
  }

  const parametros = new URLSearchParams(window.location.search);
  const codigo = parametros.get('code');
  const estadoOauth = parametros.get('state');

  if (codigo && estadoOauth) {
    try {
      const destino = await sesion.completar(codigo, estadoOauth);
      limpiarLaBarraDeDirecciones();
      return router.parseUrl(destino || '/cuentas');
    } catch {
      // Código caducado, ya usado, o `state` que no corresponde a esta pestaña.
      // Insistir con el mismo código falla igual, así que se empieza de nuevo —
      // pero sin el código en la URL, o volvería a entrar por esta rama.
      limpiarLaBarraDeDirecciones();
      void sesion.iniciar('/cuentas');
      return false;
    }
  }

  void sesion.iniciar(estado.url);
  return false;
};

/** Quita `?code=…&state=…` sin recargar ni añadir una entrada al historial. */
function limpiarLaBarraDeDirecciones(): void {
  window.history.replaceState({}, '', window.location.pathname);
}
