package com.ondexia.infrastructure.salida.persistencia.comprobante;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * Ninguna consulta menciona {@code empresa_id}: lo impone la política de Row
 * Level Security de la tabla.
 */
public interface SerieCorrelativoJpaRepository extends JpaRepository<SerieCorrelativoJpa, UUID> {

    Optional<SerieCorrelativoJpa> findByTipoDocumentoAndSerie(String tipoDocumento, String serie);

    List<SerieCorrelativoJpa> findAllByOrderByTipoDocumentoAscSerieAsc();

    /**
     * La consulta que sostiene la unicidad del correlativo.
     *
     * <p>{@code PESSIMISTIC_WRITE} genera {@code SELECT … FOR UPDATE}: la primera
     * transacción que la ejecuta retiene la fila y las demás se detienen ahí
     * hasta que confirme o se deshaga. Sin ese bloqueo, dos peticiones
     * simultáneas leen el mismo {@code ultimo_numero}, ambas suman uno, y se
     * emiten dos comprobantes con el mismo número.
     *
     * <p>Es una consulta explícita y no {@code findById} con {@code @Lock}
     * encima: {@code findById} atiende también al camino de solo lectura, y
     * ponerle un bloqueo de escritura serializaría cada consulta de series.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SerieCorrelativoJpa s where s.id = :id")
    Optional<SerieCorrelativoJpa> bloquearPorId(UUID id);
}
