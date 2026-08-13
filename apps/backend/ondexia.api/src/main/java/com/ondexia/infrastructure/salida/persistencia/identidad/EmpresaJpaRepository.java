package com.ondexia.infrastructure.salida.persistencia.identidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpresaJpaRepository extends JpaRepository<EmpresaJpa, UUID> {

    Optional<EmpresaJpa> findByRuc(String ruc);

    List<EmpresaJpa> findByCuentaIdOrderByRazonSocial(UUID cuentaId);
}
