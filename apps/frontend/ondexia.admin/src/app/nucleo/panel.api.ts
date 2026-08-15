import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { CONFIGURACION } from './configuracion';

/**
 * @param limiteEmpresas `null` es **sin límite**, no cero. Es lo que necesita el
 *   plan negociable, y confundirlo pintaría «0 disponibles» donde debería decir
 *   «sin límite» — el error más caro posible en esta pantalla, porque llevaría a
 *   subir de plan a quien no lo necesita
 */
export interface CuentaResumen {
  readonly id: string;
  readonly nombre: string;
  readonly planCodigo: string;
  readonly planNombre: string;
  readonly estadoSuscripcion: string;
  readonly empresas: number;
  readonly limiteEmpresas: number | null;
  readonly usuarios: number;
  readonly limiteUsuarios: number | null;
  readonly creadoEn: string;
}

/**
 * @param decision lo decidido para esta cuenta. `null` significa que no hay
 *   decisión y manda el plan — distinto de `false`: sin decisión, la cuenta
 *   hereda los módulos que se añadan al plan más adelante
 * @param contratado el resultado efectivo, que es lo que el servidor aplica
 */
export interface ModuloContratado {
  readonly id: string;
  readonly codigo: string;
  readonly modulo: string;
  readonly nivel: 'MODULO' | 'SUBMODULO';
  readonly nombre: string;
  readonly decision: boolean | null;
  readonly contratado: boolean;
}

@Injectable({ providedIn: 'root' })
export class PanelApiService {
  private readonly http = inject(HttpClient);
  private readonly configuracion = inject(CONFIGURACION);

  private get base(): string {
    return `${this.configuracion.api}/api/v1/cuentas`;
  }

  cuentas(): Promise<CuentaResumen[]> {
    return firstValueFrom(this.http.get<CuentaResumen[]>(this.base));
  }

  modulos(cuentaId: string): Promise<ModuloContratado[]> {
    return firstValueFrom(
      this.http.get<ModuloContratado[]>(`${this.base}/${cuentaId}/modulos`),
    );
  }

  cambiarPlan(cuentaId: string, plan: string): Promise<void> {
    return firstValueFrom(this.http.put<void>(`${this.base}/${cuentaId}/plan`, { plan }));
  }

  cambiarEstado(cuentaId: string, estado: string, motivo: string | null): Promise<void> {
    return firstValueFrom(
      this.http.put<void>(`${this.base}/${cuentaId}/estado`, { estado, motivo }),
    );
  }

  /** `habilitado` nulo borra la decisión y devuelve la cuenta a su plan. */
  decidirModulo(
    cuentaId: string,
    permisoId: string,
    habilitado: boolean | null,
    motivo: string | null,
  ): Promise<void> {
    return firstValueFrom(
      this.http.put<void>(`${this.base}/${cuentaId}/modulos`, { permisoId, habilitado, motivo }),
    );
  }
}
