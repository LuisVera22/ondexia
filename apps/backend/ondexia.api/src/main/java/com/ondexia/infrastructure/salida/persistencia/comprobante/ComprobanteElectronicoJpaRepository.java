package com.ondexia.infrastructure.salida.persistencia.comprobante;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Sin {@code empresa_id} en ninguna consulta: lo impone la política de RLS. */
public interface ComprobanteElectronicoJpaRepository
        extends JpaRepository<ComprobanteElectronicoJpa, UUID> {

    Optional<ComprobanteElectronicoJpa> findByDocumentoId(UUID documentoId);

    List<ComprobanteElectronicoJpa> findAllByDocumentoIdIn(Collection<UUID> documentoIds);
}
