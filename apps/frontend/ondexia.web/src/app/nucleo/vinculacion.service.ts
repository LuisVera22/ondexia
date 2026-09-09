import { HttpBackend, HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CONFIGURACION } from './configuracion';
import { interpretarError } from './errores';
import { SesionService } from './sesion.service';

export interface ResultadoVinculacion {
  /** True si la persona ya está dentro de la cuenta que la invitó. */
  readonly vinculado: boolean;
  /**
   * Motivo por el que no se pudo vincular teniendo invitación. Null en el caso
   * normal —no había ninguna— para no alarmar a quien simplemente se registra
   * por su cuenta.
   */
  readonly aviso: string | null;
}

/**
 * Acepta la invitación pendiente de quien acaba de registrarse en Cognito.
 *
 * <h2>Por qué esto no pasa por el interceptor</h2>
 *
 * Porque manda el token de IDENTIDAD y no el de acceso, y el interceptor
 * sobrescribe la cabecera `Authorization` con el de acceso en toda llamada a
 * nuestra API. Es el único endpoint que necesita el otro: el de acceso de
 * Cognito no lleva el correo, y el correo es lo que decide en qué empresa entra
 * la persona.
 *
 * Se usa `HttpBackend` —la cadena sin interceptores— en vez de relajar el
 * interceptor para que respete una cabecera puesta a mano. Un interceptor que a
 * veces firma y a veces no es la clase de cosa que acaba dejando una llamada sin
 * token sin que nadie lo note.
 */
@Injectable({ providedIn: 'root' })
export class VinculacionService {
  private readonly configuracion = inject(CONFIGURACION);
  private readonly sesion = inject(SesionService);
  private readonly http = new HttpClient(inject(HttpBackend));

  async intentar(): Promise<ResultadoVinculacion> {
    const identidad = await this.sesion.tokenDeIdentidad();
    if (!identidad) {
      return { vinculado: false, aviso: null };
    }

    try {
      await firstValueFrom(
        this.http.post(
          `${this.configuracion.api}/api/v1/registro/vinculo`,
          null,
          { headers: new HttpHeaders({ Authorization: `Bearer ${identidad}` }) }
        )
      );
      return { vinculado: true, aviso: null };
    } catch (fallo: unknown) {
      /*
       * «No hay invitación» es el caso normal, no un error: quien se registra
       * por su cuenta llega aquí siempre. Se distingue por el código estable
       * porque el resto de 404 sí merecen contarse.
       */
      const sinInvitacion =
        fallo instanceof HttpErrorResponse && fallo.error?.codigo === 'sin_invitacion';

      if (sinInvitacion) {
        return { vinculado: false, aviso: null };
      }

      /*
       * Los conflictos que sí hay que contar: dos empresas invitaron al mismo
       * correo, o la invitación se desactivó antes de llegar. Sin este mensaje
       * la persona vería el formulario de empresa nueva y se crearía una cuenta
       * duplicada creyendo que es lo que toca.
       */
      // Por interpretarError, no leyendo `detail` a mano (tabla de bajas):
      // sin `codigo` la respuesta no es nuestra —es de la pasarela o del
      // balanceador— y su texto describe la infraestructura, no algo que la
      // persona deba leer.
      const error = interpretarError(fallo, 'No se pudo comprobar la invitación.');
      return { vinculado: false, aviso: error.codigo === 'desconocido' ? null : error.mensaje };
    }
  }
}
