import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { CuentaResumen, PanelApiService } from '../../nucleo/panel.api';

/**
 * El listado de cuentas cliente con su consumo.
 *
 * <p>El consumo se pinta como «3 de 5» y no como una barra de progreso, porque lo
 * que se necesita saber es si queda sitio, no qué porcentaje se lleva usado. Y
 * cuando el límite es nulo dice <strong>«sin límite»</strong>: pintar «3 de 0»
 * llevaría a subir de plan a quien no lo necesita.
 */
@Component({
  selector: 'app-cuentas',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="mb-4 text-xl font-semibold">Cuentas</h1>

    @if (error()) {
      <p class="rounded border border-red-500/40 bg-red-500/10 p-3 text-sm text-red-200">
        {{ error() }}
      </p>
    } @else if (cargando()) {
      <p class="text-sm text-slate-400">Cargando…</p>
    } @else {
      <div class="overflow-x-auto rounded-lg border border-slate-700">
        <table class="w-full text-left text-sm">
          <thead class="bg-slate-800 text-slate-300">
            <tr>
              <th class="px-3 py-2 font-medium">Cuenta</th>
              <th class="px-3 py-2 font-medium">Plan</th>
              <th class="px-3 py-2 font-medium">Estado</th>
              <th class="px-3 py-2 font-medium">Empresas</th>
              <th class="px-3 py-2 font-medium">Usuarios</th>
            </tr>
          </thead>
          <tbody>
            @for (cuenta of cuentas(); track cuenta.id) {
              <tr class="border-t border-slate-800 hover:bg-slate-800/50">
                <td class="px-3 py-2">
                  <a class="text-sky-300 hover:underline" [routerLink]="['/cuentas', cuenta.id]">
                    {{ cuenta.nombre }}
                  </a>
                </td>
                <td class="px-3 py-2">{{ cuenta.planNombre }}</td>
                <td class="px-3 py-2">
                  <span class="rounded px-2 py-0.5 text-xs" [class]="colorEstado(cuenta)">
                    {{ cuenta.estadoSuscripcion }}
                  </span>
                </td>
                <td class="px-3 py-2" [class.text-amber-300]="cuenta.limiteEmpresas !== null && cuenta.empresas >= cuenta.limiteEmpresas">
                  {{ consumo(cuenta.empresas, cuenta.limiteEmpresas) }}
                </td>
                <td class="px-3 py-2" [class.text-amber-300]="cuenta.limiteUsuarios !== null && cuenta.usuarios >= cuenta.limiteUsuarios">
                  {{ consumo(cuenta.usuarios, cuenta.limiteUsuarios) }}
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="5" class="px-3 py-6 text-center text-slate-400">
                  Todavía no hay cuentas.
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class CuentasComponent {
  private readonly api = inject(PanelApiService);

  readonly cuentas = signal<CuentaResumen[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    this.api
      .cuentas()
      .then((cuentas) => this.cuentas.set(cuentas))
      .catch(() => this.error.set('No se pudieron cargar las cuentas.'))
      .finally(() => this.cargando.set(false));
  }

  /** Nulo es sin límite. Pintarlo como «de 0» seria decir lo contrario. */
  consumo(usados: number, limite: number | null): string {
    return limite === null ? `${usados} · sin límite` : `${usados} de ${limite}`;
  }

  colorEstado(cuenta: CuentaResumen): string {
    switch (cuenta.estadoSuscripcion) {
      case 'ACTIVA':
        return 'bg-emerald-500/20 text-emerald-300';
      case 'EN_PRUEBA':
        return 'bg-sky-500/20 text-sky-300';
      default:
        return 'bg-amber-500/20 text-amber-300';
    }
  }
}
