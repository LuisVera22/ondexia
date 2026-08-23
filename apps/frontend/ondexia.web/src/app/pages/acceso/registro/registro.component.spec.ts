import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { RegistroComponent } from './registro.component';
import { CONFIGURACION } from '../../../nucleo/configuracion';
import { SesionService } from '../../../nucleo/sesion.service';
import { ResultadoVinculacion, VinculacionService } from '../../../nucleo/vinculacion.service';
import { ContextoService } from '../../../shared/services/contexto.service';

/**
 * La bifurcación que decide si alguien entra o se crea una cuenta duplicada.
 *
 * <p>Se prueba porque falla en silencio hacia el lado caro. Si la vinculación
 * deja de intentarse, nadie ve un error: al invitado se le pide un RUC, lo
 * rellena, y acaba con una segunda cuenta y una suscripción propia. Nadie
 * reporta eso como fallo — se reporta como «no me aparece la empresa».
 */
describe('RegistroComponent · invitado o empresa nueva', () => {
  let resultado: ResultadoVinculacion;
  let contextoCargado: number;
  let router: Router;

  function crear(): RegistroComponent {
    return TestBed.runInInjectionContext(() => new RegistroComponent());
  }

  beforeEach(() => {
    resultado = { vinculado: false, aviso: null };
    contextoCargado = 0;

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: CONFIGURACION,
          useValue: {
            api: 'http://api.pruebas',
            consultas: 'http://consultas.pruebas',
            cognito: {},
          },
        },
        { provide: SesionService, useValue: { usuario: () => ({ correo: 'a@b.c', nombre: 'A' }) } },
        {
          provide: ContextoService,
          useValue: {
            cargar: () => {
              contextoCargado += 1;
              return Promise.resolve();
            },
          },
        },
        // `resultado` se lee dentro de la función, no se copia: cada prueba lo
        // reasigna antes de construir el componente.
        { provide: VinculacionService, useValue: { intentar: () => Promise.resolve(resultado) } },
      ],
    });

    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
  });

  it('al invitado no se le pide RUC: entra directo', async () => {
    resultado = { vinculado: true, aviso: null };

    const componente = crear();
    await componente.ngOnInit();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/', { replaceUrl: true });
    // El contexto se carga ANTES de navegar; si no, la primera pantalla sale
    // con el menú vacío.
    expect(contextoCargado).toBe(1);
  });

  it('quien se registra por su cuenta ve el formulario, y sin avisos', async () => {
    // El caso normal. Que `aviso` sea null aquí importa: un cartel rojo justo
    // después de verificar el correo es el peor recibimiento posible.
    const componente = crear();
    await componente.ngOnInit();

    expect(componente.comprobando()).toBeFalse();
    expect(componente.error()).toBeNull();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('si había invitación y no se pudo aceptar, se dice', async () => {
    // Dos empresas invitaron al mismo correo, o la invitación se desactivó.
    // Callarlo dejaría a la persona creándose una cuenta duplicada convencida
    // de que es el camino.
    resultado = { vinculado: false, aviso: 'Hay más de una empresa que te ha invitado.' };

    const componente = crear();
    await componente.ngOnInit();

    expect(componente.comprobando()).toBeFalse();
    expect(componente.error()).toContain('más de una empresa');
  });

  it('el formulario no se pinta mientras se comprueba', () => {
    // Sin esperar al ngOnInit: es el estado con el que nace el componente.
    expect(crear().comprobando()).toBeTrue();
  });

  /**
   * Lo que la atestación compra, visto desde el formulario.
   *
   * <p>Se prueba porque el modo de fallo es callado: si el alta se pudiera
   * enviar sin RUC comprobado, o si viajaran los campos en vez de la firma, todo
   * «funcionaría» —se crearían cuentas— y el problema aparecería cuando SUNAT
   * rechazara los comprobantes de una empresa cuya razón social nadie verificó.
   */
  describe('la verificación del RUC', () => {
    function conFormularioListo(): RegistroComponent {
      const componente = crear();
      componente.formulario.patchValue({
        nombreTitular: 'Ana',
        apellidoTitular: 'Torres',
      });
      return componente;
    }

    const consulta = {
      atestacion: 'carga.firma',
      datos: {
        ruc: '20601030013',
        razonSocial: 'ONDEXIA S.A.C.',
        estado: 'ACTIVO',
        condicion: 'HABIDO',
        domicilioFiscal: 'AV. AREQUIPA 100',
        ubigeo: '150101',
        distrito: 'LIMA',
        provincia: 'LIMA',
        departamento: 'LIMA',
        esAgenteRetencion: false,
        esBuenContribuyente: false,
        tipoSocietario: 'S.A.C.',
        consultadoEn: '2026-08-23T10:00:00Z',
        aptaParaRegistro: true,
        motivoDeRechazo: null,
      },
    };

    it('sin RUC comprobado no se puede enviar, aunque el resto esté completo', () => {
      const componente = conFormularioListo();

      expect(componente.formulario.valid)
        .withContext('el nombre y el apellido sí están')
        .toBeTrue();
      expect(componente.puedeEnviar)
        .withContext('falta la verificación, que no es un campo del formulario')
        .toBeFalse();
    });

    it('con el RUC comprobado sí', () => {
      const componente = conFormularioListo();
      componente.alVerificar(consulta);

      expect(componente.puedeEnviar).toBeTrue();
    });

    /**
     * El componente de consulta emite {@code null} cuando el RUC existe pero no
     * es apto —de baja, no habido—. Aquí eso tiene que bloquear el envío igual
     * que si no se hubiera consultado nada.
     */
    it('un RUC consultado y no apto tampoco deja enviar', () => {
      const componente = conFormularioListo();
      componente.alVerificar(consulta);
      componente.alVerificar(null);

      expect(componente.puedeEnviar).toBeFalse();
    });

    /**
     * La prueba que fija el contrato: lo que se envía es la firma.
     *
     * <p>Si alguien añadiera `razonSocial` al cuerpo «para que el backend no
     * tenga que decodificar», volvería el agujero que la atestación cerró.
     */
    it('lo que viaja es la atestación, no los datos de la empresa', async () => {
      const componente = conFormularioListo();
      componente.alVerificar(consulta);

      const envio = componente.registrar();

      const http = TestBed.inject(HttpTestingController);
      const peticion = http.expectOne('http://api.pruebas/api/v1/registro');
      const cuerpo = peticion.request.body as Record<string, unknown>;

      expect(cuerpo['atestacion']).toBe('carga.firma');
      expect(Object.keys(cuerpo).sort())
        .withContext('nada de la empresa viaja aparte de la firma')
        .toEqual(['apellidoTitular', 'atestacion', 'correo', 'nombreTitular']);

      peticion.flush({ cuentaId: 'c-1' });
      await envio;
      http.verify();
    });
  });
});
