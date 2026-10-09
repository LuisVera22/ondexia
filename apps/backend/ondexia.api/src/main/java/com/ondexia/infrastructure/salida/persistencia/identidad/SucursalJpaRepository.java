package com.ondexia.infrastructure.salida.persistencia.identidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SucursalJpaRepository extends JpaRepository<SucursalJpa, UUID> {

    Optional<SucursalJpa> findByEmpresaIdAndCodigo(UUID empresaId, String codigo);

    List<SucursalJpa> findByEmpresaIdOrderByNombre(UUID empresaId);
}
