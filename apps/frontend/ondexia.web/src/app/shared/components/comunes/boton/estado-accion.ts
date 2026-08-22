import { signal } from '@angular/core';
import { EstadoBoton } from './boton.component';

/** Cuánto se queda a la vista el resultado antes de volver a reposo. */
const MS_RESULTADO = 1600;

/**
 * La secuencia reposo → cargando → resultado → reposo, en un solo sitio.
 *
 * <h2>Por qué no vive dentro del botón</h2>
 *
 * <p>Porque el botón es de presentación: pinta el estado que recibe y nada más.
 * Si además llevara el temporizador, habría dos dueños del mismo dato y el
 * padre podría resucitar un estado ya caducado con solo volver a pintar.
 *
 * <h2>Por qué no lo escribe cada pantalla</h2>
 *
 * <p>Porque son cuatro líneas de {@code setTimeout} por botón, repetidas en
 * treinta pantallas, y con dos detalles fáciles de olvidar: cancelar el
 * temporizador anterior si se vuelve a pulsar antes de que expire —o el estado
 * salta a reposo en mitad de la segunda acción— y no dejar el botón cargando
 * para siempre cuando la promesa falla.
 *
 * <p>Ejemplo de uso:
 *
 * <pre>
 *   readonly guardar = accionConEstado(() => this.api.guardar(datos));
 *   // en la plantilla: [estado]="guardar.estado()" (accion)="guardar.ejecutar()"
 * </pre>
 */
export interface AccionConEstado<T> {
  readonly estado: () => EstadoBoton;
  /**
   * Devuelve lo que devuelva la tarea, o {@code undefined} si falló. El error
   * no se propaga: quien lo necesite lo trata dentro de la tarea, que es donde
   * se sabe qué mensaje mostrar.
   */
  ejecutar(): Promise<T | undefined>;
}

export function accionConEstado<T>(tarea: () => Promise<T>): AccionConEstado<T> {
  const estado = signal<EstadoBoton>('reposo');
  let temporizador: ReturnType<typeof setTimeout> | null = null;
  let enCurso = false;

  const aReposoTrasResultado = () => {
    if (temporizador) {
      clearTimeout(temporizador);
    }
    temporizador = setTimeout(() => estado.set('reposo'), MS_RESULTADO);
  };

  return {
    estado: estado.asReadonly(),

    async ejecutar(): Promise<T | undefined> {
      // Segundo cerrojo, además del `disabled` del botón. El botón se puede
      // disparar por teclado antes de que Angular refleje el estado, y en un
      // sistema que consume correlativos un doble envío es un documento
      // duplicado.
      if (enCurso) {
        return undefined;
      }
      enCurso = true;

      if (temporizador) {
        clearTimeout(temporizador);
        temporizador = null;
      }
      estado.set('cargando');

      try {
        const resultado = await tarea();
        estado.set('exito');
        aReposoTrasResultado();
        return resultado;
      } catch {
        // El estado de error es la única señal que da esta función. El mensaje
        // lo pone la tarea, con el aviso que corresponda: aquí no se sabe si
        // el fallo fue de red, de validación o de permisos.
        estado.set('error');
        aReposoTrasResultado();
        return undefined;
      } finally {
        enCurso = false;
      }
    },
  };
}
