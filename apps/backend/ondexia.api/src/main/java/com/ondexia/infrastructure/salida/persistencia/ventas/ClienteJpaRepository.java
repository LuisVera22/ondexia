package com.ondexia.infrastructure.salida.persistencia.ventas;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteJpaRepository extends JpaRepository<ClienteJpa, UUID> {

    Optional<ClienteJpa> findByTipoDocumentoAndNumeroDocumento(String tipoDocumento,
            String numeroDocumento);

    List<ClienteJpa> findAllByOrderByNombreAsc();

    @Query(value = """
            select * from cliente
             where numero_documento like :texto || '%'
                or nombre ilike '%' || :texto || '%'
                or nombre % :texto
             order by (numero_documento = :texto) desc, similarity(nombre, :texto) desc, nombre
             limit :maximo
            """, nativeQuery = true)
    List<ClienteJpa> buscar(@Param("texto") String texto, @Param("maximo") int maximo);
}
