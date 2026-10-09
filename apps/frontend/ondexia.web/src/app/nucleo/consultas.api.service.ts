import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CONFIGURACION } from './configuracion';

/**
 * Consulta el RUC contra SUNAT.
 *
 * <h2>Por qué no cuelga de la API</h2>
 *
 * <p>Porque la Lambda de la API está en subred privada sin salida a internet, y
 * darle una cuesta entre 7 y 32 USD al mes. Quien consulta es
 * `ondexia.consultas`, un desplegable aparte, y por eso la URL sale de su propia
 * clave de configuración.
 *
 * <p>Desplegado esa clave vale lo mismo que `api` —allí la consulta es una ruta
 * más de la misma pasarela— y en local apunta a otro puerto. Este servicio no
 * distingue los dos casos: lee la clave y compone.
 *
 * <h2>Qué es la atestación, y por qué el formulario no debe tocarla</h2>
 *
 * <p>Es una firma de `ondexia.consultas` sobre los datos que devuelve. La API no
 * puede preguntarle a SUNAT, así que **valida esa firma** y saca de ahí la razón
 * social, el domicilio, el ubigeo, el estado y la condición.
 *
 * <p>Consecuencia práctica para esta pantalla: los campos autocompletados no se
 * envían al registrar. Se envía la atestación. Lo que se pinta en pantalla es
 * solo para que la persona lo lea y confirme — si alguien lo edita con las
 * herramientas del navegador no consigue nada, porque no puede firmar.
 */

/** Lo que SUNAT dice, para pintar el formulario. */
export interface DatosDeRuc {
  readonly ruc: string;
  readonly razonSocial: string;
  readonly estado: string;
  readonly condicion: string;
  readonly domicilioFiscal: string | null;
  readonly ubigeo: string | null;
  readonly distrito: string | null;
  readonly provincia: string | null;
  readonly departamento: string | null;
  readonly esAgenteRetencion: boolean;
  readonly esBuenContribuyente: boolean;
  readonly tipoSocietario: string | null;
  readonly consultadoEn: string;

  /**
   * Si este RUC puede darse de alta.
   *
   * <p>Viene calculado del servidor a propósito. Repetir la regla aquí
   * —«ACTIVO y HABIDO»— daría dos implementaciones de la puerta del registro, y
   * el día que cambie una el formulario diría lo contrario que el backend.
   */
  readonly aptaParaRegistro: boolean;

  /** Por qué no, en el idioma de quien lo lee. Nulo si es apta. */
  readonly motivoDeRechazo: string | null;
}

export interface ConsultaDeRuc {
  readonly datos: DatosDeRuc;
  /** La firma. Es lo único que el backend cree; se envía tal cual. */
  readonly atestacion: string;
}

/** Lo que le pasa a quien consulta, en términos que la pantalla puede usar. */
/** Lo que RENIEC dice de un DNI. Sin atestación: el nombre no decide nada fiscal. */
export interface DatosDeDni {
  readonly dni: string;
  readonly nombres: string | null;
  readonly apellidoPaterno: string | null;
  readonly apellidoMaterno: string | null;
  /** «Apellidos Nombres», como lo imprime la boleta. */
  readonly nombreCompleto: string;
  readonly consultadoEn: string;
}

export interface FalloDeConsulta {
  readonly mensaje: string;
  /**
   * Si merece la pena ofrecer «volver a intentarlo».
   *
   * <p>Lo dice el servidor, y no se adivina por el código HTTP: una clave
   * caducada del proveedor y una caída pasajera son el mismo 503 y no llevan a
   * la misma sugerencia. Ofrecer reintentar cuando no sirve es hacer perder el
   * tiempo; no ofrecerlo cuando sí, mandar a alguien a soporte sin motivo.
   */
  readonly reintentable: boolean;
  /** El padrón no conoce ese RUC: es un error del número, no del servicio. */
  readonly noEncontrado: boolean;
}

export class ErrorDeConsulta extends Error {
  constructor(readonly detalle: FalloDeConsulta) {
    super(detalle.mensaje);
  }
}

@Injectable({ providedIn: 'root' })
export class ConsultasApiService {
  private readonly http = inject(HttpClient);
  private readonly configuracion = inject(CONFIGURACION);

  /**
   * @param ruc once dígitos. No se valida aquí el dígito verificador: lo hace el
   *     servidor y responde sin salir a la red, así que una errata de tecleo no
   *     gasta una consulta del plan contratado
   */
  async consultarRuc(ruc: string): Promise<ConsultaDeRuc> {
    try {
      return await firstValueFrom(
        this.http.get<ConsultaDeRuc>(
          `${this.configuracion.consultas}/consultas/ruc/${encodeURIComponent(ruc)}`
        )
      );
    } catch (fallo: unknown) {
      throw new ErrorDeConsulta(this.traducir(fallo));
    }
  }

  async consultarDni(dni: string): Promise<DatosDeDni> {
    try {
      return await firstValueFrom(
        this.http.get<DatosDeDni>(
          `${this.configuracion.consultas}/consultas/dni/${encodeURIComponent(dni)}`
        )
      );
    } catch (fallo: unknown) {
      throw new ErrorDeConsulta(this.traducir(fallo, 'DNI'));
    }
  }

  private traducir(fallo: unknown, documento: 'RUC' | 'DNI' = 'RUC'): FalloDeConsulta {
    if (!(fallo instanceof HttpErrorResponse)) {
      return {
        mensaje: `No se pudo consultar el ${documento}.`,
        reintentable: true,
        noEncontrado: false,
      };
    }

    const cuerpo = fallo.error as
      | { codigo?: string; mensaje?: string; reintentable?: boolean }
      | null;

    // Status 0: no hubo respuesta. En local es casi siempre que
    // ondexia.consultas no esta arrancado, y decirlo ahorra media hora de
    // buscar el problema en el sitio equivocado.
    if (fallo.status === 0) {
      return {
        mensaje: `No se pudo contactar con el servicio de consulta de ${documento}.`,
        reintentable: true,
        noEncontrado: false,
      };
    }

    // El texto del servidor solo si trae `codigo` (tabla de bajas): sin él la
    // respuesta no es de ondexia.consultas sino de la pasarela, y su cuerpo
    // describe la infraestructura.
    const esNuestra = typeof cuerpo?.codigo === 'string';

    return {
      mensaje: (esNuestra && cuerpo?.mensaje) || `No se pudo consultar el ${documento}.`,
      reintentable: (esNuestra && cuerpo?.reintentable) || false,
      noEncontrado: fallo.status === 404,
    };
  }
}
