import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';

import { FichaClienteComponent } from './ficha-cliente.component';
import { ClienteApi, VentasApiService } from '../../../nucleo/ventas.api.service';
import { ConsultaDeRuc, ConsultasApiService, ErrorDeConsulta } from '../../../nucleo/consultas.api.service';
import { AvisosService } from '../../../shared/services/avisos.service';
import { ContextoService } from '../../../shared/services/contexto.service';
import { ConfirmacionesService } from '../../../shared/services/confirmaciones.service';

/**
 * La ficha de cliente decide dos cosas que el servidor no puede ver: qué
 * validador rige el número según el tipo, y cuándo la atestación viaja. Una
 * atestación de otro RUC la rechaza el servidor, pero mandarla sería un 400
 * confuso; no mandarla cuando toca dejaría al cliente «sin verificar» sin que
 * nadie lo note.
 */
describe('FichaClienteComponent · documento y consulta', () => {
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

  let api: jasmine.SpyObj<VentasApiService>;
  let consultas: jasmine.SpyObj<ConsultasApiService>;
  let router: Router;

  function crear(): FichaClienteComponent {
    return TestBed.runInInjectionContext(() => new FichaClienteComponent());
  }

  async function asentar(): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
  }

  beforeEach(() => {
    api = jasmine.createSpyObj<VentasApiService>('VentasApiService', [
      'cliente', 'crearCliente', 'actualizarCliente', 'verificarCliente', 'cambiarEstadoCliente',
    ]);
    consultas = jasmine.createSpyObj<ConsultasApiService>('ConsultasApiService', ['consultarRuc', 'consultarDni']);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: VentasApiService, useValue: api },
        { provide: ConsultasApiService, useValue: consultas },
        { provide: AvisosService, useValue: jasmine.createSpyObj<AvisosService>('AvisosService', ['exito', 'error']) },
        { provide: ConfirmacionesService, useValue: { pedir: () => Promise.resolve(true) } },
        { provide: ContextoService, useValue: { puede: () => true } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['id', 'nuevo']]) } } },
      ],
    });
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
  });

  it('el validador del número sigue al tipo de documento', () => {
    const componente = crear();

    componente.formulario.patchValue({ tipoDocumento: 'RUC', numeroDocumento: '45678912' });
    expect(componente.controles.numeroDocumento.valid).toBeFalse();

    componente.formulario.patchValue({ tipoDocumento: 'DNI' });
    expect(componente.controles.numeroDocumento.valid).toBeTrue();

    componente.formulario.patchValue({ tipoDocumento: 'PASAPORTE', numeroDocumento: 'AB123456' });
    expect(componente.controles.numeroDocumento.valid).toBeTrue();
  });

  it('con RUC, la consulta rellena razón social y domicilio y el alta manda la atestación', async () => {
    consultas.consultarRuc.and.resolveTo(CONSULTA);
    api.crearCliente.and.resolveTo({ id: 'cli-1', numeroDocumento: '20512345671', nombre: 'X' } as ClienteApi);
    const componente = crear();
    await asentar();

    componente.formulario.patchValue({ tipoDocumento: 'RUC', numeroDocumento: '20512345671' });
    await componente.consultar.ejecutar();

    expect(componente.controles.nombre.value).toBe('DISTRIBUIDORA ANDINA S.A.C.');
    expect(componente.controles.direccion.value).toBe('AV. COLONIAL 2450');

    await componente.guardar.ejecutar();
    expect(api.crearCliente).toHaveBeenCalledWith(jasmine.objectContaining({
      tipoDocumento: 'RUC',
      numeroDocumento: '20512345671',
      atestacion: 'firma-de-prueba',
    }));
  });

  it('cambiar el número después de consultar descarta la atestación: era de otro RUC', async () => {
    consultas.consultarRuc.and.resolveTo(CONSULTA);
    api.crearCliente.and.resolveTo({ id: 'cli-2', numeroDocumento: '20601030013', nombre: 'Y' } as ClienteApi);
    const componente = crear();
    await asentar();

    componente.formulario.patchValue({ tipoDocumento: 'RUC', numeroDocumento: '20512345671' });
    await componente.consultar.ejecutar();
    componente.formulario.patchValue({ numeroDocumento: '20601030013', nombre: 'Otra empresa' });

    expect(componente.consultaRuc()).toBeNull();
    await componente.guardar.ejecutar();
    expect(api.crearCliente).toHaveBeenCalledWith(jasmine.objectContaining({ atestacion: null }));
  });

  it('con DNI, la consulta solo pone el nombre y no viaja ninguna atestación', async () => {
    consultas.consultarDni.and.resolveTo({
      dni: '45678912', nombres: 'ROSA', apellidoPaterno: 'QUISPE', apellidoMaterno: 'MAMANI',
      nombreCompleto: 'QUISPE MAMANI ROSA', consultadoEn: '2026-09-07T10:00:00Z',
    });
    api.crearCliente.and.resolveTo({ id: 'cli-3', numeroDocumento: '45678912', nombre: 'Z' } as ClienteApi);
    const componente = crear();
    await asentar();

    componente.formulario.patchValue({ tipoDocumento: 'DNI', numeroDocumento: '45678912' });
    await componente.consultar.ejecutar();
    expect(componente.controles.nombre.value).toBe('QUISPE MAMANI ROSA');

    await componente.guardar.ejecutar();
    expect(api.crearCliente).toHaveBeenCalledWith(jasmine.objectContaining({ tipoDocumento: 'DNI', atestacion: null }));
    expect(consultas.consultarRuc).not.toHaveBeenCalled();
  });

  it('si la consulta falla, avisa y deja teclear el nombre', async () => {
    consultas.consultarDni.and.rejectWith(new ErrorDeConsulta({
      mensaje: 'No se pudo contactar con el servicio de consulta de DNI.', reintentable: true, noEncontrado: false,
    }));
    const componente = crear();
    await asentar();

    componente.formulario.patchValue({ tipoDocumento: 'DNI', numeroDocumento: '45678912' });
    await componente.consultar.ejecutar().catch(() => undefined);

    expect(componente.avisoConsulta()).toContain('escribir el nombre a mano');
    expect(componente.controles.nombre.enabled).toBeTrue();
  });
});
