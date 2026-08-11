package com.ondexia.domain.identidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RolRepository extends JpaRepository<Rol, UUID> {

    /** Roles predefinidos del sistema, comunes a todas las cuentas. */
    List<Rol> findByCuentaIdIsNullOrderByNombre();

    Optional<Rol> findByCuentaIdIsNullAndCodigo(String codigo);

    /**
     * Los que puede usar una cuenta: los predefinidos mas los suyos.
     *
     * <p>Se resuelve en una consulta y no en dos con union en memoria porque el
     * resultado alimenta un desplegable ordenado, y ordenar dos listas por
     * separado y fusionarlas es trabajo que la base ya sabe hacer.
     */
    @Query("""
            select r from Rol r
            where r.cuentaId is null or r.cuentaId = :cuentaId
            order by r.nombre
            """)
    List<Rol> findDisponiblesParaCuenta(UUID cuentaId);

    Optional<Rol> findByCuentaIdAndCodigo(UUID cuentaId, String codigo);
}
