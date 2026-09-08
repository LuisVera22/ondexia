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

// ── Documentos de venta ─────────────────────────────────────────────────

/** '01' factura, '03' boleta, 'NV' nota de venta (interna, no se declara). */
/** Los códigos del catálogo 01 que el punto de venta maneja, más la nota de venta. */
export type TipoDocumentoVenta = '01' | '03' | '07' | 'NV';
export type TipoComprobanteEmitible = 'BOLETA' | 'FACTURA';
export type EstadoDocumentoVenta = 'EMITIDO' | 'PENDIENTE' | 'CANJEADO' | 'ANULADO';

export interface LineaPedida {
  readonly productoId: string;
  readonly cantidad: number;
  readonly descuento?: number | null;
}

export interface PagoPedido {
  readonly forma: FormaDePago;
  readonly monto: number;
  readonly referencia?: string | null;
}

export interface PeticionVenta {
  readonly cajaId: string;
  readonly serieId?: string | null;
  readonly clienteId?: string | null;
  readonly lineas: LineaPedida[];
  readonly pagos: PagoPedido[];
  readonly observaciones?: string | null;
}

export interface LineaDocumentoApi {
  readonly orden: number;
  readonly productoId: string;
  readonly codigo: string;
  readonly descripcion: string;
  readonly unidad: string;
  readonly cantidad: number;
  /** Con IGV. */
  readonly precioUnitario: number;
  /** Sin IGV. */
  readonly valorUnitario: number;
  readonly descuento: number;
  readonly afectacion: string;
  readonly valorVenta: number;
  readonly igv: number;
  readonly total: number;
}

export interface PagoDocumentoApi {
  readonly forma: FormaDePago;
  readonly monto: number;
  readonly referencia: string | null;
}

export interface ClienteDocumentoApi {
  readonly id: string;
  readonly tipoDocumento: TipoDocumentoCliente;
  readonly numeroDocumento: string;
  readonly nombre: string;
  readonly direccion: string | null;
}

export interface DocumentoVentaApi {
  readonly id: string;
  readonly tipo: TipoDocumentoVenta;
  readonly tipoNombre: string;
  readonly fiscal: boolean;
  readonly serie: string;
  readonly numero: number;
  readonly numeroCompleto: string;
  readonly estado: EstadoDocumentoVenta;
  readonly sucursalId: string;
  readonly sesionCajaId: string;
  /** null: adquirente sin documento. */
  readonly cliente: ClienteDocumentoApi | null;
  readonly fechaEmision: string;
  readonly emitidoEn: string;
  readonly emitidoPor: string;
  readonly moneda: string;
  readonly totalGravado: number;
  readonly totalExonerado: number;
  readonly totalInafecto: number;
  readonly totalDescuento: number;
  readonly totalIgv: number;
  readonly total: number;
  readonly observaciones: string | null;
  readonly documentoOrigenId: string | null;
  /** Solo en una nota de crédito: el motivo del catálogo 09. */
  readonly motivo: string | null;
  readonly motivoNombre: string | null;
  readonly origen: OrigenDocumentoApi | null;
  readonly lineas: LineaDocumentoApi[];
  readonly pagos: PagoDocumentoApi[];
}

/**
 * Motivo de una nota de crédito, catálogo 09 de SUNAT (doc 13 §5.2). El
 * servidor dice cuáles anulan y cuáles reponen existencias: la pantalla no lo
 * deduce del código.
 */
export interface MotivoNotaCredito {
  /** El nombre del enumerado, que es lo que se manda de vuelta. */
  readonly codigo: string;
  /** El del catálogo 09, que es lo que viaja en el XML. */
  readonly codigoSunat: string;
  readonly nombre: string;
  /** Deja sin efecto el comprobante entero. */
  readonly anula: boolean;
  /** La mercadería vuelve al almacén. */
  readonly repone: boolean;
}

/** Dónde está una boleta o factura ante SUNAT (doc 14 §3). */
export type EstadoSunat = 'EN_COLA' | 'EN_PROCESO' | 'ACEPTADO' | 'RECHAZADO' | 'ERROR_ENVIO' | 'ANULADO';

export const ESTADOS_SUNAT: Readonly<Record<EstadoSunat, string>> = {
  EN_COLA: 'Enviando a SUNAT',
  // Solo en los envíos asíncronos: SUNAT dio un ticket y falta su veredicto.
  EN_PROCESO: 'SUNAT lo está procesando',
  ACEPTADO: 'Aceptado por SUNAT',
  RECHAZADO: 'Rechazado por SUNAT',
  ERROR_ENVIO: 'Error de envío',
  ANULADO: 'Anulado',
};

/** Lo que dijo SUNAT de un comprobante, tal como lo guarda la API. */
export interface EstadoSunatApi {
  readonly comprobanteId: string;
  readonly documentoId: string;
  readonly estado: EstadoSunat;
  readonly intentos: number;
  readonly encoladoEn: string | null;
  readonly respondidoEn: string | null;
  /** El código de SUNAT (0, 2335, 0100…) o uno del Emisor en un fallo local. */
  readonly codigo: string | null;
  readonly descripcion: string | null;
  /** Observaciones del CDR (códigos 4000+): aceptado con reparos. */
  readonly observaciones: readonly string[];
  readonly xmlDisponible: boolean;
  readonly cdrDisponible: boolean;
  /** El DigestValue de la firma, que va impreso y en el QR. */
  readonly resumenFirma: string | null;
  readonly admiteReintento: boolean;
}

/** El documento al que otro se refiere: el corregido, o la nota de venta canjeada. */
export interface OrigenDocumentoApi {
  readonly id: string;
  readonly tipo: TipoDocumentoVenta;
  readonly numeroCompleto: string;
}

export interface ResumenDocumentoApi {
  readonly id: string;
  readonly tipo: TipoDocumentoVenta;
  readonly tipoNombre: string;
  readonly numeroCompleto: string;
  readonly estado: EstadoDocumentoVenta;
  /** Nulo en una nota de venta: no se declara. */
  readonly estadoSunat: EstadoSunat | null;
  readonly fechaEmision: string;
  readonly emitidoEn: string;
  readonly cliente: string | null;
  readonly clienteDocumento: string | null;
  readonly total: number;
}

export interface EmisionApi {
  readonly documento: DocumentoVentaApi;
  /** Lo que conviene decir sin impedir: existencias que quedaron en negativo. */
  readonly avisos: string[];
}

export interface SerieDisponibleApi {
  readonly id: string;
  readonly serie: string;
  readonly siguienteNumero: string;
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

  // ── Documentos de venta ────────────────────────────────────────────────

  emitirNotaDeVenta(venta: PeticionVenta): Promise<EmisionApi> {
    return firstValueFrom(this.http.post<EmisionApi>(`${this.base}/notas-de-venta`, venta));
  }

  /** Boleta o factura. Queda PENDIENTE de envío a SUNAT. */
  emitirComprobante(tipo: TipoComprobanteEmitible, venta: PeticionVenta): Promise<EmisionApi> {
    return firstValueFrom(this.http.post<EmisionApi>(`${this.base}/comprobantes`, { tipo, venta }));
  }

  notasDeVenta(): Promise<ResumenDocumentoApi[]> {
    return firstValueFrom(this.http.get<ResumenDocumentoApi[]>(`${this.base}/notas-de-venta`));
  }

  comprobantes(tipo?: TipoComprobanteEmitible): Promise<ResumenDocumentoApi[]> {
    const params = tipo ? new HttpParams().set('tipo', tipo) : undefined;
    return firstValueFrom(this.http.get<ResumenDocumentoApi[]>(`${this.base}/comprobantes`, { params }));
  }

  /** Por la puerta que corresponde al tipo: los permisos son distintos. */
  documento(id: string, tipo: TipoDocumentoVenta): Promise<DocumentoVentaApi> {
    const ruta = tipo === 'NV' ? 'notas-de-venta' : 'comprobantes';
    return firstValueFrom(this.http.get<DocumentoVentaApi>(`${this.base}/${ruta}/${id}`));
  }

  seriesDisponibles(tipo: 'NOTA_VENTA' | TipoComprobanteEmitible, sucursalId: string): Promise<SerieDisponibleApi[]> {
    const params = new HttpParams().set('tipo', tipo).set('sucursalId', sucursalId);
    return firstValueFrom(this.http.get<SerieDisponibleApi[]>(`${this.base}/series`, { params }));
  }

  // ── Emisión electrónica (doc 14) ──────────────────────────────────────────
  //
  // Cuelga del documento: la pantalla tiene su id y no tiene por qué conocer
  // el del comprobante electrónico. Mientras está EN_COLA, cada consulta mira
  // si el Emisor ya respondió, así que la pantalla la repite cada pocos
  // segundos en lugar de esperar un aviso.

  estadoSunat(documentoId: string): Promise<EstadoSunatApi> {
    return firstValueFrom(this.http.get<EstadoSunatApi>(`${this.base}/comprobantes/${documentoId}/sunat`));
  }

  /** Con el mismo número: para SUNAT un comprobante rechazado no existe. */
  reintentarEnvio(documentoId: string): Promise<EstadoSunatApi> {
    return firstValueFrom(this.http.post<EstadoSunatApi>(`${this.base}/comprobantes/${documentoId}/sunat/reintento`, {}));
  }

  /** URL temporal del XML firmado. Se abre en el navegador; la API no sirve el archivo. */
  async urlDelXml(documentoId: string): Promise<string> {
    const r = await firstValueFrom(this.http.get<{ url: string }>(`${this.base}/comprobantes/${documentoId}/sunat/xml`));
    return r.url;
  }

  async urlDelCdr(documentoId: string): Promise<string> {
    const r = await firstValueFrom(this.http.get<{ url: string }>(`${this.base}/comprobantes/${documentoId}/sunat/cdr`));
    return r.url;
  }

  // ── Anular y canjear (doc 13 §5) ──────────────────────────────────────────
  //
  // Anular y emitir una nota de crédito son dos rutas distintas y no un
  // parámetro, porque exigen permisos distintos: anular un comprobante ya
  // emitido tiene efecto tributario y quien atiende el mostrador casi nunca
  // debe poder hacerlo.

  motivosDeNotaDeCredito(): Promise<MotivoNotaCredito[]> {
    return firstValueFrom(this.http.get<MotivoNotaCredito[]>(`${this.base}/notas-de-credito/motivos`));
  }

  /** Deja sin efecto el comprobante entero. */
  anularComprobante(documentoId: string, datos: PeticionAnulacion): Promise<DocumentoVentaApi> {
    return firstValueFrom(
      this.http.post<DocumentoVentaApi>(`${this.base}/comprobantes/${documentoId}/anulacion`, datos)
    );
  }

  /** Devolución por ítem, descuentos y correcciones. */
  emitirNotaDeCredito(datos: PeticionNotaDeCredito): Promise<DocumentoVentaApi> {
    return firstValueFrom(this.http.post<DocumentoVentaApi>(`${this.base}/notas-de-credito`, datos));
  }

  notasDeCredito(): Promise<ResumenDocumentoApi[]> {
    return firstValueFrom(this.http.get<ResumenDocumentoApi[]>(`${this.base}/notas-de-credito`));
  }

  notaDeCredito(id: string): Promise<DocumentoVentaApi> {
    return firstValueFrom(this.http.get<DocumentoVentaApi>(`${this.base}/notas-de-credito/${id}`));
  }

  /** Las series de nota de crédito que sirven para ese comprobante: su misma letra. */
  seriesDeNotaDeCredito(documentoId: string): Promise<SerieDisponibleApi[]> {
    return firstValueFrom(
      this.http.get<SerieDisponibleApi[]>(`${this.base}/comprobantes/${documentoId}/series-nota-credito`)
    );
  }

  /** Lo que salió de este documento: sus notas de crédito, o el comprobante que lo canjeó. */
  relacionadosCon(documentoId: string): Promise<ResumenDocumentoApi[]> {
    return firstValueFrom(
      this.http.get<ResumenDocumentoApi[]>(`${this.base}/documentos/${documentoId}/relacionados`)
    );
  }

  canjear(notaDeVentaId: string, datos: PeticionCanje): Promise<DocumentoVentaApi> {
    return firstValueFrom(
      this.http.post<DocumentoVentaApi>(`${this.base}/notas-de-venta/${notaDeVentaId}/canje`, datos)
    );
  }

  seriesDeCanje(notaDeVentaId: string, tipo: TipoComprobanteEmitible): Promise<SerieDisponibleApi[]> {
    const params = new HttpParams().set('tipo', tipo);
    return firstValueFrom(
      this.http.get<SerieDisponibleApi[]>(`${this.base}/notas-de-venta/${notaDeVentaId}/series-canje`, { params })
    );
  }

  // ── Comunicación de baja (doc 13 §6) ──────────────────────────────────────
  //
  // El envío es asíncrono: SUNAT devuelve un ticket y el veredicto llega
  // después. El servidor sincroniza al listar y al abrir una, así que la
  // pantalla no tiene que orquestar nada: vuelve a pedir y ya.

  comunicacionesDeBaja(): Promise<ComunicacionDeBajaApi[]> {
    return firstValueFrom(this.http.get<ComunicacionDeBajaApi[]>(`${this.base}/comunicaciones-de-baja`));
  }

  comunicacionDeBaja(id: string): Promise<ComunicacionDeBajaApi> {
    return firstValueFrom(this.http.get<ComunicacionDeBajaApi>(`${this.base}/comunicaciones-de-baja/${id}`));
  }

  darDeBaja(comprobantes: ComprobanteADarDeBaja[]): Promise<ComunicacionDeBajaApi> {
    return firstValueFrom(
      this.http.post<ComunicacionDeBajaApi>(`${this.base}/comunicaciones-de-baja`, { comprobantes })
    );
  }

  reintentarBaja(id: string): Promise<ComunicacionDeBajaApi> {
    return firstValueFrom(
      this.http.post<ComunicacionDeBajaApi>(`${this.base}/comunicaciones-de-baja/${id}/reintento`, {})
    );
  }
}

/** Cómo se devolvió el dinero. Vacío: no se devolvió nada ahora. */
export interface PagoDevuelto {
  readonly forma: FormaDePago;
  readonly monto: number;
  readonly referencia?: string | null;
}

export interface PeticionAnulacion {
  /** Ausente se toma como anulación de la operación, que es el caso normal. */
  readonly motivo?: string;
  readonly cajaId: string;
  readonly serieId?: string | null;
  readonly pagos?: PagoDevuelto[];
  readonly observaciones?: string | null;
}

/** @param orden el de la línea del comprobante original. */
export interface LineaAcreditada {
  readonly orden: number;
  readonly cantidad: number;
}

export interface PeticionNotaDeCredito {
  readonly documentoId: string;
  readonly motivo: string;
  readonly cajaId: string;
  readonly serieId?: string | null;
  readonly lineas?: LineaAcreditada[];
  readonly pagos?: PagoDevuelto[];
  readonly observaciones?: string | null;
}

export interface PeticionCanje {
  readonly tipo: TipoComprobanteEmitible;
  readonly serieId?: string | null;
  readonly clienteId?: string | null;
  readonly observaciones?: string | null;
}

/** Un comprobante que se comunica a SUNAT como no emitido. */
export interface ComprobanteADarDeBaja {
  readonly documentoId: string;
  readonly motivo: string;
}

export interface ComprobanteDeBajaApi {
  readonly documentoId: string;
  readonly tipo: TipoDocumentoVenta;
  readonly numeroCompleto: string;
  readonly motivo: string;
}

/**
 * Una comunicación de baja. Su estado es el de SUNAT, con un valor que un
 * comprobante nunca tiene: `EN_PROCESO` significa que SUNAT dio un ticket y
 * todavía no hay veredicto.
 */
export interface ComunicacionDeBajaApi {
  readonly id: string;
  /** `RA-20260909-1`, como SUNAT la identifica. */
  readonly identificador: string;
  readonly fechaComprobantes: string;
  readonly fechaGeneracion: string;
  readonly estado: EstadoSunat;
  readonly intentos: number;
  readonly ticket: string | null;
  readonly codigo: string | null;
  readonly descripcion: string | null;
  readonly encoladaEn: string | null;
  readonly respondidaEn: string | null;
  readonly xmlDisponible: boolean;
  readonly cdrDisponible: boolean;
  readonly admiteReintento: boolean;
  /** Los que quedan para comunicar la baja; negativo si el plazo venció. */
  readonly diasDePlazo: number;
  readonly comprobantes: ComprobanteDeBajaApi[];
}
