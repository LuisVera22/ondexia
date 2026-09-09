package com.ondexia.infrastructure.salida.persistencia.identidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolJpaRepository extends JpaRepository<RolJpa, UUID> {

    /** Predefinido: {@code cuenta_id} nulo, compartido por todas las cuentas. */
    Optional<RolJpa> findByCuentaIdIsNullAndCodigo(String codigo);

    /**
     * Los predefinidos más los de la cuenta.
     *
     * <p>Consulta explícita y no un método derivado: el nombre derivado
     * equivalente sería {@code findByCuentaIdIsNullOrCuentaId}, que se lee mal y
     * cuya precedencia entre el OR y el resto de condiciones no es evidente al
     * leerla.
     */
    @Query("select r from RolJpa r where r.cuentaId is null or r.cuentaId = :cuentaId "
            + "order by r.nombre asc")
    List<RolJpa> findDisponibles(@Param("cuentaId") UUID cuentaId);

    boolean existsByCuentaIdAndCodigo(UUID cuentaId, String codigo);

    /**
     * Trae el rol con sus permisos ya cargados.
     *
     * <p>La colección es perezosa a propósito —el camino caliente no la necesita—
     * así que el único sitio que sí la quiere lo dice explícitamente, en vez de
     * volverla {@code EAGER} y hacer que cada consulta de roles arrastre
     * doscientas filas.
     */
    @Query("select r from RolJpa r left join fetch r.permisos where r.id = :id")
    Optional<RolJpa> findConPermisos(@Param("id") UUID id);
}
