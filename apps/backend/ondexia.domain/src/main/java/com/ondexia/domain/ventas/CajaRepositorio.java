package com.ondexia.domain.ventas;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Las cajas de la empresa activa. Sin filtro por empresa en las firmas: lo pone
 * la política de Row Level Security, como en el resto de repositorios.
 */
public interface CajaRepositorio {

    Optional<Caja> buscarPorId(UUID id);

    /** El código es único por establecimiento: dos locales pueden tener su «CAJA1». */
    Optional<Caja> buscarPorCodigo(UUID sucursalId, String codigo);

    List<Caja> listar();

    Caja guardar(Caja caja);
}
