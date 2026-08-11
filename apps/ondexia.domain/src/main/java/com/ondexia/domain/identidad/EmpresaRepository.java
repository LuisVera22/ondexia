package com.ondexia.domain.identidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpresaRepository extends JpaRepository<Empresa, UUID> {

    Optional<Empresa> findByRuc(String ruc);

    boolean existsByRuc(String ruc);

    List<Empresa> findByCuentaIdOrderByRazonSocial(UUID cuentaId);
}
