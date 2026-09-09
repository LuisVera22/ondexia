import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';

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
       * Los parámetros de la ruta llegan como entradas del componente.
       *
       * Sin esto, `id = input.required<string>()` en la ficha de una cuenta no
       * recibe nunca el `:id` de la URL y leerlo revienta con NG0950 —«required
       * input is accessed before a value is set»— dejando la pantalla en
       * «Cargando…» para siempre. El listado seguía funcionando, así que el
       * fallo solo aparecía al entrar en una cuenta.
       *
       * Es una divergencia consciente respecto de ondexia.web, que lee los
       * parámetros con ActivatedRoute. Aquí los componentes ya están escritos
       * con señales, y `input.required` es su forma natural; mezclar las dos
       * habría sido peor que tener dos convenciones separadas por proyecto.
       */
      withComponentInputBinding(),

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
