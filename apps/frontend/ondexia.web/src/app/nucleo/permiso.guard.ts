import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { ContextoService } from '../shared/services/contexto.service';

/**
 * Impide entrar a un módulo que la cuenta no tiene.
 *
 * <p>Igual que {@link sesionGuard}, es comodidad y no seguridad: quien edite el
 * JavaScript de su navegador se la salta, y lo único que verá son pantallas
 * vacías, porque los datos los sirve la API, que recorta los permisos a lo
 * contratado en <strong>cada petición</strong>. Esta guarda existe para que un
 * usuario legítimo cuyo módulo se apagó desde el panel vea el escritorio en vez
 * de una pantalla que responde 403 a todo.
 *
 * <h2>El módulo sale de la URL, y no de una anotación por ruta</h2>
 *
 * <p>Las rutas son planas —{@code almacen/productos},
 * {@code compras/facturas}— y el primer segmento coincide con el código del
 * módulo, que es como se llaman los permisos: {@code almacen:acceder}. Así que
 * la guarda lo deduce leyendo la URL.
 *
 * <p>La alternativa era declarar {@code data: { permiso: '…' }} en cada ruta.
 * Son más de cincuenta, y el día que se añada la cincuenta y una nadie se
 * acordará: una ruta sin anotar no falla, simplemente queda sin proteger, que
 * es el peor modo de fallar que existe. Deduciéndolo de la URL, una ruta nueva
 * bajo {@code almacen/} nace protegida sin que haya que hacer nada.
 */
/**
 * Los primeros segmentos de URL que son un módulo.
 *
 * <p>Se exporta porque el menú lateral filtra con la misma regla. Si cada uno
 * tuviera su lista, el día que se añada un módulo se actualizaría una y no la
 * otra: o el menú ofrece una entrada que la guarda rechaza, o la guarda protege
 * algo que el menú ya no enseña. Las dos formas de desincronizarse son
 * confusas, y ninguna da error.
 */
export const MODULOS = new Set(['almacen', 'compras', 'ventas', 'configuracion']);

export const permisoGuard: CanActivateFn = async (_ruta, estado) => {
  const contexto = inject(ContextoService);
  const router = inject(Router);

  /*
   * Antes de mirar la ruta, y no solo para las de módulo.
   *
   * `ContextoService.cargar()` solo se invoca al terminar el registro y al
   * volver de Cognito, o sea: UNA vez, justo despues de iniciar sesion. Quien
   * recarga la pagina con la sesion ya guardada no pasa por ninguno de los dos
   * sitios, asi que el contexto se queda vacio para siempre.
   *
   * Mientras el menu fue una lista fija eso no se notaba. Al hacerlo depender
   * de los permisos, una simple recarga dejaba la barra lateral sin ningun
   * modulo: no es que estuvieran apagados, es que nadie habia preguntado.
   *
   * Se hace aqui porque esta guarda corre en CADA navegacion —lo garantiza
   * runGuardsAndResolvers: 'always'— y porque tiene que ocurrir antes de que se
   * construya cualquier pantalla. Cargar desde el marco de la aplicacion
   * llegaria tarde: las guardas deciden primero.
   */
  await contexto.asegurarCargado();

  const primerSegmento = estado.url.split(/[?#]/)[0].split('/').filter(Boolean)[0];

  // El escritorio, el perfil y cualquier cosa que no sea un módulo pasan. Pedir
  // «perfil:acceder» inventaría un permiso que no existe y dejaría fuera a
  // todo el mundo.
  if (!primerSegmento || !MODULOS.has(primerSegmento)) {
    return true;
  }

  if (contexto.puede(`${primerSegmento}:acceder`)) {
    return true;
  }

  // Al escritorio, que es la única pantalla que no exige módulo. Tambien es
  // donde cae quien tiene varias empresas y aun no ha elegido una: sin empresa
  // activa no hay permisos, y elegirla es justo lo que se hace ahi.
  return router.createUrlTree(['/']);
};
