package com.ondexia.domain.identidad;

import java.util.List;
import java.util.UUID;

public interface CuentaAdministradorRepositorio {

    boolean esAdministrador(UUID cuentaId, UUID usuarioId);

    List<CuentaAdministrador> listarDeCuenta(UUID cuentaId);

    long contarEnCuenta(UUID cuentaId);

    /**
     * Cuantos administradores ACTIVOS quedan, con la cuenta bloqueada hasta que
     * termine la transaccion (hallazgo M6).
     *
     * <p>Es la version que hay que usar antes de desactivar a alguien: dos
     * operaciones concurrentes sobre la misma cuenta se serializan aqui, y la
     * segunda ve el resultado de la primera.
     */
    long contarActivosEnCuentaBloqueando(UUID cuentaId);

    CuentaAdministrador guardar(CuentaAdministrador administrador);

    /**
     * La base impide que la cuenta se quede sin ninguno, con un disparador
     * diferido. Este método puede llamarse con confianza: si dejara la cuenta
     * huérfana, falla al confirmar.
     */
    void eliminar(UUID id);
}
