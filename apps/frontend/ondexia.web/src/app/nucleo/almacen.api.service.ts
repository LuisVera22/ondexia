import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CONFIGURACION } from './configuracion';

/**
 * Cliente del módulo de almacén: productos, disponibilidad por local y
 * existencias (doc 12 §3.5). Los almacenes siguen en el cliente de
 * configuración, donde estaban.
 */

export interface OpcionCatalogo {
  readonly codigo: string;
  readonly nombre: string;
}

export interface CatalogosProducto {
  /** Catálogo 03 de SUNAT, acotado a lo que admite el dominio. */
  readonly unidades: OpcionCatalogo[];
  /** Catálogo 07: gravado, exonerado, inafecto. */
  readonly afectaciones: OpcionCatalogo[];
}

export interface ProductoApi {
  readonly id: string;
  readonly codigo: string;
  readonly nombre: string;
  readonly descripcion: string | null;
  readonly unidad: string;
  readonly unidadNombre: string;
  readonly afectacion: string;
  readonly afectacionNombre: string;
  readonly llevaIgv: boolean;
  readonly precioLista: number;
  readonly controlaStock: boolean;
  readonly activo: boolean;
}

export interface DatosProducto {
  readonly nombre: string;
  readonly descripcion: string | null;
  readonly unidad: string;
  readonly afectacion: string;
  readonly precioLista: number;
  readonly controlaStock: boolean;
}

export interface DisponibilidadApi {
  readonly sucursalId: string;
  readonly disponible: boolean;
  /** null: rige el precio de lista. */
  readonly precio: number | null;
}

export interface ExistenciaApi {
  readonly almacenId: string;
  readonly cantidad: number;
}

export interface MovimientoApi {
  readonly id: string;
  readonly almacenId: string;
  readonly cantidad: number;
  readonly tipo: 'AJUSTE' | 'INGRESO' | 'VENTA' | 'DEVOLUCION' | 'TRASLADO';
  readonly documentoTipo: string | null;
  readonly documentoId: string | null;
  readonly motivo: string | null;
  readonly usuarioId: string | null;
  readonly creadoEn: string;
}

@Injectable({ providedIn: 'root' })
export class AlmacenApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(CONFIGURACION);

  private get base(): string {
    return `${this.config.api}/api/v1/almacen/productos`;
  }

  /** Sin texto, todo el catálogo; con texto, por código o nombre, hasta 50. */
  productos(texto?: string): Promise<ProductoApi[]> {
    const params = texto ? new HttpParams().set('q', texto) : undefined;
    return firstValueFrom(this.http.get<ProductoApi[]>(this.base, { params }));
  }

  catalogos(): Promise<CatalogosProducto> {
    return firstValueFrom(this.http.get<CatalogosProducto>(`${this.base}/catalogos`));
  }

  producto(id: string): Promise<ProductoApi> {
    return firstValueFrom(this.http.get<ProductoApi>(`${this.base}/${id}`));
  }

  crearProducto(datos: DatosProducto & { codigo: string; sucursalId: string }): Promise<ProductoApi> {
    return firstValueFrom(this.http.post<ProductoApi>(this.base, datos));
  }

  actualizarProducto(id: string, datos: DatosProducto): Promise<ProductoApi> {
    return firstValueFrom(this.http.put<ProductoApi>(`${this.base}/${id}`, datos));
  }

  cambiarEstadoProducto(id: string, activo: boolean): Promise<ProductoApi> {
    return firstValueFrom(this.http.put<ProductoApi>(`${this.base}/${id}/estado`, { activo }));
  }

  disponibilidad(id: string): Promise<DisponibilidadApi[]> {
    return firstValueFrom(this.http.get<DisponibilidadApi[]>(`${this.base}/${id}/locales`));
  }

  fijarDisponibilidad(
    id: string,
    sucursalId: string,
    datos: { disponible: boolean; precio: number | null }
  ): Promise<DisponibilidadApi> {
    return firstValueFrom(
      this.http.put<DisponibilidadApi>(`${this.base}/${id}/locales/${sucursalId}`, datos)
    );
  }

  existencias(id: string): Promise<ExistenciaApi[]> {
    return firstValueFrom(this.http.get<ExistenciaApi[]>(`${this.base}/${id}/existencias`));
  }

  movimientos(id: string): Promise<MovimientoApi[]> {
    return firstValueFrom(this.http.get<MovimientoApi[]>(`${this.base}/${id}/movimientos`));
  }

  /** Lo contado manda: el servidor anota la diferencia como movimiento. */
  ajustarExistencias(
    id: string,
    datos: { almacenId: string; cantidad: number; motivo: string | null }
  ): Promise<ExistenciaApi> {
    return firstValueFrom(this.http.post<ExistenciaApi>(`${this.base}/${id}/existencias/ajustes`, datos));
  }
}
