import { Component, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { accionConEstado } from '../../../shared/components/comunes/boton/estado-accion';
import {
  BuscadorEntidadComponent,
  OpcionEntidad,
} from '../../../shared/components/comunes/buscador-entidad/buscador-entidad.component';
import { DesplegableComponent, OpcionDesplegable } from '../../../shared/components/comunes/desplegable/desplegable.component';
import { AlmacenApiService, ProductoDisponibleApi } from '../../../nucleo/almacen.api.service';
import {
  ClienteApi,
  FORMAS_DE_PAGO,
  FormaDePago,
  PeticionVenta,
  SerieDisponibleApi,
  TipoComprobanteEmitible,
  VentasApiService,
} from '../../../nucleo/ventas.api.service';
import { CajaActivaService } from '../../../nucleo/caja-activa.service';
import { ConfiguracionApiService } from '../../../nucleo/configuracion.api.service';
import { mensajeDeError } from '../../../nucleo/errores';
import { ContextoService } from '../../../shared/services/contexto.service';
import { AvisosService } from '../../../shared/services/avisos.service';

export type TipoVenta = 'NV' | 'BOLETA' | 'FACTURA';

export interface LineaPos {
  readonly productoId: string;
  readonly codigo: string;
  readonly descripcion: string;
  readonly unidad: string;
  readonly precio: number;
  readonly llevaIgv: boolean;
  readonly existencia: number | null;
  cantidad: number;
  descuento: number;
}

export interface PagoPos {
  forma: FormaDePago;
  /**
   * Lo que se escribe en la casilla.
   *
   * En efectivo es lo que el cliente ENTREGA, que es lo que un cajero tiene
   * en la mano: si la venta son S/ 98 y da un billete de 100, escribe 100. Lo
   * que se aplica al comprobante lo calcula `aplicaciones()`, y la diferencia
   * es el vuelto. En las demás formas de pago es el importe exacto, porque un
   * datáfono no devuelve suelto.
   */
  monto: number | null;
  referencia: string;
}

/** Un renglón de cobro con lo escrito ya repartido entre importe y vuelto. */
export interface PagoAplicado {
  readonly pago: PagoPos;
  /** Lo que este renglón aporta al total del documento. */
  readonly aplicado: number;
  /** Lo que sobra y hay que devolver. Siempre cero fuera del efectivo. */
  readonly vuelto: number;
}

/** Redondeo a céntimos, que es lo que el servidor valida. */
export function redondear(valor: number): number {
  return Math.round((valor + Number.EPSILON) * 100) / 100;
}

/** El total de una línea: cantidad por precio con IGV menos el descuento, en céntimos. */
export function totalDeLinea(linea: { cantidad: number; precio: number; descuento: number }): number {
  return Math.max(0, redondear(linea.cantidad * linea.precio - (linea.descuento || 0)));
}

/**
 * El punto de venta (doc 12 §8, iteración 4).
 *
 * <p>La pantalla no calcula nada que decida: los precios vienen del catálogo
 * del local y el servidor recalcula totales, IGV y correlativo al emitir. Lo
 * que sí hace es adelantar lo que el servidor va a decir —el total, lo que
 * falta cobrar, si la boleta necesita cliente— para que el cajero no descubra
 * la regla con un error.
 *
 * <p>Sin caja abierta no hay venta: la pantalla lo dice y lleva a Cajas.
 */
@Component({
  selector: 'app-punto-de-venta',
  imports: [EncabezadoPaginaComponent, BotonComponent, BuscadorEntidadComponent, DesplegableComponent, FormsModule],
  templateUrl: './punto-de-venta.component.html',
})
export class PuntoDeVentaComponent {
  private readonly ventas = inject(VentasApiService);
  private readonly almacen = inject(AlmacenApiService);
  private readonly configuracion = inject(ConfiguracionApiService);
  private readonly cajaActiva = inject(CajaActivaService);
  private readonly contexto = inject(ContextoService);
  private readonly avisos = inject(AvisosService);
  private readonly router = inject(Router);

  readonly formasDePago = FORMAS_DE_PAGO;
  readonly TOPE_BOLETA_SIN_DOCUMENTO = 700;

  readonly tipo = signal<TipoVenta>('NV');
  readonly lineas = signal<LineaPos[]>([]);
  readonly pagos = signal<PagoPos[]>([{ forma: 'EFECTIVO', monto: null, referencia: '' }]);
  readonly cliente = signal<ClienteApi | null>(null);
  readonly observaciones = signal('');
  readonly series = signal<SerieDisponibleApi[]>([]);
  readonly serieId = signal<string>('');
  readonly emiteFacturas = signal(true);

  readonly opcionesProducto = signal<OpcionEntidad[]>([]);
  readonly opcionesCliente = signal<OpcionEntidad[]>([]);
  readonly buscandoProducto = signal(false);
  private productosEncontrados: ProductoDisponibleApi[] = [];
  private clientesEncontrados: ClienteApi[] = [];

  /** La caja abierta del establecimiento activo; sin ella no se vende. */
  readonly caja = this.cajaActiva.enUso;
  readonly sucursalId = computed(() => this.caja()?.sucursalId ?? null);

  readonly puedeNotaDeVenta = computed(() => this.contexto.puede('ventas.nota_venta:registrar'));
  readonly puedeComprobante = computed(() => this.contexto.puede('ventas.comprobante:emitir'));

  readonly opcionesTipo = computed<OpcionDesplegable[]>(() => {
    const opciones: OpcionDesplegable[] = [];
    if (this.puedeNotaDeVenta()) {
      opciones.push({ valor: 'NV', etiqueta: 'Nota de venta', detalle: 'interna' });
    }
    if (this.puedeComprobante()) {
      opciones.push({ valor: 'BOLETA', etiqueta: 'Boleta de venta', detalle: '03' });
      if (this.emiteFacturas()) {
        opciones.push({ valor: 'FACTURA', etiqueta: 'Factura', detalle: '01' });
      }
    }
    return opciones;
  });

  readonly opcionesSerie = computed<OpcionDesplegable[]>(() =>
    this.series().map((s) => ({ valor: s.id, etiqueta: s.serie, detalle: s.siguienteNumero }))
  );

  readonly total = computed(() => redondear(this.lineas().reduce((suma, l) => suma + totalDeLinea(l), 0)));
  readonly igvEstimado = computed(() =>
    redondear(
      this.lineas()
        .filter((l) => l.llevaIgv)
        .reduce((suma, l) => suma + (totalDeLinea(l) - redondear(totalDeLinea(l) / 1.18)), 0)
    )
  );
  /**
   * Reparte lo escrito en cada renglón entre lo que se aplica y lo que se
   * devuelve.
   *
   * El efectivo no puede pasarse: lo que exceda de lo que queda por cobrar es
   * vuelto, no un cobro de más. Las demás formas sí pueden pasarse, y entonces
   * es un error — nadie devuelve suelto de una transferencia.
   *
   * Se recorre en orden y descontando, para que en un pago mixto cada renglón
   * vea lo que dejaron los anteriores.
   */
  readonly aplicaciones = computed<PagoAplicado[]>(() => {
    let restante = this.total();
    return this.pagos().map((pago) => {
      const escrito = redondear(Number(pago.monto) || 0);
      const aplicado =
        pago.forma === 'EFECTIVO' ? Math.min(escrito, Math.max(restante, 0)) : escrito;
      restante = redondear(restante - aplicado);
      return { pago, aplicado, vuelto: redondear(escrito - aplicado) };
    });
  });

  readonly cobrado = computed(() =>
    redondear(this.aplicaciones().reduce((suma, a) => suma + a.aplicado, 0))
  );
  readonly porCobrar = computed(() => redondear(this.total() - this.cobrado()));
  /** Lo que hay que devolver de la gaveta. Cero salvo que alguien pague con un billete grande. */
  readonly vuelto = computed(() =>
    redondear(this.aplicaciones().reduce((suma, a) => suma + a.vuelto, 0))
  );

  /** Lo que el servidor va a decir, dicho antes. */
  readonly impedimento = computed<string | null>(() => {
    if (!this.caja()) {
      return 'No hay una caja abierta en este establecimiento.';
    }
    if (this.lineas().length === 0) {
      return 'Agrega al menos un producto.';
    }
    if (this.tipo() === 'FACTURA' && this.cliente()?.tipoDocumento !== 'RUC') {
      return 'Una factura exige un cliente con RUC.';
    }
    if (this.tipo() === 'BOLETA' && !this.cliente() && this.total() > this.TOPE_BOLETA_SIN_DOCUMENTO) {
      return `Una boleta de más de S/ ${this.TOPE_BOLETA_SIN_DOCUMENTO} tiene que identificar al adquirente.`;
    }
    if (this.porCobrar() !== 0) {
      return this.porCobrar() > 0
        ? `Falta cobrar ${this.importe(this.porCobrar())}.`
        : `Los pagos superan el total en ${this.importe(-this.porCobrar())}. Solo el efectivo`
          + ' admite entregar de más, y se devuelve como vuelto.';
    }
    if (this.series().length > 1 && !this.serieId()) {
      return 'Falta elegir la serie.';
    }
    return null;
  });

  constructor() {
    void this.cargarEmpresa();
    // Las series dependen del tipo y del local de la caja abierta.
    effect(() => {
      const tipo = this.tipo();
      const sucursal = this.sucursalId();
      if (!sucursal) {
        this.series.set([]);
        return;
      }
      void this.cargarSeries(tipo, sucursal);
    });
  }

  private async cargarEmpresa(): Promise<void> {
    try {
      const empresa = await this.configuracion.empresa();
      this.emiteFacturas.set(empresa.emiteFacturas);
    } catch {
      // Sin la ficha, se asume que factura: el servidor tiene la última palabra.
    }
  }

  private async cargarSeries(tipo: TipoVenta, sucursalId: string): Promise<void> {
    try {
      const series = await this.ventas.seriesDisponibles(tipo === 'NV' ? 'NOTA_VENTA' : tipo, sucursalId);
      this.series.set(series);
      this.serieId.set(series.length === 1 ? series[0].id : '');
    } catch (fallo: unknown) {
      this.series.set([]);
      this.avisos.error(mensajeDeError(fallo, 'No se pudieron cargar las series.'));
    }
  }

  cambiarTipo(valor: string): void {
    this.tipo.set(valor as TipoVenta);
  }

  // ── Productos ──────────────────────────────────────────────────────────

  async buscarProducto(termino: string): Promise<void> {
    const sucursal = this.sucursalId();
    if (!sucursal || termino.trim().length < 2) {
      this.opcionesProducto.set([]);
      return;
    }
    this.buscandoProducto.set(true);
    try {
      this.productosEncontrados = await this.almacen.disponibles(sucursal, termino.trim());
      this.opcionesProducto.set(
        this.productosEncontrados.map((p) => ({
          id: p.id,
          titulo: p.nombre,
          detalle: `${p.codigo} · ${p.unidadNombre}${p.existencia === null ? '' : ' · ' + this.cantidad(p.existencia) + ' en el local'}`,
          extremo: this.importe(p.precio),
        }))
      );
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo buscar en el catálogo.'));
    } finally {
      this.buscandoProducto.set(false);
    }
  }

  agregarProducto(opcion: OpcionEntidad): void {
    const producto = this.productosEncontrados.find((p) => p.id === opcion.id);
    if (!producto) {
      return;
    }
    const actuales = this.lineas();
    const existente = actuales.find((l) => l.productoId === producto.id);
    if (existente) {
      // El mismo producto dos veces suma cantidad: es lo que hace el mostrador.
      this.lineas.set(actuales.map((l) => (l === existente ? { ...l, cantidad: l.cantidad + 1 } : l)));
      return;
    }
    this.lineas.set([
      ...actuales,
      {
        productoId: producto.id,
        codigo: producto.codigo,
        descripcion: producto.nombre,
        unidad: producto.unidadNombre,
        precio: producto.precio,
        llevaIgv: producto.llevaIgv,
        existencia: producto.existencia,
        cantidad: 1,
        descuento: 0,
      },
    ]);
  }

  cambiarCantidad(linea: LineaPos, valor: string | number): void {
    const cantidad = Number(valor);
    this.lineas.set(this.lineas().map((l) => (l === linea ? { ...l, cantidad: cantidad > 0 ? cantidad : 1 } : l)));
  }

  cambiarDescuento(linea: LineaPos, valor: string | number): void {
    const descuento = Math.max(0, Number(valor) || 0);
    this.lineas.set(this.lineas().map((l) => (l === linea ? { ...l, descuento } : l)));
  }

  quitarLinea(linea: LineaPos): void {
    this.lineas.set(this.lineas().filter((l) => l !== linea));
  }

  totalLinea(linea: LineaPos): number {
    return totalDeLinea(linea);
  }

  sinExistencias(linea: LineaPos): boolean {
    return linea.existencia !== null && linea.existencia < linea.cantidad;
  }

  // ── Cliente ────────────────────────────────────────────────────────────

  async buscarCliente(termino: string): Promise<void> {
    if (termino.trim().length < 2) {
      this.opcionesCliente.set([]);
      return;
    }
    try {
      this.clientesEncontrados = (await this.ventas.clientes(termino.trim())).filter((c) => c.activo);
      this.opcionesCliente.set(
        this.clientesEncontrados.map((c) => ({
          id: c.id,
          titulo: c.nombre,
          detalle: `${c.tipoDocumentoNombre} ${c.numeroDocumento}`,
          deshabilitada: this.tipo() === 'FACTURA' && c.tipoDocumento !== 'RUC',
        }))
      );
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo buscar el cliente.'));
    }
  }

  elegirCliente(opcion: OpcionEntidad): void {
    this.cliente.set(this.clientesEncontrados.find((c) => c.id === opcion.id) ?? null);
  }

  quitarCliente(): void {
    this.cliente.set(null);
  }

  // ── Pagos ──────────────────────────────────────────────────────────────

  agregarPago(): void {
    const usadas = new Set(this.pagos().map((p) => p.forma));
    const libre = FORMAS_DE_PAGO.find((f) => !usadas.has(f.codigo))?.codigo ?? 'EFECTIVO';
    this.pagos.set([...this.pagos(), { forma: libre, monto: null, referencia: '' }]);
  }

  quitarPago(pago: PagoPos): void {
    if (this.pagos().length === 1) {
      return;
    }
    this.pagos.set(this.pagos().filter((p) => p !== pago));
  }

  cambiarFormaDePago(pago: PagoPos, valor: string): void {
    this.pagos.set(this.pagos().map((p) => (p === pago ? { ...p, forma: valor as FormaDePago } : p)));
  }

  cambiarMonto(pago: PagoPos, valor: string | number): void {
    const monto = valor === '' || valor === null ? null : Number(valor);
    this.pagos.set(this.pagos().map((p) => (p === pago ? { ...p, monto } : p)));
  }

  cambiarReferencia(pago: PagoPos, valor: string): void {
    this.pagos.set(this.pagos().map((p) => (p === pago ? { ...p, referencia: valor } : p)));
  }

  /**
   * Pone en este pago lo que falta: el gesto más frecuente del mostrador.
   *
   * Parte de lo APLICADO de este renglón y no de lo escrito, para que pulsarlo
   * sobre un efectivo con vuelto lo deje en el importe justo en vez de
   * conservar el billete: quien pulsa «Resto» está pidiendo cuadrar, no
   * cobrar de más.
   */
  completarPago(pago: PagoPos): void {
    const aplicado = this.aplicaciones().find((a) => a.pago === pago)?.aplicado ?? 0;
    const resto = redondear(this.porCobrar() + aplicado);
    this.cambiarMonto(pago, resto > 0 ? resto : 0);
  }

  // ── Emitir ─────────────────────────────────────────────────────────────

  peticion(): PeticionVenta {
    return {
      cajaId: this.caja()!.id,
      serieId: this.serieId() || null,
      clienteId: this.cliente()?.id ?? null,
      lineas: this.lineas().map((l) => ({
        productoId: l.productoId,
        cantidad: l.cantidad,
        descuento: l.descuento || null,
      })),
      // Se manda lo APLICADO como monto y, solo si hubo vuelto, lo entregado.
      // El comprobante y el arqueo cuadran con el monto; el billete del cliente
      // queda aparte.
      pagos: this.aplicaciones()
        .filter((a) => a.aplicado > 0)
        .map((a) => ({
          forma: a.pago.forma,
          monto: a.aplicado,
          referencia: a.pago.referencia || null,
          entregado: a.vuelto > 0 ? redondear(a.aplicado + a.vuelto) : null,
        })),
      observaciones: this.observaciones() || null,
    };
  }

  readonly emitir = accionConEstado(async () => {
    const impedimento = this.impedimento();
    if (impedimento) {
      this.avisos.error(impedimento);
      throw new Error(impedimento);
    }
    try {
      const tipo = this.tipo();
      const emision =
        tipo === 'NV'
          ? await this.ventas.emitirNotaDeVenta(this.peticion())
          : await this.ventas.emitirComprobante(tipo as TipoComprobanteEmitible, this.peticion());
      for (const aviso of emision.avisos) {
        this.avisos.error(aviso, 'Existencias');
      }
      this.avisos.exito(
        `${emision.documento.tipoNombre} ${emision.documento.numeroCompleto} por ${this.importe(emision.documento.total)}.`,
        tipo === 'NV' ? 'Nota de venta emitida' : 'Comprobante registrado, pendiente de SUNAT'
      );
      this.limpiar();
      await this.router.navigate(['/ventas/documentos', emision.documento.tipo, emision.documento.id]);
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo emitir.'));
      throw fallo;
    }
  });

  limpiar(): void {
    this.lineas.set([]);
    this.pagos.set([{ forma: 'EFECTIVO', monto: null, referencia: '' }]);
    this.cliente.set(null);
    this.observaciones.set('');
    this.opcionesProducto.set([]);
    this.opcionesCliente.set([]);
  }

  importe(valor: number): string {
    return Number(valor).toLocaleString('es-PE', { style: 'currency', currency: 'PEN', minimumFractionDigits: 2 });
  }

  cantidad(valor: number): string {
    return Number(valor).toLocaleString('es-PE', { maximumFractionDigits: 6 });
  }
}
