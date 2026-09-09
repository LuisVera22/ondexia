import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SesionService } from './sesion.service';

/**
 * Impide entrar sin sesión.
 *
 * Es comodidad, no seguridad. Quien quiera puede saltarse esta guarda editando
 * el JavaScript en su navegador, y lo único que conseguiría es ver pantallas
 * vacías: los datos los sirve la API, que valida el token en cada petición. La
 * guarda existe para que un usuario legítimo con la sesión vencida vea la
 * pantalla de acceso en vez de un panel roto lleno de errores.
 *
 * Se guarda a dónde iba para devolverlo ahí después de entrar. Sin eso, abrir
 * un enlace a un comprobante con la sesión caducada te deja en el panel
 * principal, y hay que volver a buscarlo.
 */
export const sesionGuard: CanActivateFn = (_ruta, estado) => {
  const sesion = inject(SesionService);
  const router = inject(Router);

  if (sesion.autenticado()) {
    return true;
  }

  return router.createUrlTree(['/acceso/ingresar'], {
    queryParams: { volverA: estado.url },
  });
};
