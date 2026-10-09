package com.ondexia.infrastructure.salida.persistencia.almacen;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisponibilidadJpaRepository extends JpaRepository<DisponibilidadJpa, UUID> {

    Optional<DisponibilidadJpa> findByProductoIdAndSucursalId(UUID productoId, UUID sucursalId);

    List<DisponibilidadJpa> findAllByProductoId(UUID productoId);

    List<DisponibilidadJpa> findAllBySucursalIdAndDisponibleTrue(UUID sucursalId);
}
