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

    /**
     * Los estados que no son ACEPTADO ni ANULADO, del más reciente al más viejo.
     *
     * <p>La lista de estados llega desde fuera y no está escrita aquí: quien
     * decide qué estado exige atención es {@code EstadoSunat}, y repetirlo en
     * una consulta sería una segunda definición que nadie actualiza cuando se
     * añada un estado.
     */
    List<ComprobanteElectronicoJpa> findAllByEstadoInOrderByCreadoEnDesc(Collection<String> estados);
}
