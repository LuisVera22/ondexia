import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CONFIGURACION } from './configuracion';

/**
 * Cliente del módulo de ventas. Por ahora, cajas y sesiones de caja (doc 12
 * §3.4); la venta llegará en la iteración 4.
 *
 * <p>Escrito a mano como el de configuración: estos tipos son el único sitio
 * donde una divergencia con el backend puede pasar inadvertida.
 */

/** Catálogo cerrado del primer producto: es un enumerado del dominio, no una tabla. */
export type FormaDePago = 'EFECTIVO' | 'TARJETA' | 'TRANSFERENCIA' | 'BILLETERA_DIGITAL';

export const FORMAS_DE_PAGO: ReadonlyArray<{ codigo: FormaDePago; nombre: string }> = [
  { codigo: 'EFECTIVO', nombre: 'Efectivo' },
  { codigo: 'TARJETA', nombre: 'Tarjeta' },
  { codigo: 'TRANSFERENCIA', nombre: 'Transferencia' },
  { codigo: 'BILLETERA_DIGITAL', nombre: 'Billetera digital' },
];

/** Importes por forma de pago. Las formas ausentes valen cero. */
export type ImportesPorForma = Partial<Record<FormaDePago, number>>;

export interface SesionCajaApi {
  readonly id: string;
  readonly cajaId: string;
  readonly estado: 'ABIERTA' | 'CERRADA';
  readonly abiertaPor: string;
  readonly abiertaEn: string;
  readonly montoInicial: number;
  readonly cerradaPor: string | null;
  readonly cerradaEn: string | null;
  readonly declarado: ImportesPorForma;
  readonly calculado: ImportesPorForma;
  /** Declarado menos calculado. Vacío mientras la sesión está abierta. */
  readonly diferencia: ImportesPorForma;
}

export interface CajaApi {
  readonly id: string;
  readonly sucursalId: string;
  /** Único por establecimiento. No se puede cambiar. */
  readonly codigo: string;
  readonly nombre: string;
  readonly activa: boolean;
  /** La sesión en curso, o null si la caja está cerrada. */
  readonly sesionAbierta: SesionCajaApi | null;
}

/** Catálogo 06 de SUNAT, sin el «sin documento»: ese no es una fila. */
export type TipoDocumentoCliente = 'DNI' | 'CARNET_EXTRANJERIA' | 'RUC' | 'PASAPORTE';

export interface TipoDocumentoApi {
  readonly codigo: TipoDocumentoCliente;
  readonly codigoSunat: string;
  readonly nombre: string;
}

export interface ClienteApi {
  readonly id: string;
  readonly tipoDocumento: TipoDocumentoCliente;
  readonly tipoDocumentoNombre: string;
  /** No cambia después del alta: ya está impreso en sus comprobantes. */
  readonly numeroDocumento: string;
  readonly nombre: string;
  readonly direccion: string | null;
  readonly correo: string | null;
  readonly telefono: string | null;
  /** Cuándo se comprobó el RUC contra el padrón. null: nunca, o no es RUC. */
  readonly verificadoEn: string | null;
  readonly admiteFactura: boolean;
  readonly activo: boolean;
}

export interface DatosCliente {
  readonly nombre: string;
  readonly direccion: string | null;
  readonly correo: string | null;
  readonly telefono: string | null;
}

@Injectable({ providedIn: 'root' })
export class VentasApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(CONFIGURACION);

  private get base(): string {
    return `${this.config.api}/api/v1/ventas`;
  }

  /** Las cajas que el usuario alcanza —todas, o las de su establecimiento— con su sesión abierta. */
  cajas(): Promise<CajaApi[]> {
    return firstValueFrom(this.http.get<CajaApi[]>(`${this.base}/cajas`));
  }

  crearCaja(datos: { codigo: string; nombre: string; sucursalId: string }): Promise<CajaApi> {
    return firstValueFrom(this.http.post<CajaApi>(`${this.base}/cajas`, datos));
  }

  renombrarCaja(id: string, nombre: string): Promise<CajaApi> {
    return firstValueFrom(this.http.put<CajaApi>(`${this.base}/cajas/${id}`, { nombre }));
  }

  cambiarEstadoCaja(id: string, activa: boolean): Promise<CajaApi> {
    return firstValueFrom(this.http.put<CajaApi>(`${this.base}/cajas/${id}/estado`, { activa }));
  }

  abrirCaja(id: string, montoInicial: number): Promise<SesionCajaApi> {
    return firstValueFrom(
      this.http.post<SesionCajaApi>(`${this.base}/cajas/${id}/sesiones`, { montoInicial })
    );
  }

  /** Lo declarado por forma de pago; lo que no se declare vale cero. */
  cerrarSesion(sesionId: string, declarado: ImportesPorForma): Promise<SesionCajaApi> {
    return firstValueFrom(
      this.http.put<SesionCajaApi>(`${this.base}/cajas/sesiones/${sesionId}/cierre`, { declarado })
    );
  }

  historialDeCaja(id: string): Promise<SesionCajaApi[]> {
    return firstValueFrom(this.http.get<SesionCajaApi[]>(`${this.base}/cajas/${id}/sesiones`));
  }

  // ── Clientes ───────────────────────────────────────────────────────────

  /** Sin texto, todos; con texto, por nombre o documento, hasta 50. */
  clientes(texto?: string): Promise<ClienteApi[]> {
    const params = texto ? new HttpParams().set('q', texto) : undefined;
    return firstValueFrom(this.http.get<ClienteApi[]>(`${this.base}/clientes`, { params }));
  }

  tiposDeDocumento(): Promise<TipoDocumentoApi[]> {
    return firstValueFrom(this.http.get<TipoDocumentoApi[]>(`${this.base}/clientes/tipos-documento`));
  }

  cliente(id: string): Promise<ClienteApi> {
    return firstValueFrom(this.http.get<ClienteApi>(`${this.base}/clientes/${id}`));
  }

  /**
   * @param atestacion solo con RUC: lo que devolvió la consulta al padrón. La
   *                   razón social y el domicilio salen de ahí.
   */
  crearCliente(
    datos: DatosCliente & {
      tipoDocumento: TipoDocumentoCliente;
      numeroDocumento: string;
      atestacion: string | null;
    }
  ): Promise<ClienteApi> {
    return firstValueFrom(this.http.post<ClienteApi>(`${this.base}/clientes`, datos));
  }

  actualizarCliente(id: string, datos: DatosCliente): Promise<ClienteApi> {
    return firstValueFrom(this.http.put<ClienteApi>(`${this.base}/clientes/${id}`, datos));
  }

  verificarCliente(id: string, atestacion: string): Promise<ClienteApi> {
    return firstValueFrom(
      this.http.post<ClienteApi>(`${this.base}/clientes/${id}/verificacion`, { atestacion })
    );
  }

  cambiarEstadoCliente(id: string, activo: boolean): Promise<ClienteApi> {
    return firstValueFrom(this.http.put<ClienteApi>(`${this.base}/clientes/${id}/estado`, { activo }));
  }
}
