import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';

import { CajasComponent, lineasDeArqueo } from './cajas.component';
import { CajaApi, SesionCajaApi, VentasApiService } from '../../../nucleo/ventas.api.service';
import { CajaActivaService } from '../../../nucleo/caja-activa.service';
import { ContextoService } from '../../../shared/services/contexto.service';
import { AvisosService } from '../../../shared/services/avisos.service';

/**
 * Lo que la pantalla de cajas decide por su cuenta, sin renderizar la plantilla.
 *
 * <p>Dos cosas sostienen una promesa al usuario: que lo que no se declara se
 * manda como cero y no como ausente, y que la diferencia que se muestra es la
 * que dijo el servidor. Lo demás —abrir, cerrar, listar— es fontanería que se
 * comprueba de paso.
 */
describe('CajasComponent · abrir y cerrar con arqueo', () => {
  const MATRIZ = 'suc-0000';

  const SESION: SesionCajaApi = {
    id: 'ses-1',
    cajaId: 'caja-1',
    estado: 'ABIERTA',
    abiertaPor: 'usr-1',
    abiertaEn: new Date().toISOString(),
    montoInicial: 100,
    cerradaPor: null,
    cerradaEn: null,
    declarado: {},
    calculado: {},
    diferencia: {},
  };

  const CERRADA: CajaApi = {
    id: 'caja-1', sucursalId: MATRIZ, codigo: 'CAJA1', nombre: 'Caja 1', activa: true, sesionAbierta: null,
  };
  const ABIERTA: CajaApi = { ...CERRADA, sesionAbierta: SESION };
  const INACTIVA: CajaApi = {
    id: 'caja-2', sucursalId: MATRIZ, codigo: 'CAJA2', nombre: 'Caja 2', activa: false, sesionAbierta: null,
  };

  let api: jasmine.SpyObj<VentasApiService>;
  let cajaActiva: jasmine.SpyObj<CajaActivaService>;
  let avisos: jasmine.SpyObj<AvisosService>;
  let permisos: string[];

  function crear(): CajasComponent {
    return TestBed.runInInjectionContext(() => new CajasComponent());
  }

  /** Espera a que las promesas encadenadas del constructor se resuelvan. */
  async function asentar(): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
  }

  beforeEach(() => {
    api = jasmine.createSpyObj<VentasApiService>('VentasApiService', [
      'cajas', 'crearCaja', 'renombrarCaja', 'cambiarEstadoCaja', 'abrirCaja', 'cerrarSesion',
    ]);
    api.cajas.and.resolveTo([CERRADA, INACTIVA]);
    cajaActiva = jasmine.createSpyObj<CajaActivaService>('CajaActivaService', ['reemplazar']);
    avisos = jasmine.createSpyObj<AvisosService>('AvisosService', ['exito', 'error']);
    permisos = ['ventas.caja:consultar', 'ventas.caja:abrir', 'ventas.caja:cerrar'];

    TestBed.configureTestingModule({
      providers: [
        { provide: VentasApiService, useValue: api },
        { provide: CajaActivaService, useValue: cajaActiva },
        { provide: AvisosService, useValue: avisos },
        {
          provide: ContextoService,
          useValue: {
            puede: (permiso: string) => permisos.includes(permiso),
            establecimientos: signal([{ id: MATRIZ, nombre: 'Principal', detalle: '0000' }]),
            establecimientoActivo: signal({ id: MATRIZ, nombre: 'Principal', detalle: '0000' }),
          },
        },
      ],
    });
  });

  it('lista las cajas con su estado y comparte la lista con la barra superior', async () => {
    const componente = crear();
    await asentar();

    const estados = componente.registros().map((r) => [r['codigo'], r['estado']]);
    expect(estados).toEqual([['CAJA1', 'Cerrada'], ['CAJA2', 'Inactiva']]);
    expect(componente.registros()[0]['establecimiento']).toBe('Principal');
    expect(cajaActiva.reemplazar).toHaveBeenCalledWith([CERRADA, INACTIVA]);
  });

  it('solo ofrece abrir sobre una caja activa y cerrada, y cerrar sobre una abierta', () => {
    const componente = crear();
    const accion = (id: string) => componente.accionesDeFila.find((a) => a.id === id)!;

    const cerrada = { activa: true, abierta: false };
    const abierta = { activa: true, abierta: true };
    const inactiva = { activa: false, abierta: false };

    expect(accion('abrir').disponible!(cerrada)).toBeTrue();
    expect(accion('abrir').disponible!(abierta)).toBeFalse();
    expect(accion('abrir').disponible!(inactiva)).toBeFalse();
    expect(accion('cerrar').disponible!(abierta)).toBeTrue();
    expect(accion('cerrar').disponible!(cerrada)).toBeFalse();

    // Sin el permiso, la acción no se ofrece aunque la caja lo admita.
    permisos = ['ventas.caja:consultar'];
    expect(accion('abrir').disponible!(cerrada)).toBeFalse();
    expect(accion('cerrar').disponible!(abierta)).toBeFalse();
    expect(accion('desactivar').disponible!(cerrada)).toBeFalse();
  });

  it('abre la caja con el monto tecleado y vuelve a cargar', async () => {
    const componente = crear();
    await asentar();
    api.abrirCaja.and.resolveTo(SESION);
    api.cajas.and.resolveTo([ABIERTA, INACTIVA]);

    componente.pedirApertura(componente.registros()[0]);
    expect(componente.modalApertura()).toBeTrue();
    componente.formularioApertura.setValue({ montoInicial: 100 });
    await componente.abrir.ejecutar();

    expect(api.abrirCaja).toHaveBeenCalledWith('caja-1', 100);
    expect(componente.modalApertura()).toBeFalse();
    expect(componente.registros()[0]['estado']).toBe('Abierta');
    expect(avisos.exito).toHaveBeenCalled();
  });

  it('al cerrar manda solo lo declarado y muestra la diferencia que dijo el servidor', async () => {
    api.cajas.and.resolveTo([ABIERTA]);
    const componente = crear();
    await asentar();

    api.cerrarSesion.and.resolveTo({
      ...SESION,
      estado: 'CERRADA',
      declarado: { EFECTIVO: 95.5, TARJETA: 0, TRANSFERENCIA: 0, BILLETERA_DIGITAL: 0 },
      calculado: { EFECTIVO: 100, TARJETA: 0, TRANSFERENCIA: 0, BILLETERA_DIGITAL: 0 },
      diferencia: { EFECTIVO: -4.5, TARJETA: 0, TRANSFERENCIA: 0, BILLETERA_DIGITAL: 0 },
    });
    api.cajas.and.resolveTo([CERRADA]);

    componente.pedirCierre(componente.registros()[0]);
    componente.formularioCierre.patchValue({ EFECTIVO: 95.5 });

    // Lo vacío no viaja: el servidor lo toma como cero, y mandar `null` sería
    // pedirle que adivine.
    expect(componente.declaradoDelFormulario()).toEqual({ EFECTIVO: 95.5 });

    await componente.cerrar.ejecutar();

    expect(api.cerrarSesion).toHaveBeenCalledWith('ses-1', { EFECTIVO: 95.5 });
    const efectivo = componente.arqueo()!.find((l) => l.forma === 'Efectivo')!;
    expect(efectivo.calculado).toBe(100);
    expect(efectivo.declarado).toBe(95.5);
    expect(efectivo.diferencia).toBe(-4.5);
    expect(componente.arqueoCuadra()).toBeFalse();
    // El modal sigue abierto mostrando el arqueo; la tabla ya dice «Cerrada».
    expect(componente.modalCierre()).toBeTrue();
    expect(componente.registros()[0]['estado']).toBe('Cerrada');
  });

  it('las líneas del arqueo salen siempre en el mismo orden y completas', () => {
    const lineas = lineasDeArqueo({
      ...SESION,
      estado: 'CERRADA',
      declarado: { TARJETA: 20 },
      calculado: { EFECTIVO: 100, TARJETA: 20 },
      diferencia: { EFECTIVO: -100, TARJETA: 0, TRANSFERENCIA: 0, BILLETERA_DIGITAL: 0 },
    });

    expect(lineas.map((l) => l.forma)).toEqual(['Efectivo', 'Tarjeta', 'Transferencia', 'Billetera digital']);
    expect(lineas[0]).toEqual({ forma: 'Efectivo', declarado: 0, calculado: 100, diferencia: -100 });
    expect(lineas[1].diferencia).toBe(0);
  });
});
