import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ConfiguracionApiService } from './configuracion.api.service';
import { CONFIGURACION } from './configuracion';

describe('Subida de logos · creación sin reemplazo', () => {
  let peticiones: HttpTestingController;
  let api: ConfiguracionApiService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(),
      { provide: CONFIGURACION, useValue: { api: 'https://api.invalid' } }] });
    api = TestBed.inject(ConfiguracionApiService);
    peticiones = TestBed.inject(HttpTestingController);
  });
  afterEach(() => peticiones.verify());

  it('envía la condición firmada junto al archivo y al MIME', async () => {
    const archivo = new File(['imagen'], 'logo.png', { type: 'image/png' });
    const pendiente = api.subirArchivo('https://marca.invalid/subida-firmada', archivo);
    const peticion = peticiones.expectOne('https://marca.invalid/subida-firmada');
    peticion.flush({});
    await pendiente;
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toBe(archivo);
    expect(peticion.request.headers.get('Content-Type')).toBe('image/png');
    expect(peticion.request.headers.get('If-None-Match')).toBe('*');
  });

  it('propaga el rechazo por archivo existente sin repetir la subida', async () => {
    const archivo = new File(['imagen'], 'logo.png', { type: 'image/png' });
    const resultado = api.subirArchivo('https://marca.invalid/subida-firmada', archivo)
      .then(() => fail('La subida rechazada no debe dar éxito'), (fallo: HttpErrorResponse) => fallo.status);
    peticiones.expectOne('https://marca.invalid/subida-firmada')
      .flush(null, { status: 412, statusText: 'Precondition Failed' });
    expect(await resultado).toBe(412);
    peticiones.expectNone('https://marca.invalid/subida-firmada');
  });
});
