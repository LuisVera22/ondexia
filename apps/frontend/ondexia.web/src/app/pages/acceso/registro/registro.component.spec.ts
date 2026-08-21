import { provideHttpClient } from '@angular/common/http';
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
        { provide: CONFIGURACION, useValue: { api: 'http://api.pruebas', cognito: {} } },
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
});
