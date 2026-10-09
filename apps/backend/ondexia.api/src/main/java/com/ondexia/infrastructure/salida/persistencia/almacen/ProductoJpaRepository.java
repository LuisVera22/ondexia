package com.ondexia.infrastructure.salida.persistencia.almacen;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoJpaRepository extends JpaRepository<ProductoJpa, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductoJpa p where p.id = :id")
    Optional<ProductoJpa> buscarParaActualizar(@Param("id") UUID id);

    Optional<ProductoJpa> findByCodigo(String codigo);

    List<ProductoJpa> findAllByOrderByCodigoAsc();

    /**
     * Por código (prefijo) o por nombre (trigramas, sin mayúsculas). Nativa
     * porque {@code %} de pg_trgm no existe en JPQL; el RLS filtra igual.
     */
    @Query(value = """
            select * from producto
             where codigo like upper(:texto) || '%'
                or nombre ilike '%' || :texto || '%'
                or nombre % :texto
             order by (codigo = upper(:texto)) desc, similarity(nombre, :texto) desc, codigo
             limit :maximo
            """, nativeQuery = true)
    List<ProductoJpa> buscar(@Param("texto") String texto, @Param("maximo") int maximo);
}
