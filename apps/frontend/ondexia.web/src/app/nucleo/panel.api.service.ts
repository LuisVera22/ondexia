import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CONFIGURACION } from './configuracion';

/**
 * Las cifras de la portada.
 *
 * <p>Un bloque en `null` no es un bloque en cero: significa que el usuario no
 * consulta esa parte y la pantalla no la pinta. La portada responde igual para
 * todos —es la primera pantalla de cualquiera, incluido quien solo tenga
 * Configuración— y es el contenido lo que se recorta.
 */

export interface CajaAbiertaApi {
  readonly sesionId: string;
  readonly cajaId: string;
  readonly abiertaEn: string;
  readonly montoInicial: number;
}

export interface VentasDelDiaApi {
  readonly documentos: number;
  readonly importe: number;
}

export interface ComprobantePorAtenderApi {
  readonly documentoId: string;
  /** Código del catálogo 01: `01`, `03`, `07`. Es lo que la ruta de la ficha pide. */
  readonly tipo: string;
  readonly tipoNombre: string;
  readonly serie: string;
  readonly numero: number;
  readonly numeroCompleto: string;
  readonly estado: string;
  readonly codigoSunat: string | null;
  readonly descripcionSunat: string | null;
}

export interface PorAtenderApi {
  /** Puede superar a `primeros`: la lista de la portada se recorta. */
  readonly total: number;
  readonly primeros: ComprobantePorAtenderApi[];
}

export interface PanelApi {
  /** El día del negocio en Lima, no el del reloj del navegador. */
  readonly fecha: string;
  readonly cajasAbiertas: CajaAbiertaApi[] | null;
  readonly ventas: VentasDelDiaApi | null;
  readonly comprobantesPorAtender: PorAtenderApi | null;
}

@Injectable({ providedIn: 'root' })
export class PanelApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(CONFIGURACION);

  consultar(): Promise<PanelApi> {
    return firstValueFrom(this.http.get<PanelApi>(`${this.config.api}/api/v1/panel`));
  }
}
