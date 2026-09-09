import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';

import { routes } from './app.routes';
import { autenticacionInterceptor } from './nucleo/autenticacion.interceptor';
import { cargarConfiguracion } from './nucleo/configuracion';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),

    provideHttpClient(withInterceptors([autenticacionInterceptor])),

    // Lee config.json antes de arrancar. Va después de provideHttpClient
    // porque lo necesita, y antes de cualquier cosa que llame a la API.
    cargarConfiguracion(),

    provideRouter(
      routes,
      /*
       * Al navegar se vuelve arriba, y al volver atrás se recupera la posición
       * anterior. Sin esto, abrir la ficha de un producto desde la fila 40 de
       * un listado deja la ficha desplazada por la mitad, y regresar al listado
       * lo devuelve al principio perdiendo el sitio donde estabas leyendo.
       */
      withInMemoryScrolling({
        scrollPositionRestoration: 'enabled',
        anchorScrolling: 'enabled',
      })
    ),
  ],
};
