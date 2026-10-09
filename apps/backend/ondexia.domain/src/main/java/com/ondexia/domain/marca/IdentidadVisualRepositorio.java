package com.ondexia.domain.marca;

import java.util.Optional;

/**
 * Puerto de salida de la identidad visual.
 *
 * <p>Sin identificador de empresa en ninguna operación: la tabla está bajo Row
 * Level Security, así que la política devuelve la fila de la empresa activa y
 * ninguna otra. Lo que no se puede pedir no se puede pedir mal.
 */
public interface IdentidadVisualRepositorio {

    /** Vacío mientras la empresa no haya subido su primer archivo. */
    Optional<IdentidadVisual> buscar();

    IdentidadVisual guardar(IdentidadVisual identidad);
}
