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
 *
 * <p>La tabla reproduce la de {@code ondexia.web} —tarjeta con borde redondeado,
 * cabecera en versalitas, filas separadas por una línea tenue— para que quien
 * trabaja en los dos sitios no tenga que aprender dos lecturas distintas de la
 * misma información.
 */
@Component({
  selector: 'app-cuentas',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
      <h1 class="text-xl font-semibold text-gray-800 dark:text-white/90">Cuentas</h1>

      @if (!cargando() && !error()) {
        <p class="text-dato text-gray-500 dark:text-gray-400">
          {{ cuentas().length }} {{ cuentas().length === 1 ? 'cuenta' : 'cuentas' }}
        </p>
      }
    </div>

    @if (error()) {
      <div
        class="rounded-2xl border border-error-200 bg-error-25 p-4 text-dato text-error-700
               dark:border-error-500/30 dark:bg-error-500/10 dark:text-error-400"
      >
        {{ error() }}
      </div>
    } @else {
      <div
        class="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-apoyo
               dark:border-gray-800 dark:bg-white/[0.03]"
      >
        @if (cargando()) {
          <!-- Bloques grises del alto de una fila, como en el SPA de clientes: la
               tabla no da un salto cuando llegan los datos. -->
          <div class="p-6">
            @for (fila of [1, 2, 3, 4, 5]; track fila) {
              <div class="mb-3 h-11 animate-pulse rounded-lg bg-gray-100 dark:bg-white/[0.05]"></div>
            }
          </div>
        } @else {
          <div class="overflow-x-auto">
            <table class="w-full min-w-[640px] text-left">
              <thead class="border-b border-gray-200 dark:border-gray-800">
                <tr>
                  @for (columna of COLUMNAS; track columna) {
                    <th
                      class="px-5 py-3.5 text-xs font-medium tracking-wide text-gray-500 uppercase
                             dark:text-gray-400"
                    >
                      {{ columna }}
                    </th>
                  }
                </tr>
              </thead>

              <tbody class="divide-y divide-gray-100 dark:divide-gray-800">
                @for (cuenta of cuentas(); track cuenta.id) {
                  <tr class="transition hover:bg-gray-50 dark:hover:bg-white/[0.02]">
                    <!-- El titular arriba y la empresa debajo, y no al reves.
                         El nombre se repite entre cuentas; el correo no. -->
                    <td class="px-5 py-4">
                      <a
                        class="text-dato font-medium text-brand-500 transition hover:text-brand-600"
                        [routerLink]="['/cuentas', cuenta.id]"
                      >
                        {{ cuenta.titular }}
                      </a>
                      <p class="mt-0.5 text-menudo text-gray-500 dark:text-gray-400">
                        {{ cuenta.nombre }}
                      </p>
                    </td>

                    <td class="px-5 py-4 text-dato text-gray-700 dark:text-gray-300">
                      {{ cuenta.planNombre }}
                    </td>

                    <td class="px-5 py-4">
                      <span
                        class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1
                               text-xs font-medium"
                        [class]="insignia(cuenta).fondo"
                      >
                        <span class="h-1.5 w-1.5 rounded-full" [class]="insignia(cuenta).punto"></span>
                        {{ insignia(cuenta).texto }}
                      </span>
                    </td>

                    <td
                      class="px-5 py-4 text-dato tabular-nums"
                      [class]="alLimite(cuenta.empresas, cuenta.limiteEmpresas)"
                    >
                      {{ consumo(cuenta.empresas, cuenta.limiteEmpresas) }}
                    </td>

                    <td
                      class="px-5 py-4 text-dato tabular-nums"
                      [class]="alLimite(cuenta.usuarios, cuenta.limiteUsuarios)"
                    >
                      {{ consumo(cuenta.usuarios, cuenta.limiteUsuarios) }}
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="5" class="px-5 py-12 text-center">
                      <p class="text-dato font-medium text-gray-700 dark:text-gray-300">
                        Todavía no hay cuentas
                      </p>
                      <p class="mt-1 text-dato text-gray-500 dark:text-gray-400">
                        Aparecerán aquí en cuanto alguien se registre.
                      </p>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    }
  `,
})
export class CuentasComponent {
  private readonly api = inject(PanelApiService);

  // «Titular» y no «Cuenta»: la columna muestra el correo de quien la abrio,
  // porque `nombre` es la razon social de su primera empresa y se repite.
  protected readonly COLUMNAS = ['Titular', 'Plan', 'Estado', 'Empresas', 'Usuarios'];

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

  /**
   * Rojo al llegar al tope, y no antes.
   *
   * <p>El aviso es que la cuenta ya no puede crecer sin cambiar de plan, no que
   * le quede poco: teñir a partir de un porcentaje convertiría el color en ruido
   * y dejaría de leerse cuando de verdad importa.
   */
  alLimite(usados: number, limite: number | null): string {
    return limite !== null && usados >= limite
      ? 'font-medium text-error-600 dark:text-error-400'
      : 'text-gray-700 dark:text-gray-300';
  }

  /**
   * El color dice qué puede hacer la cuenta hoy, no si nos gusta su situación.
   *
   * <p>{@code EN_PRUEBA} en informativo porque es un estado de trabajo normal
   * —aunque no pueda emitir hacia SUNAT—, {@code ACTIVA} en verde, y todo lo
   * demás en rojo: {@code SUSPENDIDA} y {@code CANCELADA} significan que alguien
   * al otro lado no puede trabajar.
   */
  insignia(cuenta: CuentaResumen): { texto: string; fondo: string; punto: string } {
    const texto = cuenta.estadoSuscripcion.replace('_', ' ').toLowerCase();

    switch (cuenta.estadoSuscripcion) {
      case 'ACTIVA':
        return {
          texto,
          fondo: 'bg-success-50 text-success-700 dark:bg-success-500/15 dark:text-success-400',
          punto: 'bg-success-500',
        };
      case 'EN_PRUEBA':
        return {
          texto,
          fondo:
            'bg-blue-light-50 text-blue-light-700 dark:bg-blue-light-500/15 dark:text-blue-light-400',
          punto: 'bg-blue-light-500',
        };
      default:
        return {
          texto,
          fondo: 'bg-error-50 text-error-700 dark:bg-error-500/15 dark:text-error-400',
          punto: 'bg-error-500',
        };
    }
  }
}
