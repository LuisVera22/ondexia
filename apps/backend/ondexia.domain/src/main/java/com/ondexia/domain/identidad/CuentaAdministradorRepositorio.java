package com.ondexia.domain.identidad;

import java.util.List;
import java.util.UUID;

public interface CuentaAdministradorRepositorio {

    boolean esAdministrador(UUID cuentaId, UUID usuarioId);

    List<CuentaAdministrador> listarDeCuenta(UUID cuentaId);

    long contarEnCuenta(UUID cuentaId);

    CuentaAdministrador guardar(CuentaAdministrador administrador);

    /**
     * La base impide que la cuenta se quede sin ninguno, con un disparador
     * diferido. Este método puede llamarse con confianza: si dejara la cuenta
     * huérfana, falla al confirmar.
     */
    void eliminar(UUID id);
}
