package com.ondexia.domain.ventas;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SesionCajaRepositorio {

    Optional<SesionCaja> buscarPorId(UUID id);

    /** A lo sumo una: lo garantiza un índice único parcial en la base. */
    Optional<SesionCaja> buscarAbierta(UUID cajaId);

    /** Las sesiones abiertas de todas las cajas de la empresa activa. */
    List<SesionCaja> listarAbiertas();

    /** Historial de una caja, de la más reciente a la más antigua. */
    List<SesionCaja> listarDeCaja(UUID cajaId);

    SesionCaja guardar(SesionCaja sesion);
}
