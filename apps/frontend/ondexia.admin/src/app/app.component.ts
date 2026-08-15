import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

import { SesionService } from './nucleo/sesion.service';

/**
 * El marco de la consola.
 *
 * <p>Deliberadamente distinto del SPA de clientes: fondo oscuro y la palabra
 * «interno» siempre visible. No es estética — es para que nadie confunda una
 * pestaña con la otra y crea que está mirando los datos de un cliente cuando
 * está mirando los de todos.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="min-h-screen bg-slate-900 text-slate-100">
      <header class="border-b border-slate-700 bg-slate-950">
        <div class="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <a routerLink="/cuentas" class="flex items-center gap-2">
            <span class="text-lg font-semibold">Ondexia</span>
            <span
              class="rounded bg-amber-500/20 px-2 py-0.5 text-xs font-medium text-amber-300"
              >Panel interno</span
            >
          </a>

          <button type="button" class="text-sm text-slate-300 hover:text-white"
                  (click)="sesion.cerrar()">
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
