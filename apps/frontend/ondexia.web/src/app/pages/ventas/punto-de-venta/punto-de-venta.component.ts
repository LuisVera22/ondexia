import { decimal, entradaDecimalValida, importeDecimal, normalizarEntradaDecimal } from '../../../nucleo/decimal-exacto';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AltaClienteVentaComponent } from '../clientes/alta-cliente-venta.component';
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
  readonly precio: string;
  readonly llevaIgv: boolean;
  readonly existencia: string | null;
  cantidad: string;
  descuento: string;
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
  monto: string | null;
  referencia: string;
}

/** Un renglón de cobro con lo escrito ya repartido entre importe y vuelto. */
export interface PagoAplicado {
  readonly pago: PagoPos;
  /** Lo que este renglón aporta al total del documento. */
  readonly aplicado: string;
  /** Lo que sobra y hay que devolver. Siempre cero fuera del efectivo. */
  readonly vuelto: string;
}

/** HALF_UP decimal, según LineaDeVenta.calcular. */
export function redondear(valor: string): string { return decimal(valor).redondear(2).texto(); }

export function totalDeLinea(linea: { cantidad: string; precio: string; descuento: string }): string {
  if (!entradaDecimalValida(linea.cantidad, 6) || !entradaDecimalValida(linea.descuento, 2)) return '0.00';
  const total = decimal(linea.cantidad).multiplicar(decimal(linea.precio)).restar(decimal(linea.descuento)).redondear(2);
  return total.comparar(decimal('0')) < 0 ? '0.00' : total.texto();
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
  imports: [EncabezadoPaginaComponent, BotonComponent, BuscadorEntidadComponent, DesplegableComponent, FormsModule, AltaClienteVentaComponent],
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
  private readonly ruta = inject(ActivatedRoute);

  readonly formasDePago = FORMAS_DE_PAGO;
  readonly TOPE_BOLETA_SIN_DOCUMENTO = '700';

  readonly tipoFijo = signal<TipoVenta | null>(null);
  readonly tipo = signal<TipoVenta>('NV');
  readonly nombreTipo = computed(() => this.tipo() === 'NV' ? 'Nota de venta' : this.tipo() === 'BOLETA' ? 'Boleta de venta' : 'Factura');
  readonly titulo = computed(() => this.tipoFijo() ? this.nombreTipo() : 'Punto de venta');
  private cargaSeries = 0;
  private cargaEmpresa = 0;
  private ultimoContexto = this.claveContexto();
  readonly lineas = signal<LineaPos[]>([]);
  readonly pagos = signal<PagoPos[]>([{ forma: 'EFECTIVO', monto: null, referencia: '' }]);
  readonly cliente = signal<ClienteApi | null>(null);
  readonly altaClienteAbierta = signal(false);
  readonly puedeRegistrarCliente = computed(() => this.contexto.puede('ventas.cliente:registrar') && this.contexto.puede('ventas.cliente:consultar'));
  readonly observaciones = signal('');
  readonly series = signal<SerieDisponibleApi[]>([]);
  readonly serieId = signal<string>('');
  readonly emiteFacturas = signal(false);

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

  readonly total = computed(() => this.lineas().reduce((suma, linea) =>
    suma.sumar(decimal(totalDeLinea(linea))), decimal('0')).redondear(2).texto());
  readonly igvEstimado = computed(() => this.lineas().filter(l => l.llevaIgv).reduce((suma, linea) => {
    const total = decimal(totalDeLinea(linea));
    return suma.sumar(total.restar(total.dividir(decimal('1.18'), 2)));
  }, decimal('0')).redondear(2).texto());

  /** Recorrido en orden: solo el efectivo excedente se devuelve como vuelto. */
  readonly aplicaciones = computed<PagoAplicado[]>(() => {
    let restante = decimal(this.total());
    return this.pagos().map(pago => {
      const escrito = decimal(pago.monto && entradaDecimalValida(pago.monto, 2) ? pago.monto : '0');
      const limite = restante.comparar(decimal('0')) > 0 ? restante : decimal('0');
      const aplicado = pago.forma === 'EFECTIVO' && escrito.comparar(limite) > 0 ? limite : escrito;
      restante = restante.restar(aplicado);
      return { pago, aplicado: aplicado.redondear(2).texto(), vuelto: escrito.restar(aplicado).redondear(2).texto() };
    });
  });
  readonly cobrado = computed(() => this.aplicaciones().reduce((suma, a) =>
    suma.sumar(decimal(a.aplicado)), decimal('0')).redondear(2).texto());
  readonly porCobrar = computed(() => decimal(this.total()).restar(decimal(this.cobrado())).redondear(2).texto());
  readonly vuelto = computed(() => this.aplicaciones().reduce((suma, a) =>
    suma.sumar(decimal(a.vuelto)), decimal('0')).redondear(2).texto());
  readonly signoPorCobrar = computed(() => decimal(this.porCobrar()).comparar(decimal('0')));
  readonly porCobrarAbsoluto = computed(() => this.signoPorCobrar() < 0
    ? decimal(this.porCobrar()).negar().texto() : this.porCobrar());
  readonly tieneVuelto = computed(() => decimal(this.vuelto()).comparar(decimal('0')) > 0);

  /** Lo que el servidor va a decir, dicho antes. */
  readonly impedimento = computed<string | null>(() => {
    if (!this.opcionesTipo().some((opcion) => opcion.valor === this.tipo())) {
      return this.tipo() === 'FACTURA' && !this.emiteFacturas()
        ? 'Esta empresa no tiene habilitada la emisión de facturas.'
        : 'No tienes permiso para emitir este tipo de documento.';
    }
    if (!this.caja()) {
      return 'No hay una caja abierta en este establecimiento.';
    }
    if (this.lineas().length === 0) {
      return 'Agrega al menos un producto.';
    }
    for (const linea of this.lineas()) {
      if (!entradaDecimalValida(linea.cantidad, 6) || decimal(linea.cantidad).comparar(decimal('0')) <= 0)
        return 'Indica una cantidad mayor que cero, con hasta seis decimales.';
      if (!entradaDecimalValida(linea.descuento, 2)) return 'El descuento debe ser positivo o cero y expresarse en céntimos.';
      if (decimal(linea.cantidad).multiplicar(decimal(linea.precio)).restar(decimal(linea.descuento)).redondear(2).comparar(decimal('0')) < 0)
        return 'El descuento no puede superar el total de la línea.';
    }
    if (this.pagos().some(p => p.monto !== null && !entradaDecimalValida(p.monto, 2)))
      return 'Indica un pago positivo o cero, con hasta dos decimales.';
    if (this.tipo() === 'FACTURA' && this.cliente()?.tipoDocumento !== 'RUC') {
      return 'Una factura exige un cliente con RUC.';
    }
    if (this.tipo() === 'BOLETA' && !this.cliente() && decimal(this.total()).comparar(decimal(this.TOPE_BOLETA_SIN_DOCUMENTO)) > 0) {
      return `Una boleta de más de S/ ${this.TOPE_BOLETA_SIN_DOCUMENTO} tiene que identificar al adquirente.`;
    }
    if (this.signoPorCobrar() !== 0) {
      return this.signoPorCobrar() > 0
        ? `Falta cobrar ${this.importe(this.porCobrar())}.`
        : `Los pagos superan el total en ${this.importe(this.porCobrarAbsoluto())}. Solo el efectivo`
          + ' admite entregar de más, y se devuelve como vuelto.';
    }
    if (this.series().length > 1 && !this.serieId()) {
      return 'Falta elegir la serie.';
    }
    return null;
  });

  constructor() {
    this.ruta.data.pipe(takeUntilDestroyed()).subscribe((datos) => {
      const fijo = datos['tipoFijo'] as TipoVenta | undefined;
      if (this.tipoFijo() !== (fijo ?? null)) {
        this.limpiar();
        this.tipoFijo.set(fijo ?? null);
        this.tipo.set(fijo ?? 'NV');
      }
    });
    void this.cargarEmpresa();
    effect(() => {
      const clave = this.claveContexto();
      if (clave !== this.ultimoContexto) {
        this.ultimoContexto = clave;
        this.limpiar();
        this.productosEncontrados = [];
        this.clientesEncontrados = [];
        this.emiteFacturas.set(false);
        void this.cargarEmpresa();
      }
    });
    // Las series dependen del tipo y del local de la caja abierta.
    effect(() => {
      const tipo = this.tipo();
      const sucursal = this.sucursalId();
      if (!sucursal) {
        this.cargaSeries++;
        this.series.set([]);
        this.serieId.set('');
        return;
      }
      void this.cargarSeries(tipo, sucursal);
    });
  }

  private claveContexto(): string {
    return `${this.contexto.empresaActiva()?.id ?? ''}/${this.contexto.establecimientoActivo()?.id ?? ''}`;
  }

  private async cargarEmpresa(): Promise<void> {
    const carga = ++this.cargaEmpresa;
    const clave = this.claveContexto();
    try {
      const empresa = await this.configuracion.empresa();
      if (carga === this.cargaEmpresa && clave === this.claveContexto()) this.emiteFacturas.set(empresa.emiteFacturas);
    } catch {
      if (carga === this.cargaEmpresa && clave === this.claveContexto()) this.emiteFacturas.set(false);
    }
  }

  private async cargarSeries(tipo: TipoVenta, sucursalId: string): Promise<void> {
    const carga = ++this.cargaSeries;
    this.series.set([]);
    this.serieId.set('');
    try {
      const series = await this.ventas.seriesDisponibles(tipo === 'NV' ? 'NOTA_VENTA' : tipo, sucursalId);
      if (carga !== this.cargaSeries) return;
      this.series.set(series);
      this.serieId.set(series.length === 1 ? series[0].id : '');
    } catch (fallo: unknown) {
      if (carga !== this.cargaSeries) return;
      this.series.set([]);
      this.avisos.error(mensajeDeError(fallo, 'No se pudieron cargar las series.'));
    }
  }

  cambiarTipo(valor: string): void {
    if (!this.tipoFijo() && this.opcionesTipo().some((opcion) => opcion.valor === valor)) {
      this.tipo.set(valor as TipoVenta);
    }
  }

  // ── Productos ──────────────────────────────────────────────────────────

  async buscarProducto(termino: string): Promise<void> {
    const sucursal = this.sucursalId();
    if (!sucursal || termino.trim().length < 2) {
      this.opcionesProducto.set([]);
      return;
    }
    this.buscandoProducto.set(true);
    const clave = this.claveContexto();
    try {
      const productos = await this.almacen.disponibles(sucursal, termino.trim());
      if (clave !== this.claveContexto()) return;
      this.productosEncontrados = productos;
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
      this.lineas.set(actuales.map((l) => (l === existente ? { ...l, cantidad: entradaDecimalValida(l.cantidad, 6) ? decimal(l.cantidad).sumar(decimal('1')).texto() : l.cantidad } : l)));
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
        cantidad: '1',
        descuento: '0',
      },
    ]);
  }

  cambiarCantidad(linea: LineaPos, valor: string | number): void {
    const cantidad = normalizarEntradaDecimal(String(valor));
    this.lineas.set(this.lineas().map((l) => (l === linea ? { ...l, cantidad } : l)));
  }

  cambiarDescuento(linea: LineaPos, valor: string | number): void {
    const descuento = normalizarEntradaDecimal(String(valor)) || '0';
    this.lineas.set(this.lineas().map((l) => (l === linea ? { ...l, descuento } : l)));
  }

  quitarLinea(linea: LineaPos): void {
    this.lineas.set(this.lineas().filter((l) => l !== linea));
  }

  totalLinea(linea: LineaPos): string {
    return totalDeLinea(linea);
  }

  sinExistencias(linea: LineaPos): boolean {
    return linea.existencia !== null && entradaDecimalValida(linea.cantidad, 6) && decimal(linea.existencia).comparar(decimal(linea.cantidad)) < 0;
  }

  // ── Cliente ────────────────────────────────────────────────────────────

  async buscarCliente(termino: string): Promise<void> {
    if (termino.trim().length < 2) {
      this.opcionesCliente.set([]);
      return;
    }
    const clave = this.claveContexto();
    try {
      const clientes = await this.ventas.clientes(termino.trim());
      if (clave !== this.claveContexto()) return;
      this.clientesEncontrados = clientes.filter((c) => c.activo);
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

  abrirAltaCliente(): void {
    if (this.puedeRegistrarCliente()) this.altaClienteAbierta.set(true);
  }

  usarClienteRegistrado(cliente: ClienteApi): void {
    if (!this.puedeRegistrarCliente() || !cliente.activo || cliente.tipoDocumento !== 'RUC') return;
    this.cliente.set(cliente);
    this.altaClienteAbierta.set(false);
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
    const monto = valor === '' || valor === null ? null : normalizarEntradaDecimal(String(valor));
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
    const aplicado = this.aplicaciones().find((a) => a.pago === pago)?.aplicado ?? '0.00';
    const resto = decimal(this.porCobrar()).sumar(decimal(aplicado)).redondear(2).texto();
    this.cambiarMonto(pago, decimal(resto).comparar(decimal('0')) > 0 ? resto : '0.00');
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
        descuento: decimal(l.descuento).comparar(decimal('0')) === 0 ? null : l.descuento,
      })),
      // Se manda lo APLICADO como monto y, solo si hubo vuelto, lo entregado.
      // El comprobante y el arqueo cuadran con el monto; el billete del cliente
      // queda aparte.
      pagos: this.aplicaciones()
        .filter((a) => decimal(a.aplicado).comparar(decimal('0')) > 0)
        .map((a) => ({
          forma: a.pago.forma,
          monto: a.aplicado,
          referencia: a.pago.referencia || null,
          entregado: decimal(a.vuelto).comparar(decimal('0')) > 0 ? decimal(a.aplicado).sumar(decimal(a.vuelto)).redondear(2).texto() : null,
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
    this.altaClienteAbierta.set(false);
    this.lineas.set([]);
    this.pagos.set([{ forma: 'EFECTIVO', monto: null, referencia: '' }]);
    this.cliente.set(null);
    this.observaciones.set('');
    this.opcionesProducto.set([]);
    this.opcionesCliente.set([]);
  }

  importe(valor: string | number): string {
    return importeDecimal(String(valor));
  }

  cantidad(valor: string): string {
    return valor;
  }
}
