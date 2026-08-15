import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

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
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (cuenta(); as c) {
      <h1 class="text-xl font-semibold">{{ c.nombre }}</h1>
      <p class="mb-6 text-sm text-slate-400">
        {{ c.empresas }} empresas · {{ c.usuarios }} usuarios
      </p>

      <section class="mb-6 rounded-lg border border-slate-700 p-4">
        <h2 class="mb-3 font-medium">Plan y estado</h2>

        <div class="flex flex-wrap items-end gap-4">
          <label class="text-sm">
            <span class="mb-1 block text-slate-400">Plan</span>
            <select class="rounded bg-slate-800 px-3 py-2" [(ngModel)]="planElegido">
              @for (plan of PLANES; track plan) {
                <option [value]="plan">{{ plan }}</option>
              }
            </select>
          </label>

          <label class="text-sm">
            <span class="mb-1 block text-slate-400">Estado</span>
            <select class="rounded bg-slate-800 px-3 py-2" [(ngModel)]="estadoElegido">
              @for (estado of ESTADOS; track estado) {
                <option [value]="estado">{{ estado }}</option>
              }
            </select>
          </label>

          <label class="flex-1 text-sm">
            <span class="mb-1 block text-slate-400">Motivo (queda en la bitácora)</span>
            <input class="w-full rounded bg-slate-800 px-3 py-2" [(ngModel)]="motivo"
                   placeholder="Por qué se cambia" />
          </label>

          <button type="button" class="rounded bg-sky-600 px-4 py-2 text-sm hover:bg-sky-500"
                  [disabled]="guardando()" (click)="guardar()">
            Guardar
          </button>
        </div>

        @if (aviso(); as texto) {
          <p class="mt-3 text-sm text-slate-300">{{ texto }}</p>
        }
      </section>

      <section class="rounded-lg border border-slate-700 p-4">
        <h2 class="mb-1 font-medium">Módulos</h2>
        <p class="mb-3 text-sm text-slate-400">
          Sin decisión, manda el plan — y así la cuenta hereda los módulos que se
          añadan más adelante. Una decisión explícita se queda fija.
        </p>

        @for (modulo of modulos(); track modulo.id) {
          <div class="flex items-center gap-3 border-t border-slate-800 py-2"
               [class.pl-6]="modulo.nivel === 'SUBMODULO'">
            <span class="flex-1 text-sm">
              {{ modulo.nombre }}
              <span class="ml-2 text-xs text-slate-500">{{ modulo.codigo }}</span>
            </span>

            <span class="text-xs" [class]="modulo.contratado ? 'text-emerald-300' : 'text-slate-500'">
              {{ modulo.contratado ? 'contratado' : 'no contratado' }}
            </span>

            <select class="rounded bg-slate-800 px-2 py-1 text-xs"
                    [ngModel]="valorDe(modulo)"
                    (ngModelChange)="decidir(modulo, $event)">
              <option value="plan">según el plan</option>
              <option value="si">forzado a sí</option>
              <option value="no">forzado a no</option>
            </select>
          </div>
        }
      </section>
    } @else {
      <p class="text-sm text-slate-400">Cargando…</p>
    }
  `,
})
export class CuentaComponent implements OnInit {
  private readonly api = inject(PanelApiService);

  readonly id = input.required<string>();

  readonly PLANES = ['ESENCIAL', 'PROFESIONAL', 'CORPORATIVO'];
  readonly ESTADOS = ['EN_PRUEBA', 'ACTIVA', 'SUSPENDIDA', 'CANCELADA'];

  readonly cuenta = signal<CuentaResumen | null>(null);
  readonly modulos = signal<ModuloContratado[]>([]);
  readonly guardando = signal(false);
  readonly aviso = signal<string | null>(null);

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
    const cuentas = await this.api.cuentas();
    const cuenta = cuentas.find((c) => c.id === this.id()) ?? null;
    this.cuenta.set(cuenta);
    this.planElegido = cuenta?.planCodigo ?? '';
    this.estadoElegido = cuenta?.estadoSuscripcion ?? '';
    this.modulos.set(await this.api.modulos(this.id()));
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
      this.aviso.set('Guardado.');
    } catch {
      this.aviso.set('No se pudo guardar.');
    } finally {
      this.guardando.set(false);
    }
  }
}
