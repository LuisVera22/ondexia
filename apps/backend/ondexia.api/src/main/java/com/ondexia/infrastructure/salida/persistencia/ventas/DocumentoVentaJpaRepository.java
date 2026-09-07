package com.ondexia.infrastructure.salida.persistencia.ventas;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoVentaJpaRepository extends JpaRepository<DocumentoVentaJpa, UUID> {

    List<DocumentoVentaJpa> findAllByOrderByEmitidoEnDesc(org.springframework.data.domain.Pageable pagina);

    List<DocumentoVentaJpa> findAllByTipoDocumentoOrderByEmitidoEnDesc(String tipoDocumento,
            org.springframework.data.domain.Pageable pagina);
}
