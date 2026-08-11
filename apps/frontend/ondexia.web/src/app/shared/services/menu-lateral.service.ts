import { Injectable, computed, signal } from '@angular/core';

/** Ancho del menú desplegado y colapsado, en píxeles. */
export const ANCHO_MENU = 300;
export const ANCHO_MENU_COLAPSADO = 90;

const CLAVE_PREFERENCIA = 'ondexia.menu-desplegado';

/**
 * Estado del menú lateral.
 *
 * Hay tres cosas distintas y conviene no confundirlas:
 *
 * - `desplegado` es la preferencia del usuario en escritorio, y **persiste**:
 *   quien trabaja todo el día con el menú colapsado no debería volver a
 *   colapsarlo en cada recarga.
 * - `sobrevolado` es transitorio. Un menú colapsado se despliega al pasar el
 *   ratón para poder leer las etiquetas, y vuelve a colapsarse al salir.
 * - `abiertoEnMovil` es el cajón que se superpone a la página por debajo de
 *   `xl`, donde no hay espacio para menú y contenido a la vez.
 */
@Injectable({ providedIn: 'root' })
export class MenuLateralService {
  readonly desplegado = signal(this.leerPreferencia());
  readonly sobrevolado = signal(false);
  readonly abiertoEnMovil = signal(false);

  /** El menú ocupa su ancho completo si está desplegado o si se sobrevuela. */
  readonly anchoCompleto = computed(() => this.desplegado() || this.sobrevolado());

  alternarDesplegado(): void {
    this.desplegado.update((valor) => !valor);
    this.guardarPreferencia(this.desplegado());
  }

  alternarEnMovil(): void {
    this.abiertoEnMovil.update((valor) => !valor);
  }

  cerrarEnMovil(): void {
    this.abiertoEnMovil.set(false);
  }

  sobrevolar(valor: boolean): void {
    // Sobrevolar un menú ya desplegado no significa nada: evita un cambio de
    // estado —y su repintado— en cada paso del ratón por el menú.
    if (!this.desplegado()) {
      this.sobrevolado.set(valor);
    }
  }

  private leerPreferencia(): boolean {
    try {
      const guardado = localStorage.getItem(CLAVE_PREFERENCIA);
      return guardado === null ? true : guardado === 'true';
    } catch {
      // Modo privado o almacenamiento bloqueado: se arranca desplegado.
      return true;
    }
  }

  private guardarPreferencia(valor: boolean): void {
    try {
      localStorage.setItem(CLAVE_PREFERENCIA, String(valor));
    } catch {
      // Perder la preferencia es aceptable; romper la navegación no.
    }
  }
}
