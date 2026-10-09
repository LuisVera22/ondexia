package com.ondexia.infrastructure.salida.persistencia.marca;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Sin ninguna consulta por empresa: la política de Row Level Security deja como
 * mucho una fila visible, la de la empresa activa.
 */
public interface IdentidadVisualJpaRepository extends JpaRepository<IdentidadVisualJpa, UUID> {

    /**
     * Devuelve lista y no {@code Optional} a propósito.
     *
     * <p>La restricción {@code UNIQUE (empresa_id)} garantiza que sea una o
     * ninguna, pero si por lo que fuera hubiera dos, un método que promete un
     * único resultado lanzaría una excepción de Spring Data que no dice nada del
     * problema real. Con una lista, el adaptador toma la primera y sigue.
     */
    List<IdentidadVisualJpa> findAll();
}
