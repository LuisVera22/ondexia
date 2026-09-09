package com.ondexia.domain.almacen;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductoRepositorio {

    Optional<Producto> buscarPorId(UUID id);

    Optional<Producto> buscarPorCodigo(String codigo);

    /** Todos los de la empresa activa, activos e inactivos, por código. */
    List<Producto> listar();

    /** Por código o por nombre, sin distinguir mayúsculas ni acentos que el motor sepa quitar. */
    List<Producto> buscar(String texto, int maximo);

    Producto guardar(Producto producto);
}
