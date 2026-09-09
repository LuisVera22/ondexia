import { TestBed } from '@angular/core/testing';

import { CuentasComponent } from './cuentas.component';
import { PanelApiService } from '../../nucleo/panel.api';

/**
 * Lo que se prueba aquí es la lectura de los límites, que es donde un error se
 * traduce en una decisión comercial equivocada.
 */
describe('CuentasComponent · consumo frente a límites', () => {
  let componente: CuentasComponent;

  beforeEach(() => {
    const api = jasmine.createSpyObj<PanelApiService>('PanelApiService', ['cuentas']);
    api.cuentas.and.resolveTo([]);

    TestBed.configureTestingModule({
      providers: [{ provide: PanelApiService, useValue: api }],
    });

    componente = TestBed.runInInjectionContext(() => new CuentasComponent());
  });

  it('con límite se lee «usados de límite»', () => {
    expect(componente.consumo(3, 5)).toBe('3 de 5');
  });

  it('sin límite NO dice «de 0»', () => {
    // El plan negociable no tiene tope. Pintar «3 de 0» llevaria a subir de plan
    // a quien no lo necesita, que es el error mas caro de esta pantalla.
    expect(componente.consumo(3, null)).toBe('3 · sin límite');
    expect(componente.consumo(3, null)).not.toContain('de 0');
  });

  it('el color del estado distingue lo que exige atención', () => {
    const activa = componente.insignia({ estadoSuscripcion: 'ACTIVA' } as never);
    const suspendida = componente.insignia({ estadoSuscripcion: 'SUSPENDIDA' } as never);

    expect(activa.fondo).not.toBe(suspendida.fondo);

    // Contra la escala semántica del tema, no contra un color literal. La
    // version anterior comprobaba «amber», que es un nombre de Tailwind de
    // fabrica: el dia que se adopto el sistema de diseno la clase dejo de
    // existir y la prueba habria seguido en verde comprobando una cadena.
    expect(activa.fondo).toContain('success');
    expect(suspendida.fondo).toContain('error');
  });

  it('el estado se lee en palabras, no en constante de base de datos', () => {
    expect(componente.insignia({ estadoSuscripcion: 'EN_PRUEBA' } as never).texto)
      .toBe('en prueba');
  });

  it('el aviso de tope salta al llegar, no al acercarse', () => {
    // Tenir antes de tiempo convierte el color en ruido y deja de leerse
    // cuando de verdad importa. Sin limite no hay tope al que llegar.
    expect(componente.alLimite(4, 5)).not.toContain('error');
    expect(componente.alLimite(5, 5)).toContain('error');
    expect(componente.alLimite(900, null)).not.toContain('error');
  });
});
