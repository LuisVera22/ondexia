import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { CONFIGURACION } from './configuracion';
import { SesionService } from './sesion.service';

/**
 * Pone el token en cada llamada a nuestra API.
 *
 * Solo en las nuestras: se comprueba que la URL empiece por la base de la API.
 * Sin esa comprobación, el token viajaría también a `config.json` y —peor— a
 * cualquier servicio externo que se llame en el futuro, que es como se filtra
 * una credencial sin que nadie lo note.
 */
export const autenticacionInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  const configuracion = inject(CONFIGURACION);

  if (!peticion.url.startsWith(configuracion.api)) {
    return siguiente(peticion);
  }

  const sesion = inject(SesionService);
  const router = inject(Router);

  const firmar = (token: string | null): HttpRequest<unknown> => {
    if (!token) {
      return peticion;
    }

    // Sin cabecera de empresa: aqui no hay «empresa activa» que elegir. El
    // panel no opera dentro de un inquilino, los mira todos.
    return peticion.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  };

  // Si ya se sabe que el token está vencido, se renueva ANTES de gastar una
  // llamada que va a fallar. Ahorra un ida y vuelta en cada primera petición
  // después de una hora de inactividad.
  const preparada = sesion.caducado()
    ? from(sesion.renovar()).pipe(switchMap((token) => siguiente(firmar(token))))
    : siguiente(firmar(sesion.tokenDeAcceso()));

  return preparada.pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }

      if (error.status === 401) {
        /*
         * Un 401 con el token todavía vigente significa que el problema no es
         * la caducidad: revocación, cambio de contraseña, o el usuario
         * desactivado en la base. Reintentar sería inútil.
         *
         * El caso de carrera —el token vence entre el envío y la llegada— ya lo
         * cubre el margen de un minuto de SesionService.
         */
        sesion.limpiar();
        void router.navigate(['/']);
        return throwError(() => error);
      }

      if (error.status === 403) {
        // Autenticado pero sin permiso. No se cierra la sesión: eso convertiría
        // «no puedes ver esta pantalla» en «te hemos echado».
        void router.navigate(['/sin-permisos']);
      }

      /*
       * Sesión válida pero alta sin completar. Cubre el caso que el retorno no
       * alcanza: quien cerró la pestaña a mitad del registro y vuelve mañana —
       * la guarda lo deja pasar (tiene sesión) y la primera llamada a la API
       * cae aquí. Se filtra por el código estable, no por el 404 a secas: un
       * «no encontrado» cualquiera no debe mandar a nadie a registrarse.
       */
      if (error.status === 404 && error.error?.codigo === 'usuario_no_registrado') {
        void router.navigate(['/acceso/registro']);
      }

      return throwError(() => error);
    })
  );
};
