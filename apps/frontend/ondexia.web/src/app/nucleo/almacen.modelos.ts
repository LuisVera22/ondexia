// Generado desde ondexia.contracts/openapi.yaml por ExportarContratoIT. No editar a mano.

export type TipoProducto = 'BIEN' | 'SERVICIO';

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
  readonly precioLista: string;
  readonly tipo: TipoProducto;
  readonly controlaStock: boolean;
  readonly activo: boolean;
}

export interface ProductoDisponibleApi {
  readonly id: string;
  readonly codigo: string;
  readonly nombre: string;
  readonly unidad: string;
  readonly unidadNombre: string;
  readonly afectacion: string;
  readonly llevaIgv: boolean;
  readonly precio: number;
  readonly controlaStock: boolean;
  readonly existencia: number | null;
}

export interface DisponibilidadApi {
  readonly sucursalId: string;
  readonly disponible: boolean;
  readonly precio: string | null;
}

export interface DatosProducto {
  readonly nombre: string;
  readonly descripcion: string | null;
  readonly unidad: string;
  readonly afectacion: string;
  readonly precioLista: string;
  readonly tipo: TipoProducto;
}
