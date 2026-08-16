import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CONFIGURACION } from './configuracion';

/**
 * Cliente de `/api/v1/perfil`.
 *
 * <p>Escrito a mano como el resto, y por lo mismo: los tipos saldrán del
 * `openapi.yaml` cuando se genere el cliente. Hasta entonces, este archivo es un
 * sitio donde backend y frontend pueden divergir sin que nadie avise.
 */

export interface DatosDePerfil {
  readonly nombre: string;
  /**
   * Nulo en las cuentas creadas antes de que el campo existiera, que conservan
   * el nombre completo en `nombre`. El formulario lo exige, así que se queda así
   * solo hasta el primer guardado.
   */
  readonly apellido: string | null;
  /** Credencial de acceso a Cognito. Se pinta, no se edita. */
  readonly email: string;
  readonly telefono: string | null;
}

export interface CambioDePerfil {
  readonly nombre: string;
  readonly apellido: string;
  /** En blanco borra el que hubiera. */
  readonly telefono: string | null;
}

@Injectable({ providedIn: 'root' })
export class PerfilApiService {
  private readonly http = inject(HttpClient);
  private readonly configuracion = inject(CONFIGURACION);

  private get base(): string {
    return `${this.configuracion.api}/api/v1/perfil`;
  }

  ver(): Promise<DatosDePerfil> {
    return firstValueFrom(this.http.get<DatosDePerfil>(this.base));
  }

  guardar(cambio: CambioDePerfil): Promise<DatosDePerfil> {
    return firstValueFrom(this.http.put<DatosDePerfil>(this.base, cambio));
  }
}
