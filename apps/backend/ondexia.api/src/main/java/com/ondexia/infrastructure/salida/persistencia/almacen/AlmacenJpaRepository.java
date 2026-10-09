package com.ondexia.infrastructure.salida.persistencia.almacen;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Ninguna consulta menciona {@code empresa_id}, y es correcto: lo impone la
 * política de Row Level Security de la tabla. Añadir el filtro aquí sugeriría
 * que el aislamiento depende de que alguien se acuerde de escribirlo.
 */
public interface AlmacenJpaRepository extends JpaRepository<AlmacenJpa, UUID> {

    Optional<AlmacenJpa> findByCodigo(String codigo);

    List<AlmacenJpa> findAllByOrderByCodigoAsc();

    Optional<AlmacenJpa> findFirstBySucursalIdAndActivoTrueOrderByCreadoEnAscCodigoAsc(UUID sucursalId);
}
