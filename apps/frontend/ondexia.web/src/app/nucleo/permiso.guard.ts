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
const MODULOS = new Set(['almacen', 'compras', 'ventas', 'configuracion']);

export const permisoGuard: CanActivateFn = async (_ruta, estado) => {
  const contexto = inject(ContextoService);
  const router = inject(Router);

  const primerSegmento = estado.url.split(/[?#]/)[0].split('/').filter(Boolean)[0];

  // El escritorio, el perfil y cualquier cosa que no sea un módulo pasan. Pedir
  // «perfil:acceder» inventaría un permiso que no existe y dejaría fuera a
  // todo el mundo.
  if (!primerSegmento || !MODULOS.has(primerSegmento)) {
    return true;
  }

  /*
   * Sin esto, la PRIMERA navegación se rechaza siempre.
   *
   * El contexto lo carga el marco de la aplicación, que se construye despues de
   * que las guardas decidan. Consultar los permisos aqui sin esperar devuelve
   * una lista vacia, y entrar por un enlace directo a /almacen/productos —o
   * recargar estando ahi— acabaria en el escritorio sin explicacion.
   */
  await contexto.asegurarCargado();

  if (contexto.puede(`${primerSegmento}:acceder`)) {
    return true;
  }

  // Al escritorio, que es la única pantalla que no exige módulo. Tambien es
  // donde cae quien tiene varias empresas y aun no ha elegido una: sin empresa
  // activa no hay permisos, y elegirla es justo lo que se hace ahi.
  return router.createUrlTree(['/']);
};
