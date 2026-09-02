import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { CONFIGURACION } from './configuracion';
import { EmpresaActivaService } from './empresa-activa.service';
import { SesionService } from './sesion.service';

/**
 * Pone el token y la empresa activa en cada llamada a nuestra API.
 *
 * Solo en las nuestras: se comprueba que la URL empiece por la base de la API.
 * Sin esa comprobación, el token viajaría también a `config.json` y —peor— a
 * cualquier servicio externo que se llame en el futuro, que es como se filtra
 * una credencial sin que nadie lo note.
 */
export const autenticacionInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  const configuracion = inject(CONFIGURACION);

  /*
   * Se compara el ORIGEN, no el prefijo de la cadena.
   *
   * Con `startsWith` y sin barra, la API en `https://api.ondexia.com` hacia que
   * `https://api.ondexia.com.ejemplo.mx` casara: el token viajaba a un dominio
   * ajeno que solo tiene que registrar un nombre que empiece igual. Es el
   * hallazgo de la tabla de bajas, y el error clasico de comparar URL como
   * texto.
   *
   * `URL` normaliza puerto y esquema, asi que tampoco cuela `http://` cuando la
   * API es `https://`.
   */
  // `consultas` va aparte porque en local vive en otro puerto. Sin esto la
  // consulta de RUC salia sin token en desarrollo, y desde el hallazgo M17 la
  // atestacion se emite para el `sub` del token: sin token no hay consulta.
  if (
    !esNuestraApi(peticion.url, configuracion.api) &&
    !esNuestraApi(peticion.url, configuracion.consultas)
  ) {
    return siguiente(peticion);
  }

  const sesion = inject(SesionService);
  const empresa = inject(EmpresaActivaService);
  const router = inject(Router);

  const firmar = (token: string | null): HttpRequest<unknown> => {
    if (!token) {
      return peticion;
    }

    const cabeceras: Record<string, string> = { Authorization: `Bearer ${token}` };

    // La empresa activa solo se manda si hay una elegida. Omitirla es
    // significativo para la API: si el usuario tiene una sola empresa, la toma;
    // si tiene varias, devuelve la lista para que elija y ningún permiso.
    const empresaId = empresa.id();
    if (empresaId) {
      cabeceras['X-Empresa-Id'] = empresaId;
    }

    return peticion.clone({ setHeaders: cabeceras });
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
        void router.navigate(['/acceso/ingresar'], {
          queryParams: { volverA: router.url },
        });
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

/**
 * Si la petición va a nuestra API. Compara origen, no texto.
 *
 * <p>Una URL relativa —las que no llevan esquema— se resuelve contra el
 * documento, así que `new URL(relativa, location.origin)` da el origen del
 * propio SPA y no coincide con el de la API. Es lo correcto: a `config.json` no
 * hay que añadirle ningún token.
 */
function esNuestraApi(url: string, api: string): boolean {
  try {
    return new URL(url, location.origin).origin === new URL(api).origin;
  } catch {
    // Una URL que no se puede analizar no es la nuestra.
    return false;
  }
}
