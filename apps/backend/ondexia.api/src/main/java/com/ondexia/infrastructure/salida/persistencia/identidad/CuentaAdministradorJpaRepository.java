package com.ondexia.infrastructure.salida.persistencia.identidad;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuentaAdministradorJpaRepository
        extends JpaRepository<CuentaAdministradorJpa, UUID> {

    boolean existsByCuentaIdAndUsuarioId(UUID cuentaId, UUID usuarioId);

    List<CuentaAdministradorJpa> findByCuentaId(UUID cuentaId);

    long countByCuentaId(UUID cuentaId);
}
