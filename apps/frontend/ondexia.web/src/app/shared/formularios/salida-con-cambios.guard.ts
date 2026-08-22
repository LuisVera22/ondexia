import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { ConfirmacionesService } from '../services/confirmaciones.service';

/** Lo que la guarda necesita de la pantalla que protege. */
export interface ConCambiosSinGuardar {
  hayCambiosSinGuardar(): boolean;
}

/**
 * Avisa antes de abandonar una ficha con cambios sin guardar.
 *
 * <h2>Por qué se pregunta al salir y no se guarda solo</h2>
 *
 * <p>Porque no todo cambio a medias es un cambio que se quiera. Se entra a
 * mirar, se teclea sobre un campo para leerlo mejor, se cambia de idea. Guardar
 * por iniciativa propia convertiría eso en un dato nuevo en la empresa, y en
 * configuración un dato equivocado se propaga: el nombre de un establecimiento
 * sale impreso en los comprobantes que emite.
 *
 * <p>La pregunta usa nuestro diálogo y no {@code window.confirm} porque este
 * ignora el tema, la tipografía y el idioma de los botones, y aparece pegado al
 * borde del navegador como si lo hubiera lanzado otro programa. Ver
 * {@link ConfirmacionesService}, que existe para esto.
 *
 * <h2>Lo que no cubre</h2>
 *
 * <p>Cerrar la pestaña o recargar. Eso lo decide el navegador con
 * {@code beforeunload}, que solo permite un texto suyo y no admite el nuestro.
 * No se pone: la mayoría de las salidas son navegaciones internas, y añadir un
 * diálogo del navegador que no dice qué se pierde es peor que no ponerlo.
 */
export const salidaConCambios: CanDeactivateFn<ConCambiosSinGuardar> = (componente) => {
  if (!componente.hayCambiosSinGuardar()) {
    return true;
  }

  return inject(ConfirmacionesService).pedir({
    titulo: '¿Salir sin guardar?',
    mensaje:
      'Hay cambios en esta ficha que todavía no se han guardado. Si sales ahora se pierden.',
    textoConfirmar: 'Salir sin guardar',
    textoCancelar: 'Seguir editando',
    peligrosa: true,
  });
};
