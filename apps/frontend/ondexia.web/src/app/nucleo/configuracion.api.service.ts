import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CONFIGURACION } from './configuracion';

/**
 * Cliente del módulo de configuración.
 *
 * <p>Escrito a mano, igual que {@code contexto.api.ts}. La intención sigue
 * siendo generarlo desde el {@code openapi.yaml} que el backend exporta en cada
 * build; mientras eso no exista, estos tipos son el único sitio donde una
 * divergencia entre backend y frontend puede pasar inadvertida.
 */

export interface Empresa {
  readonly id: string;
  /** No se puede cambiar: identifica al contribuyente en los comprobantes emitidos. */
  readonly ruc: string;
  readonly razonSocial: string;
  readonly nombreComercial: string | null;
  readonly domicilioFiscal: string;
  readonly ubigeo: string | null;
  readonly modoSunat: string;
  readonly activa: boolean;
}

export interface DatosEmpresa {
  readonly razonSocial: string;
  readonly nombreComercial: string | null;
  readonly domicilioFiscal: string;
  readonly ubigeo: string | null;
}

export interface Establecimiento {
  readonly id: string;
  /** Los cuatro dígitos que asigna SUNAT. No se puede cambiar. */
  readonly codigo: string;
  readonly nombre: string;
  readonly direccion: string;
  readonly ubigeo: string | null;
  readonly activa: boolean;
}

export interface DatosEstablecimiento {
  readonly nombre: string;
  readonly direccion: string;
  readonly ubigeo: string | null;
}

export interface AlmacenApi {
  readonly id: string;
  /** Corto y en mayúsculas: se teclea en cada movimiento de mercadería. */
  readonly codigo: string;
  readonly nombre: string;
  /** null: hay almacenes que no cuelgan de ningún establecimiento. */
  readonly sucursalId: string | null;
  readonly activo: boolean;
}

export interface DatosAlmacen {
  readonly nombre: string;
  readonly sucursalId: string | null;
}

@Injectable({ providedIn: 'root' })
export class ConfiguracionApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(CONFIGURACION);

  private get base(): string {
    return `${this.config.api}/api/v1/configuracion`;
  }

  empresa(): Promise<Empresa> {
    return firstValueFrom(this.http.get<Empresa>(`${this.base}/empresa`));
  }

  guardarEmpresa(datos: DatosEmpresa): Promise<Empresa> {
    return firstValueFrom(this.http.put<Empresa>(`${this.base}/empresa`, datos));
  }

  establecimientos(): Promise<Establecimiento[]> {
    return firstValueFrom(
      this.http.get<Establecimiento[]>(`${this.base}/establecimientos`)
    );
  }

  crearEstablecimiento(
    datos: DatosEstablecimiento & { readonly codigo: string }
  ): Promise<Establecimiento> {
    return firstValueFrom(
      this.http.post<Establecimiento>(`${this.base}/establecimientos`, datos)
    );
  }

  actualizarEstablecimiento(
    id: string,
    datos: DatosEstablecimiento
  ): Promise<Establecimiento> {
    return firstValueFrom(
      this.http.put<Establecimiento>(`${this.base}/establecimientos/${id}`, datos)
    );
  }

  /** Desactiva, no borra: el establecimiento aparece en los comprobantes emitidos. */
  desactivarEstablecimiento(id: string): Promise<void> {
    return firstValueFrom(
      this.http.delete<void>(`${this.base}/establecimientos/${id}`)
    );
  }

  // ── Almacenes ────────────────────────────────────────────────────────────
  //
  // Cuelgan de /almacen y no de /configuracion porque los gobierna el permiso
  // `almacen.almacen`: quien administra el inventario los crea, sin necesitar
  // acceso a los datos fiscales de la empresa.

  private get baseAlmacen(): string {
    return `${this.config.api}/api/v1/almacen`;
  }

  almacenes(): Promise<AlmacenApi[]> {
    return firstValueFrom(this.http.get<AlmacenApi[]>(`${this.baseAlmacen}/almacenes`));
  }

  crearAlmacen(datos: DatosAlmacen & { readonly codigo: string }): Promise<AlmacenApi> {
    return firstValueFrom(this.http.post<AlmacenApi>(`${this.baseAlmacen}/almacenes`, datos));
  }

  actualizarAlmacen(id: string, datos: DatosAlmacen): Promise<AlmacenApi> {
    return firstValueFrom(
      this.http.put<AlmacenApi>(`${this.baseAlmacen}/almacenes/${id}`, datos)
    );
  }

  /** Desactiva, no borra: el almacén aparece en cada movimiento de stock que lo tocó. */
  desactivarAlmacen(id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.baseAlmacen}/almacenes/${id}`));
  }
}

/**
 * Saca el mensaje legible de un error de la API.
 *
 * <p>El backend responde con `application/problem+json` y añade un `codigo`
 * estable. Se prefiere `detail` porque es el texto escrito para el usuario;
 * `title` es genérico —«Conflict»— y no dice nada útil.
 */
export function mensajeDeError(error: unknown, porDefecto: string): string {
  const cuerpo = (error as { error?: { detail?: string; codigo?: string } })?.error;
  return cuerpo?.detail ?? porDefecto;
}
