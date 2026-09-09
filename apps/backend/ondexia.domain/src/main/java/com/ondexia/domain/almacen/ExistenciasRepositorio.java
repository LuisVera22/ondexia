package com.ondexia.domain.almacen;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * El libro y su proyección, juntos a propósito: {@link #mover} escribe el
 * movimiento y suma a la existencia en la misma operación, y no hay forma de
 * hacer una cosa sin la otra.
 */
public interface ExistenciasRepositorio {

    Optional<Existencia> buscar(UUID almacenId, UUID productoId);

    List<Existencia> existenciasDe(UUID productoId);

    List<MovimientoStock> movimientosDe(UUID productoId, int maximo);

    /**
     * Anota el movimiento y aplica su cantidad, con signo, a la existencia del
     * almacén; la crea si no la había. Atómico frente a otro movimiento del
     * mismo producto en el mismo almacén.
     *
     * @return la existencia resultante
     */
    Existencia mover(MovimientoStock movimiento);
}
