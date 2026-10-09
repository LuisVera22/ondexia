import { Injectable, signal } from '@angular/core';

/** Lo que hay que contarle al usuario antes de que decida. */
export interface PeticionDeConfirmacion {
  readonly titulo: string;
  /** Qué va a pasar exactamente. Específico, no «¿estás seguro?». */
  readonly mensaje: string;
  /** El botón nombra la acción: «Descartar», «Anular orden». No «Aceptar». */
  readonly textoConfirmar?: string;
  readonly textoCancelar?: string;
  readonly peligrosa?: boolean;
}

interface EnCurso extends PeticionDeConfirmacion {
  readonly resolver: (respuesta: boolean) => void;
}

/**
 * Confirmaciones que no puede montar la pantalla que las necesita.
 *
 * <h2>Para qué hace falta, habiendo ya un componente</h2>
 *
 * <p>{@code ConfirmacionComponent} sirve cuando quien pregunta es una pantalla:
 * tiene plantilla donde ponerlo y una señal donde guardar si está abierto. Pero
 * hay sitios que necesitan preguntar y no tienen plantilla — una guarda de
 * ruta, por ejemplo, que decide si se puede abandonar una ficha con cambios sin
 * guardar. Ahí la alternativa era {@code window.confirm}, que ignora el tema, el
 * idioma de los botones y la tipografía, y aparece pegado al borde del
 * navegador como si lo hubiera lanzado otro programa.
 *
 * <p>Así que la pregunta se pide aquí y la pinta un único componente montado en
 * el marco, con el mismo diálogo que todo lo demás.
 *
 * <h2>Una a la vez</h2>
 *
 * <p>Igual que los avisos. Dos confirmaciones simultáneas son dos preguntas
 * apiladas donde no se sabe cuál se está respondiendo; si llega una segunda, la
 * primera se resuelve como cancelada — que es la respuesta segura, porque
 * cancelar no hace nada.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmacionesService {
  private readonly _peticion = signal<EnCurso | null>(null);
  readonly peticion = this._peticion.asReadonly();

  readonly procesando = signal(false);

  /**
   * Pregunta y espera.
   *
   * @returns {@code true} si confirmó; {@code false} si canceló, cerró con
   *     {@code Escape} o clicó fuera
   */
  pedir(peticion: PeticionDeConfirmacion): Promise<boolean> {
    const anterior = this._peticion();
    if (anterior) {
      anterior.resolver(false);
    }

    return new Promise<boolean>((resolver) => {
      this._peticion.set({ ...peticion, resolver });
    });
  }

  responder(respuesta: boolean): void {
    const actual = this._peticion();
    if (!actual) {
      return;
    }
    this._peticion.set(null);
    this.procesando.set(false);
    actual.resolver(respuesta);
  }
}
