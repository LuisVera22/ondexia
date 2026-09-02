import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { autenticacionInterceptor } from './autenticacion.interceptor';
import { CONFIGURACION } from './configuracion';
import { EmpresaActivaService } from './empresa-activa.service';
import { SesionService } from './sesion.service';

/**
 * A quién se le manda el token.
 *
 * <p>Hallazgo de la tabla de bajas de la auditoría 2026-09-01: el interceptor
 * decidía con `peticion.url.startsWith(configuracion.api)`. Con la API en
 * `https://api.ondexia.com`, eso hace que `https://api.ondexia.com.ejemplo.mx`
 * case — un dominio ajeno al que solo hay que registrarle un nombre que empiece
 * igual recibe el token de acceso de quien tenga la sesión abierta.
 *
 * <p>Se prueba porque es un fallo de una sola línea que no rompe nada al
 * introducirse: el caso normal sigue funcionando igual.
 */
describe('autenticacionInterceptor', () => {
  const API = 'https://api.ondexia.com';

  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([autenticacionInterceptor])),
        provideHttpClientTesting(),
        {
          provide: CONFIGURACION,
          useValue: {
            api: API,
            consultas: API,
            autoservicio: false,
            cognito: { dominio: 'https://cognito.ejemplo', clienteId: 'c' },
          },
        },
        {
          provide: SesionService,
          useValue: {
            caducado: () => false,
            tokenDeAcceso: () => 'token-de-acceso',
            renovar: () => Promise.resolve('token-de-acceso'),
            limpiar: () => {},
          },
        },
        { provide: EmpresaActivaService, useValue: { id: () => null } },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('añade el token a una llamada a nuestra API', async () => {
    const respuesta = firstValue(http.get(`${API}/api/v1/contexto`));

    const peticion = httpMock.expectOne(`${API}/api/v1/contexto`);
    expect(peticion.request.headers.has('Authorization')).toBeTrue();
    peticion.flush({});
    await respuesta;
  });

  /** El caso del hallazgo: mismo prefijo, otro dominio. */
  it('NO lo añade a un dominio que solo empieza igual', async () => {
    const impostor = 'https://api.ondexia.com.ejemplo.mx/api/v1/contexto';
    const respuesta = firstValue(http.get(impostor));

    const peticion = httpMock.expectOne(impostor);
    expect(peticion.request.headers.has('Authorization')).toBeFalse();
    peticion.flush({});
    await respuesta;
  });

  it('NO lo añade por http cuando la API es https', async () => {
    const enClaro = 'http://api.ondexia.com/api/v1/contexto';
    const respuesta = firstValue(http.get(enClaro));

    const peticion = httpMock.expectOne(enClaro);
    expect(peticion.request.headers.has('Authorization')).toBeFalse();
    peticion.flush({});
    await respuesta;
  });

  it('NO lo añade a config.json, que es del propio origen', async () => {
    const respuesta = firstValue(http.get('config.json'));

    const peticion = httpMock.expectOne('config.json');
    expect(peticion.request.headers.has('Authorization')).toBeFalse();
    peticion.flush({});
    await respuesta;
  });

  function firstValue(observable: { subscribe: (o: object) => void }): Promise<unknown> {
    return new Promise((resolve) =>
      observable.subscribe({ next: resolve, error: resolve, complete: () => resolve(null) })
    );
  }
});
