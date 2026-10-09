package com.ondexia.infrastructure.salida.persistencia.ventas;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SesionCajaJpaRepository extends JpaRepository<SesionCajaJpa, UUID> {

    Optional<SesionCajaJpa> findByCajaIdAndEstado(UUID cajaId, String estado);

    List<SesionCajaJpa> findAllByEstadoOrderByAbiertaEnDesc(String estado);

    List<SesionCajaJpa> findAllByCajaIdOrderByAbiertaEnDesc(UUID cajaId);
}
