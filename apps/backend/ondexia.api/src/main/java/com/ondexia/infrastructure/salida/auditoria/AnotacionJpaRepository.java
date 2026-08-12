package com.ondexia.infrastructure.salida.auditoria;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AnotacionJpaRepository extends JpaRepository<AnotacionJpa, UUID> {

    /**
     * Anotado porque los métodos de consulta derivados de Spring Data no son
     * transaccionales: sobre una tabla con RLS, sin transacción el inquilino
     * nunca se fija y la consulta devuelve vacío sin dar error.
     */
    @Transactional(readOnly = true)
    List<AnotacionJpa> findByEmpresaIdAndEntidadAndEntidadIdOrderByCreadoEnDesc(
            UUID empresaId, String entidad, UUID entidadId);
}
