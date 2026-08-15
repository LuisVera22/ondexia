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
    const activa = componente.colorEstado({ estadoSuscripcion: 'ACTIVA' } as never);
    const suspendida = componente.colorEstado({ estadoSuscripcion: 'SUSPENDIDA' } as never);

    expect(activa).not.toBe(suspendida);
    expect(suspendida).toContain('amber');
  });
});
