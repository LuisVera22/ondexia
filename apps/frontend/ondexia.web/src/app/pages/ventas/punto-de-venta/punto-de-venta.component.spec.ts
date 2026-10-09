import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { signal } from '@angular/core';

import { PuntoDeVentaComponent, totalDeLinea } from './punto-de-venta.component';
import { AlmacenApiService, ProductoDisponibleApi } from '../../../nucleo/almacen.api.service';
import { CajaApi, VentasApiService } from '../../../nucleo/ventas.api.service';
import { CajaActivaService } from '../../../nucleo/caja-activa.service';
import { ConfiguracionApiService, Empresa } from '../../../nucleo/configuracion.api.service';
import { ContextoService } from '../../../shared/services/contexto.service';
import { AvisosService } from '../../../shared/services/avisos.service';

/**
 * Lo que el punto de venta adelanta del servidor: el total exacto que paga el
 * cliente, lo que falta cobrar, y las reglas de §3.2 dichas antes del error.
 * Y lo que manda: producto y cantidad, nunca un precio tecleado.
 */
describe('PuntoDeVentaComponent · totales, cobro y reglas', () => {
  const CAJA: CajaApi = { id: 'caja-1', sucursalId: 'suc-1', codigo: 'CAJA1', nombre: 'Caja 1', activa: true, sesionAbierta: null };
  const CEMENTO: ProductoDisponibleApi = {
    id: 'p-cem', codigo: 'CEM-001', nombre: 'Cemento', unidad: 'BG', unidadNombre: 'Bolsa', afectacion: 'GRAVADO',
    llevaIgv: true, precio: 32.5, controlaStock: true, existencia: 10,
  };
  const SERVICIO: ProductoDisponibleApi = {
    id: 'p-srv', codigo: 'SRV', nombre: 'Instalación', unidad: 'ZZ', unidadNombre: 'Servicio', afectacion: 'GRAVADO',
    llevaIgv: true, precio: 80, controlaStock: false, existencia: null,
  };

  let empresaContexto: ReturnType<typeof signal<{ id: string }>>;
  let datosRuta: BehaviorSubject<Record<string, string>>;
  let ventas: jasmine.SpyObj<VentasApiService>;
  let almacen: jasmine.SpyObj<AlmacenApiService>;
  let caja: ReturnType<typeof signal<CajaApi | null>>;

  function crear(): PuntoDeVentaComponent {
    return TestBed.runInInjectionContext(() => new PuntoDeVentaComponent());
  }

  /**
   * Deja correr los efectos y las promesas del constructor. Los efectos de un
   * componente creado fuera de una plantilla los planifica Angular y solo
   * corren con un tick; sin él, las series nunca se cargarían en la prueba.
   */
  async function asentar(): Promise<void> {
    for (let i = 0; i < 3; i++) {
      TestBed.tick();
      for (let j = 0; j < 3; j++) {
        await Promise.resolve();
      }
    }
  }

  beforeEach(() => {
    empresaContexto = signal({ id: 'empresa-1' });
    datosRuta = new BehaviorSubject<Record<string, string>>({});
    ventas = jasmine.createSpyObj<VentasApiService>('VentasApiService', [
      'emitirNotaDeVenta', 'emitirComprobante', 'clientes', 'seriesDisponibles',
    ]);
    ventas.seriesDisponibles.and.resolveTo([{ id: 'ser-1', serie: 'N001', siguienteNumero: 'N001-00000007' }]);
    ventas.clientes.and.resolveTo([]);
    almacen = jasmine.createSpyObj<AlmacenApiService>('AlmacenApiService', ['disponibles']);
    almacen.disponibles.and.resolveTo([CEMENTO, SERVICIO]);
    caja = signal<CajaApi | null>(CAJA);
    const configuracion = jasmine.createSpyObj<ConfiguracionApiService>('ConfiguracionApiService', ['empresa']);
    configuracion.empresa.and.resolveTo({ emiteFacturas: true } as Empresa);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { data: datosRuta, snapshot: { data: {} } } },
        { provide: VentasApiService, useValue: ventas },
        { provide: AlmacenApiService, useValue: almacen },
        { provide: ConfiguracionApiService, useValue: configuracion },
        { provide: CajaActivaService, useValue: { enUso: caja } },
        { provide: AvisosService, useValue: jasmine.createSpyObj<AvisosService>('AvisosService', ['exito', 'error']) },
        { provide: ContextoService, useValue: { puede: () => true, empresaActiva: empresaContexto, establecimientoActivo: signal({ id: 'suc-1' }) } },
      ],
    });
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
  });

  it('el total de la línea es cantidad por precio menos descuento, en céntimos', () => {
    expect(totalDeLinea({ cantidad: 2, precio: 32.5, descuento: 0 })).toBe(65);
    expect(totalDeLinea({ cantidad: 3, precio: 0.1, descuento: 0 })).toBe(0.3);
    expect(totalDeLinea({ cantidad: 1, precio: 10, descuento: 12 })).toBe(0);
  });

  it('repetir un producto suma cantidad, el total incluye IGV y «Resto» completa el cobro', async () => {
    const componente = crear();
    await asentar();

    await componente.buscarProducto('cem');
    componente.agregarProducto({ id: 'p-cem', titulo: 'Cemento' });
    componente.agregarProducto({ id: 'p-cem', titulo: 'Cemento' });
    componente.agregarProducto({ id: 'p-srv', titulo: 'Instalación' });

    expect(componente.lineas().length).toBe(2);
    expect(componente.lineas()[0].cantidad).toBe(2);
    expect(componente.total()).toBe(145);
    // 65 → 55.08 + 9.92; 80 → 67.80 + 12.20.
    expect(componente.igvEstimado()).toBe(22.12);

    componente.cambiarMonto(componente.pagos()[0], 100);
    expect(componente.porCobrar()).toBe(45);
    expect(componente.impedimento()).toContain('Falta cobrar');

    componente.agregarPago();
    componente.completarPago(componente.pagos()[1]);
    expect(componente.cobrado()).toBe(145);
    expect(componente.impedimento()).toBeNull();
  });

  it('la petición manda producto y cantidad, nunca el precio', async () => {
    const componente = crear();
    await asentar();
    await componente.buscarProducto('cem');
    componente.agregarProducto({ id: 'p-cem', titulo: 'Cemento' });
    componente.cambiarDescuento(componente.lineas()[0], 2.5);
    componente.completarPago(componente.pagos()[0]);

    const peticion = componente.peticion();
    expect(peticion.cajaId).toBe('caja-1');
    expect(peticion.serieId).toBe('ser-1');
    expect(peticion.lineas).toEqual([{ productoId: 'p-cem', cantidad: 1, descuento: 2.5 }]);
    // `entregado` en null es «pagó justo». Va explícito y no ausente para que
    // la forma del cuerpo no dependa de si hubo vuelto.
    expect(peticion.pagos).toEqual([
      { forma: 'EFECTIVO', monto: 30, referencia: null, entregado: null },
    ]);
    expect(JSON.stringify(peticion)).not.toContain('precio');
  });

  it('un billete de más se manda como entregado, y el monto sigue siendo la venta', async () => {
    const componente = crear();
    await asentar();
    await componente.buscarProducto('cem');
    componente.agregarProducto({ id: 'p-cem', titulo: 'Cemento' });

    // La casilla del efectivo es lo que ENTREGA el cliente: 50 sobre una venta
    // de 32.50.
    componente.cambiarMonto(componente.pagos()[0], 50);

    expect(componente.cobrado()).toBe(32.5);
    expect(componente.vuelto()).toBe(17.5);
    // Pasarse en efectivo ya no impide registrar: es vuelto, no un cobro de más.
    expect(componente.impedimento()).toBeNull();

    expect(componente.peticion().pagos).toEqual([
      { forma: 'EFECTIVO', monto: 32.5, referencia: null, entregado: 50 },
    ]);
  });

  it('solo el efectivo da vuelto: pasarse con tarjeta sigue siendo un impedimento', async () => {
    const componente = crear();
    await asentar();
    await componente.buscarProducto('cem');
    componente.agregarProducto({ id: 'p-cem', titulo: 'Cemento' });

    componente.cambiarFormaDePago(componente.pagos()[0], 'TARJETA');
    componente.cambiarMonto(componente.pagos()[0], 50);

    expect(componente.vuelto()).toBe(0);
    expect(componente.impedimento()).toContain('superan el total');
  });

  it('dice antes lo que el servidor rechazaría: factura sin RUC y boleta grande sin cliente', async () => {
    const componente = crear();
    await asentar();
    await componente.buscarProducto('cem');
    for (let i = 0; i < 22; i++) {
      componente.agregarProducto({ id: 'p-cem', titulo: 'Cemento' });
    }
    componente.completarPago(componente.pagos()[0]);
    expect(componente.total()).toBe(715);

    componente.cambiarTipo('BOLETA');
    expect(componente.impedimento()).toContain('700');

    componente.cambiarTipo('FACTURA');
    expect(componente.impedimento()).toContain('RUC');

    componente.cambiarTipo('NV');
    expect(componente.impedimento()).toBeNull();
  });

  it('sin caja abierta no hay venta', async () => {
    caja.set(null);
    const componente = crear();
    await asentar();
    expect(componente.impedimento()).toContain('caja abierta');
  });
  for (const tipo of ['NV', 'BOLETA', 'FACTURA'] as const) {
    it(`respeta el tipo fijo ${tipo} del módulo incluso ante un cambio programático`, async () => {
      datosRuta.next({ tipoFijo: tipo });
      const componente = crear();
      await asentar();
      expect(componente.tipo()).toBe(tipo);
      componente.cambiarTipo(tipo === 'FACTURA' ? 'NV' : 'FACTURA');
      expect(componente.tipo()).toBe(tipo);
      expect(ventas.seriesDisponibles).toHaveBeenCalledWith(tipo === 'NV' ? 'NOTA_VENTA' : tipo, 'suc-1');
    });
  }

  it('no sustituye por nota una factura deshabilitada', async () => {
    datosRuta.next({ tipoFijo: 'FACTURA' });
    TestBed.inject(ConfiguracionApiService).empresa = async () => ({ emiteFacturas: false } as Empresa);
    const componente = crear();
    await asentar();
    expect(componente.tipo()).toBe('FACTURA');
    expect(componente.impedimento()).toContain('facturas');
  });

  it('ofrece Nuevo cliente en la venta y oculta el aviso de IGV para la nota', async () => {
    const vista = TestBed.createComponent(PuntoDeVentaComponent);
    vista.detectChanges();
    await asentar();
    vista.detectChanges();
    const botones = Array.from((vista.nativeElement as HTMLElement).querySelectorAll('button'));
    expect(botones.some((boton) => boton.textContent?.includes('Nuevo cliente'))).toBeTrue();
    expect(vista.nativeElement.textContent).not.toContain('Incluye IGV');
  });

  it('usar al cliente registrado conserva líneas, pagos, observaciones y tipo de venta', async () => {
    const c = crear(); await asentar(); await c.buscarProducto('cem');
    c.agregarProducto({ id: 'p-cem', titulo: 'Cemento' }); c.completarPago(c.pagos()[0]);
    c.observaciones.set('Entregar por la tarde'); c.cambiarTipo('BOLETA');
    const preparado = { lineas: c.lineas(), pagos: c.pagos(), observaciones: c.observaciones(), tipo: c.tipo() };
    c.abrirAltaCliente(); c.usarClienteRegistrado({ id: 'c-1', tipoDocumento: 'RUC', activo: true } as import('../../../nucleo/ventas.api.service').ClienteApi);
    expect(c.cliente()?.id).toBe('c-1'); expect(c.altaClienteAbierta()).toBeFalse();
    expect({ lineas: c.lineas(), pagos: c.pagos(), observaciones: c.observaciones(), tipo: c.tipo() }).toEqual(preparado);
  });

  it('al cambiar de empresa descarta la venta y el alta del contexto anterior', async () => {
    const c = crear(); await asentar(); await c.buscarProducto('cem'); c.agregarProducto({ id: 'p-cem', titulo: 'Cemento' });
    c.abrirAltaCliente(); c.observaciones.set('Empresa anterior');
    empresaContexto.set({ id: 'empresa-2' }); await asentar();
    expect(c.lineas()).toEqual([]); expect(c.altaClienteAbierta()).toBeFalse();
    expect(c.cliente()).toBeNull(); expect(c.observaciones()).toBe('');
  });

  it('una serie tardía de nota no reemplaza la serie de factura', async () => {
    let resolver!: (valor: import('../../../nucleo/ventas.api.service').SerieDisponibleApi[]) => void;
    ventas.seriesDisponibles.and.callFake((tipo) => tipo === 'NOTA_VENTA'
      ? new Promise((resolucion) => { resolver = resolucion; })
      : Promise.resolve([{ id: 'serie-factura', serie: 'F001', siguienteNumero: 'F001-00000001' }]));
    const c = crear(); await asentar(); c.cambiarTipo('FACTURA'); await asentar();
    resolver([{ id: 'serie-nota', serie: 'N001', siguienteNumero: 'N001-00000001' }]); await asentar();
    expect(c.serieId()).toBe('serie-factura');
  });

});
