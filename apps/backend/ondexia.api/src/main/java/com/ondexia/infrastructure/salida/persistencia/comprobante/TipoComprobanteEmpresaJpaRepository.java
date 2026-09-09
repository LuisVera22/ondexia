package com.ondexia.infrastructure.salida.persistencia.comprobante;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Ninguna consulta menciona {@code empresa_id}: lo impone la política de Row
 * Level Security de la tabla.
 *
 * <p>Va en su propio archivo y no anidada en el adaptador —que es donde se leería
 * mejor, siendo dos métodos— porque Spring Data <strong>no escanea interfaces
 * anidadas</strong>: el contexto arranca sin el bean y falla con un «required a
 * bean of type … $Filas that could not be found» que no apunta a la causa.
 */
public interface TipoComprobanteEmpresaJpaRepository
        extends JpaRepository<TipoComprobanteEmpresaJpa, UUID> {

    /** Solo las decisiones de apagar. Lo que no aparece está habilitado. */
    List<TipoComprobanteEmpresaJpa> findByActivoFalse();

    Optional<TipoComprobanteEmpresaJpa> findByTipoDocumento(String tipoDocumento);
}
