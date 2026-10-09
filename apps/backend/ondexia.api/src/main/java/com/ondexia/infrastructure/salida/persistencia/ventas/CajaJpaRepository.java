package com.ondexia.infrastructure.salida.persistencia.ventas;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CajaJpaRepository extends JpaRepository<CajaJpa, UUID> {

    Optional<CajaJpa> findBySucursalIdAndCodigo(UUID sucursalId, String codigo);

    List<CajaJpa> findAllByOrderByCodigoAsc();
}
