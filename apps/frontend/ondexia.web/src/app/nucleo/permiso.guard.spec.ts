import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { provideRouter } from '@angular/router';

import { permisoGuard } from './permiso.guard';
import { ContextoService } from '../shared/services/contexto.service';

/**
 * La guarda que decide si se entra a un módulo.
 *
 * Se prueba porque su modo de fallar es silencioso: si deja de ejecutarse, o si
 * deduce mal el módulo, nada revienta — simplemente se deja de proteger, y eso
 * no se ve mirando la pantalla.
 */
describe('permisoGuard', () => {
  let permisos: string[];
  let cargas: number;

  function correr(url: string): Promise<boolean | UrlTree> {
    return TestBed.runInInjectionContext(
      () =>
        permisoGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot) as Promise<
          boolean | UrlTree
        >
    );
  }

  beforeEach(() => {
    permisos = ['almacen:acceder', 'ventas:acceder'];
    cargas = 0;

    // `permisos` se lee dentro de la funcion, no se copia: cada prueba puede
    // reasignarlo antes de correr la guarda sin rehacer el modulo de prueba
    // —configurarlo dos veces revienta una vez instanciado—.
    const contexto = {
      puede: (permiso: string) => permisos.includes(permiso),
      asegurarCargado: () => {
        cargas += 1;
        return Promise.resolve();
      },
    };

    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: ContextoService, useValue: contexto }],
    });
  });

  it('deja entrar al módulo contratado', async () => {
    await expectAsync(correr('/almacen/productos')).toBeResolvedTo(true);
  });

  it('no deja entrar al módulo apagado desde el panel', async () => {
    // El caso que motivó todo esto: el operador apaga «compras» en el panel y
    // el cliente sigue llegando por el menú o escribiendo la URL.
    const resultado = await correr('/compras/facturas');
    expect(resultado).toBeInstanceOf(UrlTree);
  });

  it('la ruta profunda de un módulo apagado tampoco', async () => {
    // Lo que hace innecesario anotar ruta por ruta: el módulo sale del primer
    // segmento, así que las rutas nuevas nacen protegidas.
    const resultado = await correr('/compras/ordenes-compra/nueva');
    expect(resultado).toBeInstanceOf(UrlTree);
  });

  it('lo que no es un módulo pasa', async () => {
    // «perfil» no tiene permiso propio. Exigir «perfil:acceder» inventaria uno
    // que no existe y dejaria fuera a todo el mundo.
    await expectAsync(correr('/perfil')).toBeResolvedTo(true);
    await expectAsync(correr('/')).toBeResolvedTo(true);
  });

  it('los parametros de la URL no confunden al modulo', async () => {
    await expectAsync(correr('/almacen/productos?pagina=2')).toBeResolvedTo(true);
  });

  it('carga el contexto tambien fuera de los modulos', async () => {
    /*
     * ContextoService.cargar() solo se llama al registrarse y al volver de
     * Cognito. Quien recarga con la sesion guardada no pasa por ninguno de los
     * dos, y sin esta llamada se quedaba con los permisos vacios: el menu
     * aparecia sin ningun modulo, como si estuvieran todos apagados.
     *
     * Se comprueba sobre «/» a proposito, que es la ruta que sale antes por el
     * atajo de «esto no es un modulo».
     */
    await correr('/');

    expect(cargas).toBe(1);
  });
});
