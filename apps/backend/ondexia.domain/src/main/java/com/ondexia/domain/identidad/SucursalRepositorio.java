package com.ondexia.domain.identidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SucursalRepositorio {

    Optional<Sucursal> buscarPorId(UUID id);

    Optional<Sucursal> buscarPorCodigo(UUID empresaId, String codigo);

    List<Sucursal> listarDeEmpresa(UUID empresaId);

    Sucursal guardar(Sucursal sucursal);
}
