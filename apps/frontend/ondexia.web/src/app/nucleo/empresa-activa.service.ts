import { Injectable, signal } from '@angular/core';

const CLAVE = 'ondexia.empresa';

/**
 * La empresa sobre la que se está trabajando, y nada más.
 *
 * Existe separado de ContextoService por una razón concreta: el interceptor
 * necesita este identificador para poner la cabecera `X-Empresa-Id`, y
 * ContextoService necesita al interceptor para poder llamar a la API. Si el
 * interceptor inyectara ContextoService directamente, el grafo de dependencias
 * se cerraría sobre sí mismo.
 *
 * Se persiste porque al recargar la página el usuario espera seguir donde
 * estaba. Sin esto, cada F5 en una cuenta con varias empresas devuelve a
 * elegir — y peor: la primera petición saldría sin cabecera, con lo que la API
 * resolvería una empresa distinta de la que muestra la pantalla.
 */
@Injectable({ providedIn: 'root' })
export class EmpresaActivaService {
  private readonly _id = signal<string | null>(sessionStorage.getItem(CLAVE));

  readonly id = this._id.asReadonly();

  fijar(id: string | null): void {
    this._id.set(id);
    if (id) {
      sessionStorage.setItem(CLAVE, id);
    } else {
      sessionStorage.removeItem(CLAVE);
    }
  }
}
