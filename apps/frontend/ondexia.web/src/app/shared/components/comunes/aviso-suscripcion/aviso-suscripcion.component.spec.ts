import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';

import { CuentaResumen } from '../../../../nucleo/contexto.api';
import { ContextoService } from '../../../services/contexto.service';
import { AvisoSuscripcionComponent } from './aviso-suscripcion.component';

/**
 * El anuncio del estado de la suscripción.
 *
 * <p>Lo que se prueba no es que se pinte un recuadro, sino <strong>qué dice y qué
 * no dice</strong>. El aviso se diseñó a partir de una página de phishing que
 * suplantaba a Netflix, y las reglas del doc 09 §5.1 salieron de ahí: sin
 * urgencia fabricada, sin ganchos y sin pedir datos de pago. Son afirmaciones
 * comprobables, así que se comprueban.
 */
describe('AvisoSuscripcionComponent · qué se le dice al cliente', () => {

  const cuenta = signal<CuentaResumen | null>(null);

  function conEstado(estadoSuscripcion: string | null, soloLectura = true): void {
    cuenta.set({
      id: 'cuenta-1',
      esAdministrador: true,
      estadoSuscripcion,
      soloLectura,
    });
  }

  let componente: AvisoSuscripcionComponent;

  beforeEach(() => {
    cuenta.set(null);

    TestBed.configureTestingModule({
      providers: [{ provide: ContextoService, useValue: { cuenta } }],
    });

    componente = TestBed.runInInjectionContext(() => new AvisoSuscripcionComponent());
  });

  it('con la cuenta activa no hay anuncio', () => {
    conEstado('ACTIVA', false);
    expect(componente.aviso()).withContext('nada que anunciar').toBeNull();
  });

  it('sin contexto todavía tampoco', () => {
    expect(componente.aviso()).toBeNull();
  });

  it('suspendida: dice primero qué sigue funcionando', () => {
    conEstado('SUSPENDIDA');

    const aviso = componente.aviso()!;
    expect(aviso.titulo).toContain('suspendida');
    expect(aviso.cuerpo)
      .withContext('la frase que ningún phishing escribe')
      .toContain('consultando y descargando');
    expect(aviso.cuerpo).toContain('luis26.ml143@gmail.com');
  });

  it('cancelada: recuerda que los datos son del cliente', () => {
    conEstado('CANCELADA');

    const aviso = componente.aviso()!;
    expect(aviso.cuerpo).toContain('exportarlos');
    expect(aviso.cuerpo)
      .withContext('el motivo legal, no una cortesía')
      .toContain('cinco años');
  });

  it('en prueba: informa, y avisa de que no se emite a SUNAT', () => {
    conEstado('EN_PRUEBA', false);

    const aviso = componente.aviso()!;
    expect(aviso.tono).withContext('no es una advertencia').toBe('informativo');
    expect(aviso.cuerpo).toContain('SUNAT');
  });

  it('ningún aviso usa urgencia, ganchos ni pide datos de pago', () => {
    // Es la regla que separa nuestro mensaje del fraude que lo inspiró. Si algún
    // día alguien añade «¡Últimos días!» o «extiende gratis», esto se pone rojo.
    const prohibidas = [
      'ahora mismo',
      'urgente',
      'gratis',
      'promoción',
      'tarjeta',
      'pulsa aquí',
      'haz clic',
      'expira en',
    ];

    for (const estado of ['SUSPENDIDA', 'CANCELADA', 'EN_PRUEBA']) {
      conEstado(estado);
      const texto = `${componente.aviso()!.titulo} ${componente.aviso()!.cuerpo}`.toLowerCase();

      for (const palabra of prohibidas) {
        expect(texto)
          .withContext(`«${palabra}» en el aviso de ${estado}`)
          .not.toContain(palabra);
      }
    }
  });
});
