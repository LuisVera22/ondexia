package com.ondexia.domain.identidad;

import java.util.Optional;
import java.util.UUID;

public interface CuentaRepositorio {

    Optional<Cuenta> buscarPorId(UUID id);

    Cuenta guardar(Cuenta cuenta);

    /**
     * Incrementa la versión de permisos de forma atómica.
     *
     * <p>Se declara aparte en vez de leer, modificar y guardar el agregado: dos
     * administradores tocando roles a la vez leerían el mismo valor y
     * escribirían el mismo incremento, con lo que una de las dos invalidaciones
     * se perdería y ese contenedor seguiría sirviendo permisos revocados.
     */
    void invalidarCachePermisos(UUID cuentaId);
}
