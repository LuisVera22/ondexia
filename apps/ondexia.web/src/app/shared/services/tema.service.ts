import { Injectable, effect, signal } from '@angular/core';

export type Tema = 'claro' | 'oscuro';

const CLAVE_PREFERENCIA = 'ondexia.tema';

/**
 * Tema claro u oscuro.
 *
 * La preferencia explícita del usuario manda y persiste. Si no ha elegido
 * nunca, se respeta la del sistema operativo: quien tiene el equipo en oscuro
 * espera que una aplicación de gestión no le deslumbre al abrirla.
 *
 * El único efecto real es la clase `oscuro` en `<html>`, que es lo que activa
 * el variante `dark:` de la hoja de estilos.
 */
@Injectable({ providedIn: 'root' })
export class TemaService {
  readonly tema = signal<Tema>(this.temaInicial());

  constructor() {
    effect(() => this.aplicar(this.tema()));
    this.seguirAlSistema();
  }

  alternar(): void {
    this.establecer(this.tema() === 'claro' ? 'oscuro' : 'claro');
  }

  establecer(tema: Tema): void {
    this.tema.set(tema);
    try {
      localStorage.setItem(CLAVE_PREFERENCIA, tema);
    } catch {
      // Sin almacenamiento el tema vale para esta sesión y no más.
    }
  }

  private temaInicial(): Tema {
    try {
      const guardado = localStorage.getItem(CLAVE_PREFERENCIA);
      if (guardado === 'claro' || guardado === 'oscuro') {
        return guardado;
      }
    } catch {
      // Se cae al valor del sistema.
    }
    return this.prefiereOscuroElSistema() ? 'oscuro' : 'claro';
  }

  private prefiereOscuroElSistema(): boolean {
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
  }

  /**
   * Si el usuario no ha elegido nada, seguir al sistema también **en vivo**:
   * quien tiene el cambio automático al anochecer lo vería a medias si solo
   * lo leyéramos al arrancar.
   */
  private seguirAlSistema(): void {
    window.matchMedia?.('(prefers-color-scheme: dark)').addEventListener('change', (evento) => {
      let hayPreferencia = false;
      try {
        hayPreferencia = localStorage.getItem(CLAVE_PREFERENCIA) !== null;
      } catch {
        hayPreferencia = false;
      }
      if (!hayPreferencia) {
        this.tema.set(evento.matches ? 'oscuro' : 'claro');
      }
    });
  }

  private aplicar(tema: Tema): void {
    // La clase se llama `dark` porque es la que espera el variante de Tailwind;
    // el resto del código habla en español.
    document.documentElement.classList.toggle('dark', tema === 'oscuro');
    document.documentElement.style.colorScheme = tema === 'oscuro' ? 'dark' : 'light';
  }
}
