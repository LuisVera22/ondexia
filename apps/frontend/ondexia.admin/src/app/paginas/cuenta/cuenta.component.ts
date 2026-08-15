import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { CuentaResumen, ModuloContratado, PanelApiService } from '../../nucleo/panel.api';

/**
 * La ficha de una cuenta: plan, estado y módulos.
 *
 * <h2>Se muestra la decisión y el efecto, no solo el efecto</h2>
 *
 * <p>Un módulo puede estar encendido porque lo trae el plan o porque alguien lo
 * encendió para esta cuenta, y son situaciones distintas: la primera cambia sola
 * si cambia el plan, la segunda no. Enseñar solo el resultado dejaría al operador
 * sin la única pregunta que se hace al mirar esta pantalla — «¿esto está así
 * porque alguien lo decidió?».
 *
 * <p>Por eso hay tres estados por módulo y no dos: <em>según el plan</em>,
 * <em>forzado a sí</em> y <em>forzado a no</em>.
 */
@Component({
  selector: 'app-cuenta',
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (cuenta(); as c) {
      <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 class="text-xl font-semibold text-gray-800 dark:text-white/90">{{ c.nombre }}</h1>
          <p class="mt-1 text-dato text-gray-500 dark:text-gray-400">
            {{ c.empresas }} {{ c.empresas === 1 ? 'empresa' : 'empresas' }} ·
            {{ c.usuarios }} {{ c.usuarios === 1 ? 'usuario' : 'usuarios' }}
          </p>
        </div>

        <a
          routerLink="/cuentas"
          class="rounded-lg border border-gray-300 px-3.5 py-2 text-dato text-gray-600 transition
                 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-white/[0.03]"
        >
          Volver
        </a>
      </div>

      <section
        class="mb-6 rounded-2xl border border-gray-200 bg-white p-5 shadow-apoyo
               dark:border-gray-800 dark:bg-white/[0.03]"
      >
        <h2 class="mb-4 font-medium text-gray-800 dark:text-white/90">Plan y estado</h2>

        <div class="flex flex-wrap items-end gap-4">
          <label class="text-dato">
            <span class="mb-1.5 block text-gray-500 dark:text-gray-400">Plan</span>
            <select [class]="CAMPO" [(ngModel)]="planElegido">
              @for (plan of PLANES; track plan) {
                <option [value]="plan">{{ plan }}</option>
              }
            </select>
          </label>

          <label class="text-dato">
            <span class="mb-1.5 block text-gray-500 dark:text-gray-400">Estado</span>
            <select [class]="CAMPO" [(ngModel)]="estadoElegido">
              @for (estado of ESTADOS; track estado) {
                <option [value]="estado">{{ estado }}</option>
              }
            </select>
          </label>

          <label class="min-w-60 flex-1 text-dato">
            <span class="mb-1.5 block text-gray-500 dark:text-gray-400">
              Motivo (queda en la bitácora)
            </span>
            <input [class]="CAMPO + ' w-full'" [(ngModel)]="motivo" placeholder="Por qué se cambia" />
          </label>

          <button
            type="button"
            class="h-11 rounded-lg bg-brand-500 px-5 text-dato font-medium text-white
                   transition hover:bg-brand-600 disabled:opacity-50"
            [disabled]="guardando()"
            (click)="guardar()"
          >
            {{ guardando() ? 'Guardando…' : 'Guardar' }}
          </button>
        </div>

        @if (aviso(); as texto) {
          <p
            class="mt-4 rounded-lg px-3.5 py-2.5 text-dato"
            [class]="
              avisoEsError()
                ? 'bg-error-25 text-error-700 dark:bg-error-500/10 dark:text-error-400'
                : 'bg-success-25 text-success-700 dark:bg-success-500/10 dark:text-success-400'
            "
          >
            {{ texto }}
          </p>
        }
      </section>

      <section
        class="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-apoyo
               dark:border-gray-800 dark:bg-white/[0.03]"
      >
        <div class="border-b border-gray-200 p-5 dark:border-gray-800">
          <h2 class="font-medium text-gray-800 dark:text-white/90">Módulos</h2>
          <p class="mt-1 text-dato text-gray-500 dark:text-gray-400">
            Sin decisión, manda el plan — y así la cuenta hereda los módulos que se
            añadan más adelante. Una decisión explícita se queda fija.
          </p>
        </div>

        <div class="divide-y divide-gray-100 dark:divide-gray-800">
          @for (modulo of modulos(); track modulo.id) {
            <div
              class="flex flex-wrap items-center gap-3 px-5 py-3 transition
                     hover:bg-gray-50 dark:hover:bg-white/[0.02]"
              [class.pl-12]="modulo.nivel === 'SUBMODULO'"
            >
              <span class="min-w-48 flex-1">
                <span
                  class="text-dato text-gray-800 dark:text-white/90"
                  [class.font-medium]="modulo.nivel === 'MODULO'"
                >
                  {{ modulo.nombre }}
                </span>
                <span class="ml-2 text-menudo text-gray-400 dark:text-gray-500">
                  {{ modulo.codigo }}
                </span>
              </span>

              <span
                class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium"
                [class]="
                  modulo.contratado
                    ? 'bg-success-50 text-success-700 dark:bg-success-500/15 dark:text-success-400'
                    : 'bg-gray-100 text-gray-600 dark:bg-white/[0.06] dark:text-gray-400'
                "
              >
                <span
                  class="h-1.5 w-1.5 rounded-full"
                  [class]="modulo.contratado ? 'bg-success-500' : 'bg-gray-400'"
                ></span>
                {{ modulo.contratado ? 'contratado' : 'no contratado' }}
              </span>

              <select
                [class]="CAMPO_COMPACTO"
                [ngModel]="valorDe(modulo)"
                (ngModelChange)="decidir(modulo, $event)"
              >
                <option value="plan">según el plan</option>
                <option value="si">forzado a sí</option>
                <option value="no">forzado a no</option>
              </select>
            </div>
          }
        </div>
      </section>
    } @else if (error()) {
      <div
        class="rounded-2xl border border-error-200 bg-error-25 p-4 text-dato text-error-700
               dark:border-error-500/30 dark:bg-error-500/10 dark:text-error-400"
      >
        {{ error() }}
      </div>
    } @else {
      <div class="space-y-3">
        @for (bloque of [1, 2, 3]; track bloque) {
          <div class="h-24 animate-pulse rounded-2xl bg-gray-100 dark:bg-white/[0.05]"></div>
        }
      </div>
    }
  `,
})
export class CuentaComponent implements OnInit {
  private readonly api = inject(PanelApiService);

  readonly id = input.required<string>();

  readonly PLANES = ['ESENCIAL', 'PROFESIONAL', 'CORPORATIVO'];
  readonly ESTADOS = ['EN_PRUEBA', 'ACTIVA', 'SUSPENDIDA', 'CANCELADA'];

  /*
   * Las clases de los campos, escritas una vez y como literales completos.
   *
   * Tailwind genera CSS leyendo los archivos y buscando cadenas que parezcan
   * clases, asi que una constante con la lista entera SI la detecta. Lo que no
   * detecta es una clase compuesta —'bg-' + color—, que acabaria en el DOM sin
   * ninguna regla detras. Esa es la trampa que explica styles.css.
   */
  protected readonly CAMPO =
    'h-11 rounded-lg border border-gray-300 bg-white px-3.5 text-dato text-gray-800 ' +
    'transition outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/20 ' +
    'dark:border-gray-700 dark:bg-gray-900 dark:text-white/90';

  protected readonly CAMPO_COMPACTO =
    'h-9 rounded-lg border border-gray-300 bg-white px-2.5 text-menudo text-gray-800 ' +
    'transition outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/20 ' +
    'dark:border-gray-700 dark:bg-gray-900 dark:text-white/90';

  readonly cuenta = signal<CuentaResumen | null>(null);
  readonly modulos = signal<ModuloContratado[]>([]);
  readonly guardando = signal(false);
  readonly aviso = signal<string | null>(null);
  readonly avisoEsError = signal(false);
  readonly error = signal<string | null>(null);

  planElegido = '';
  estadoElegido = '';
  motivo = '';

  /*
   * En ngOnInit y no en el constructor. Las entradas de la ruta todavía no
   * están puestas cuando corre el constructor; leer `id()` allí es exactamente
   * el NG0950 que dejaba esta pantalla en «Cargando…». Antes había un
   * queueMicrotask para esquivarlo, que funcionaba por cómo se encadenan las
   * tareas y no porque Angular lo garantice.
   */
  ngOnInit(): void {
    void this.recargar();
  }

  private async recargar(): Promise<void> {
    try {
      const cuentas = await this.api.cuentas();
      const cuenta = cuentas.find((c) => c.id === this.id()) ?? null;
      this.cuenta.set(cuenta);
      this.planElegido = cuenta?.planCodigo ?? '';
      this.estadoElegido = cuenta?.estadoSuscripcion ?? '';
      this.modulos.set(await this.api.modulos(this.id()));
    } catch {
      // Sin esto la pantalla se queda en el esqueleto de carga para siempre, y
      // un fallo de red es indistinguible de una respuesta lenta.
      this.error.set('No se pudo cargar la cuenta.');
    }
  }

  /** Las tres posiciones: sin decisión, forzado a sí, forzado a no. */
  valorDe(modulo: ModuloContratado): string {
    if (modulo.decision === null) {
      return 'plan';
    }
    return modulo.decision ? 'si' : 'no';
  }

  async decidir(modulo: ModuloContratado, valor: string): Promise<void> {
    const habilitado = valor === 'plan' ? null : valor === 'si';
    await this.api.decidirModulo(this.id(), modulo.id, habilitado, this.motivo || null);
    this.modulos.set(await this.api.modulos(this.id()));
  }

  async guardar(): Promise<void> {
    const actual = this.cuenta();
    if (!actual) {
      return;
    }

    this.guardando.set(true);
    try {
      // Dos llamadas y no una: son dos aspectos distintos y el servidor los
      // expone por separado a proposito, para que dos operadores a la vez no se
      // pisen guardando la cuenta entera.
      if (this.planElegido !== actual.planCodigo) {
        await this.api.cambiarPlan(this.id(), this.planElegido);
      }
      if (this.estadoElegido !== actual.estadoSuscripcion) {
        await this.api.cambiarEstado(this.id(), this.estadoElegido, this.motivo || null);
      }
      await this.recargar();
      this.avisoEsError.set(false);
      this.aviso.set('Guardado.');
    } catch {
      this.avisoEsError.set(true);
      this.aviso.set('No se pudo guardar.');
    } finally {
      this.guardando.set(false);
    }
  }
}
