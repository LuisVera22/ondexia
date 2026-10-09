package com.ondexia.domain.almacen;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisponibilidadRepositorio {

    Optional<DisponibilidadEnLocal> buscar(UUID productoId, UUID sucursalId);

    List<DisponibilidadEnLocal> listarDeProducto(UUID productoId);

    /** Los productos disponibles en un establecimiento: lo que el punto de venta ofrece. */
    List<DisponibilidadEnLocal> listarDisponiblesEn(UUID sucursalId);

    DisponibilidadEnLocal guardar(DisponibilidadEnLocal disponibilidad);
}
