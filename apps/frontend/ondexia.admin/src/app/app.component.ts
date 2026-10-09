import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

import { SesionService } from './nucleo/sesion.service';

/**
 * El marco de la consola.
 *
 * <h2>El mismo sistema de diseño, no un tema aparte</h2>
 *
 * <p>Los colores, la tipografía y las sombras salen de {@code styles.css}, que es
 * copia del de {@code ondexia.web}. La primera versión de esta consola usaba
 * clases {@code slate-*} de las que trae Tailwind de fábrica, y no pintaban nada:
 * el tema declara <code>--color-*: initial</code> antes de definir su propia
 * escala, así que esas clases existen en el marcado pero no tienen ninguna regla
 * detrás. Se veía «casi bien», que es la peor forma de estar roto.
 *
 * <h2>La barra oscura sigue siendo a propósito</h2>
 *
 * <p>Las páginas son claras, como en el SPA de clientes, pero la barra superior
 * es oscura y lleva la palabra «interno» siempre visible. No es estética: es para
 * que nadie confunda una pestaña con la otra y crea que mira los datos de un
 * cliente cuando mira los de todos. Antes ese aviso lo daba el fondo entero; al
 * adoptar el sistema de diseño pasa a darlo la barra, que es lo único que se ve
 * en todas las pantallas.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="min-h-screen bg-gray-50 dark:bg-gray-900">
      <header class="sticky top-0 z-10 border-b border-gray-800 bg-gray-900">
        <div class="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3.5">
          <a routerLink="/cuentas" class="flex items-center gap-2.5">
            <span class="text-lg font-semibold text-white">Ondexia</span>

            <!-- El aviso de que esto no es la aplicación de un cliente. Usa la
                 escala warning, que en este sistema significa «mira dos veces
                 antes de tocar», y no un color suelto elegido aquí. -->
            <span
              class="inline-flex items-center gap-1.5 rounded-full bg-warning-500/15 px-2.5 py-1 text-menudo font-medium text-warning-300"
            >
              <span class="h-1.5 w-1.5 rounded-full bg-warning-400"></span>
              Panel interno
            </span>
          </a>

          <button
            type="button"
            class="rounded-lg px-3 py-1.5 text-dato text-gray-300 transition hover:bg-white/10 hover:text-white"
            (click)="sesion.cerrar()"
          >
            Salir
          </button>
        </div>
      </header>

      <main class="mx-auto max-w-6xl px-4 py-6">
        <router-outlet />
      </main>
    </div>
  `,
})
export class AppComponent {
  readonly sesion = inject(SesionService);
}
