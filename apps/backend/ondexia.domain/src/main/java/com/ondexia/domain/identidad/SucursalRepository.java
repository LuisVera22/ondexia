package com.ondexia.domain.identidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SucursalRepository extends JpaRepository<Sucursal, UUID> {

    List<Sucursal> findByEmpresaIdOrderByNombre(UUID empresaId);

    Optional<Sucursal> findByEmpresaIdAndCodigo(UUID empresaId, String codigo);
}
