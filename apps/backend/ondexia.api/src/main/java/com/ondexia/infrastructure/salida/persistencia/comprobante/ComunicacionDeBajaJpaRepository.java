package com.ondexia.infrastructure.salida.persistencia.comprobante;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Sin {@code empresa_id} en ninguna consulta: lo impone la política de RLS. */
public interface ComunicacionDeBajaJpaRepository extends JpaRepository<ComunicacionDeBajaJpa, UUID> {

    List<ComunicacionDeBajaJpa> findAllByOrderByCreadoEnDesc(Pageable pagina);

    List<ComunicacionDeBajaJpa> findAllByEstadoInOrderByCreadoEnAsc(Collection<String> estados);

    @Query("select c from ComunicacionDeBajaJpa c join c.items i where i.documentoId = :documentoId")
    List<ComunicacionDeBajaJpa> queIncluyen(UUID documentoId);

    @Query("select coalesce(max(c.numeroDelDia), 0) from ComunicacionDeBajaJpa c "
            + "where c.fechaGeneracion = :fecha")
    int ultimoNumeroDelDia(LocalDate fecha);
}
