package com.ondexia.domain.identidad;

import com.ondexia.domain.comun.Ruc;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmpresaRepositorio {

    Optional<Empresa> buscarPorId(UUID id);

    /** Recibe un {@link Ruc}, no una cadena: quien pregunta ya validó la forma. */
    Optional<Empresa> buscarPorRuc(Ruc ruc);

    List<Empresa> listarDeCuenta(UUID cuentaId);

    Empresa guardar(Empresa empresa);
}
