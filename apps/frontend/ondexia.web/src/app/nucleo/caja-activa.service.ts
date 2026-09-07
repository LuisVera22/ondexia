import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { ContextoService } from '../shared/services/contexto.service';
import { CajaApi, VentasApiService } from './ventas.api.service';

/**
 * Las cajas que el usuario alcanza en la empresa activa, y cuál está abierta.
 *
 * <p>Vive fuera de la pantalla de cajas porque la barra superior lo muestra en
 * todas partes (doc 12 §8, iteración 2): quien vende tiene que saber en qué
 * caja está cobrando sin ir a buscarlo. La pantalla de cajas escribe aquí
 * después de abrir o cerrar, y la barra se entera sin volver a preguntar.
 *
 * <p>Se recarga solo cuando cambia la empresa activa y el usuario puede
 * consultar cajas. Sin ese permiso no se pide nada: la respuesta sería un 403
 * que no aporta más que ruido en la consola.
 */
@Injectable({ providedIn: 'root' })
export class CajaActivaService {
  private readonly api = inject(VentasApiService);
  private readonly contexto = inject(ContextoService);

  private readonly _cajas = signal<CajaApi[]>([]);
  readonly cajas = this._cajas.asReadonly();

  /** Las que tienen sesión en curso, empezando por la del establecimiento activo. */
  readonly abiertas = computed(() => {
    const establecimiento = this.contexto.establecimientoActivo()?.id;
    return this._cajas()
      .filter((caja) => caja.sesionAbierta !== null)
      .sort((a, b) => Number(b.sucursalId === establecimiento) - Number(a.sucursalId === establecimiento));
  });

  /** La caja abierta que se muestra en la barra: la primera del establecimiento activo. */
  readonly enUso = computed<CajaApi | null>(() => this.abiertas()[0] ?? null);

  constructor() {
    effect(() => {
      const empresa = this.contexto.empresaActiva();
      const puede = this.contexto.puede('ventas.caja:consultar');
      if (!empresa || !puede) {
        this._cajas.set([]);
        return;
      }
      void this.recargar();
    });
  }

  async recargar(): Promise<void> {
    try {
      this._cajas.set(await this.api.cajas());
    } catch {
      // La barra no es el sitio para explicar un fallo de red: la pantalla de
      // cajas lo hará con su propio mensaje cuando el usuario entre.
      this._cajas.set([]);
    }
  }

  /** Para que la pantalla de cajas comparta lo que acaba de traer. */
  reemplazar(cajas: CajaApi[]): void {
    this._cajas.set(cajas);
  }
}
