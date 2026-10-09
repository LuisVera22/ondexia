import { datosAltaCliente } from './datos-alta-cliente';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AltaClienteVentaComponent } from './alta-cliente-venta.component';
import { ClienteApi, VentasApiService } from '../../../nucleo/ventas.api.service';
import { ConsultaDeRuc, ConsultasApiService } from '../../../nucleo/consultas.api.service';
import { ContextoService } from '../../../shared/services/contexto.service';

describe('Alta de cliente desde ventas · revisión, permisos y respuestas tardías', () => {
  const CONSULTA: ConsultaDeRuc = {
    datos: {
      ruc: '20512345671',
      razonSocial: 'DISTRIBUIDORA ANDINA S.A.C.',
      estado: 'ACTIVO',
      condicion: 'HABIDO',
      domicilioFiscal: 'AV. COLONIAL 2450',
      ubigeo: '150101',
      distrito: 'LIMA',
      provincia: 'LIMA',
      departamento: 'LIMA',
      esAgenteRetencion: false,
      esBuenContribuyente: false,
      tipoSocietario: null,
      consultadoEn: '2026-09-07T10:00:00Z',
      aptaParaRegistro: true,
      motivoDeRechazo: null,
    },
    atestacion: 'firma-de-prueba',
  };
  const CLIENTE: ClienteApi = { id: 'cliente-1', tipoDocumento: 'RUC', tipoDocumentoNombre: 'RUC',
    numeroDocumento: '20512345671', nombre: 'DISTRIBUIDORA ANDINA S.A.C.', direccion: 'AV. COLONIAL 2450',
    correo: null, telefono: null, verificadoEn: '2026-10-08T18:00:00Z', admiteFactura: true, activo: true };
  let ventas: jasmine.SpyObj<VentasApiService>;
  let consultas: jasmine.SpyObj<ConsultasApiService>;
  let empresa: ReturnType<typeof signal<{ id: string }>>;
  let permiso: ReturnType<typeof signal<boolean>>;

  beforeEach(() => {
    ventas = jasmine.createSpyObj<VentasApiService>('Ventas', ['clientes', 'crearCliente']);
    consultas = jasmine.createSpyObj<ConsultasApiService>('Consultas', ['consultarRuc']);
    ventas.clientes.and.resolveTo([]);
    ventas.crearCliente.and.resolveTo(CLIENTE);
    consultas.consultarRuc.and.resolveTo(CONSULTA);
    empresa = signal({ id: 'empresa-1' });
    permiso = signal(true);
    TestBed.configureTestingModule({ providers: [
      { provide: VentasApiService, useValue: ventas }, { provide: ConsultasApiService, useValue: consultas },
      { provide: ContextoService, useValue: { puede: () => permiso(), empresaActiva: empresa,
        establecimientoActivo: signal({ id: 'suc-1' }) } },
    ] });
  });

  function crear() {
    const vista = TestBed.createComponent(AltaClienteVentaComponent);
    vista.componentInstance.formulario.controls.ruc.setValue('20512345671');
    return vista;
  }

  it('consultar no crea; Registrar y usar envía la atestación y selecciona el cliente', async () => {
    const vista = crear(); const c = vista.componentInstance;
    const salida = jasmine.createSpy('seleccionado'); c.seleccionado.subscribe(salida);
    await c.consultar();
    expect(c.formulario.controls.nombre.value).toBe('DISTRIBUIDORA ANDINA S.A.C.');
    expect(ventas.crearCliente).not.toHaveBeenCalled();
    await c.registrar();
    expect(ventas.crearCliente).toHaveBeenCalledOnceWith({ tipoDocumento: 'RUC', numeroDocumento: '20512345671',
      nombre: 'DISTRIBUIDORA ANDINA S.A.C.', direccion: 'AV. COLONIAL 2450', correo: null, telefono: null, atestacion: 'firma-de-prueba' });
    expect(salida).toHaveBeenCalledOnceWith(CLIENTE);
  });

  it('cancelar destruye el alta sin registrar al cliente', async () => {
    const vista = crear(); const c = vista.componentInstance;
    await c.consultar(); vista.destroy(); await c.registrar();
    expect(ventas.crearCliente).not.toHaveBeenCalled();
  });

  it('una consulta tardía de A no rellena ni registra el RUC B', async () => {
    let resolver!: (valor: ConsultaDeRuc) => void;
    consultas.consultarRuc.and.returnValue(new Promise((resolucion) => { resolver = resolucion; }));
    const c = crear().componentInstance;
    const pendiente = c.consultar();
    await Promise.resolve(); await Promise.resolve();
    c.formulario.controls.ruc.setValue('20100000033');
    resolver(CONSULTA); await pendiente;
    expect(c.consulta()).toBeNull(); expect(c.revisando()).toBeFalse();
    expect(c.formulario.controls.nombre.value).toBe('');
    await c.registrar(); expect(ventas.crearCliente).not.toHaveBeenCalled();
  });

  it('no usa una respuesta que pertenece a otro RUC', async () => {
    consultas.consultarRuc.and.resolveTo({ ...CONSULTA, datos: { ...CONSULTA.datos, ruc: '20100000033' } });
    const c = crear().componentInstance; await c.consultar(); await c.registrar();
    expect(c.consulta()).toBeNull(); expect(c.fallo()).toContain('no corresponde');
    expect(ventas.crearCliente).not.toHaveBeenCalled();
  });

  it('sin permiso no consulta ni registra, incluso invocando las acciones directamente', async () => {
    permiso.set(false); const c = crear().componentInstance;
    await c.consultar(); await c.registrar();
    expect(ventas.clientes).not.toHaveBeenCalled(); expect(consultas.consultarRuc).not.toHaveBeenCalled();
    expect(ventas.crearCliente).not.toHaveBeenCalled();
  });

  it('revocar permiso tras consultar impide registrar', async () => {
    const c = crear().componentInstance; await c.consultar(); permiso.set(false); await c.registrar();
    expect(ventas.crearCliente).not.toHaveBeenCalled();
  });

  it('cliente existente activo se selecciona sin alta ni nueva consulta de padrón', async () => {
    ventas.clientes.and.resolveTo([CLIENTE]);
    const c = crear().componentInstance; const salida = jasmine.createSpy('seleccionado'); c.seleccionado.subscribe(salida);
    await c.consultar(); c.usarExistente();
    expect(salida).toHaveBeenCalledOnceWith(CLIENTE);
    expect(ventas.crearCliente).not.toHaveBeenCalled(); expect(consultas.consultarRuc).not.toHaveBeenCalled();
  });

  it('un cliente inactivo no se selecciona ni reactiva', async () => {
    ventas.clientes.and.resolveTo([{ ...CLIENTE, activo: false }]);
    const c = crear().componentInstance; const salida = jasmine.createSpy('seleccionado'); c.seleccionado.subscribe(salida);
    await c.consultar(); c.usarExistente();
    expect(salida).not.toHaveBeenCalled(); expect(c.fallo()).toContain('inactivo');
    expect(ventas.crearCliente).not.toHaveBeenCalled();
  });

  it('la búsqueda parcial no sustituye al RUC exacto', async () => {
    ventas.clientes.and.resolveTo([{ ...CLIENTE, numeroDocumento: '20100000033' }]);
    const c = crear().componentInstance; await c.consultar();
    expect(consultas.consultarRuc).toHaveBeenCalledOnceWith('20512345671'); expect(c.existente()).toBeNull();
  });

  it('permite revisar un alta manual sin inventar atestación ante fallo del padrón', async () => {
    consultas.consultarRuc.and.rejectWith(new Error('Proveedor')); const c = crear().componentInstance;
    await c.consultar(); expect(c.revisando()).toBeFalse(); c.ingresarManualmente();
    c.formulario.patchValue({ nombre: 'CLIENTE MANUAL', direccion: 'LIMA' }); await c.registrar();
    expect(ventas.crearCliente.calls.mostRecent().args[0].atestacion).toBeNull();
    expect(ventas.crearCliente.calls.mostRecent().args[0].nombre).toBe('CLIENTE MANUAL');
  });

  it('recupera el cliente propio ante duplicado concurrente sin repetir altas', async () => {
    const c = crear().componentInstance; await c.consultar();
    ventas.crearCliente.and.rejectWith(new HttpErrorResponse({ status: 409, error: { codigo: 'cliente_duplicado' } }));
    ventas.clientes.and.resolveTo([CLIENTE]);
    const salida = jasmine.createSpy('seleccionado'); c.seleccionado.subscribe(salida); await c.registrar();
    expect(salida).toHaveBeenCalledOnceWith(CLIENTE); expect(ventas.crearCliente).toHaveBeenCalledTimes(1);
  });

  it('doble pulsación registra una vez y una respuesta de la empresa anterior no se selecciona', async () => {
    let resolver!: (valor: ClienteApi) => void;
    ventas.crearCliente.and.returnValue(new Promise((resolucion) => { resolver = resolucion; }));
    const c = crear().componentInstance; await c.consultar();
    const salida = jasmine.createSpy('seleccionado'); c.seleccionado.subscribe(salida);
    const pendiente = c.registrar(); await c.registrar(); expect(ventas.crearCliente).toHaveBeenCalledTimes(1);
    empresa.set({ id: 'empresa-2' }); resolver(CLIENTE); await pendiente;
    expect(salida).not.toHaveBeenCalled();
  });
  it('el contrato compartido jamás adjunta la firma de otro RUC', () => {
    const datos = datosAltaCliente({ tipoDocumento: 'RUC', numeroDocumento: '20100000033', nombre: 'CLIENTE', direccion: null, correo: null, telefono: null }, CONSULTA);
    expect(datos.atestacion).toBeNull();
  });

});
