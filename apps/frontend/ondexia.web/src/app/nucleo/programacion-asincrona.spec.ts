import { NgZone } from '@angular/core';
import { fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';

describe('Programación asíncrona de la interfaz', () => {
  it('resuelve promesas y respeta el plazo del temporizador', fakeAsync(() => {
    const resultados: string[] = [];
    Promise.resolve().then(() => resultados.push('promesa'));
    setTimeout(() => resultados.push('temporizador'), 5);
    expect(resultados).toEqual([]);
    flushMicrotasks();
    expect(resultados).toEqual(['promesa']);
    tick(4);
    expect(resultados).toEqual(['promesa']);
    tick(1);
    expect(resultados).toEqual(['promesa', 'temporizador']);
  }));

  it('no ejecuta un temporizador cancelado', fakeAsync(() => {
    let ejecuciones = 0;
    const temporizador = setTimeout(() => ejecuciones++, 5);
    clearTimeout(temporizador);
    tick(10);
    expect(ejecuciones).toBe(0);
  }));

  it('mantiene el contexto de Angular en un evento del botón', () => {
    const zona = new NgZone({ enableLongStackTrace: false });
    const boton = document.createElement('button');
    let dentroDeAngular = false;
    const escuchar = () => { dentroDeAngular = NgZone.isInAngularZone(); };
    zona.run(() => boton.addEventListener('click', escuchar));
    try {
      boton.click();
      expect(dentroDeAngular).toBeTrue();
    } finally {
      boton.removeEventListener('click', escuchar);
    }
  });
});
