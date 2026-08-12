package com.ondexia.infrastructure.salida.persistencia.identidad;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PermisoJpaRepository extends JpaRepository<PermisoJpa, UUID> {

    /**
     * Devuelve cadenas y no entidades: comprobar si alguien puede ejecutar una
     * acción es una pertenencia a conjunto, y materializar 200 entidades
     * gestionadas para leer un campo de cada una es trabajo puro.
     */
    @Query("select p.codigo from RolJpa r join r.permisos p where r.id = :rolId")
    Set<String> findCodigosByRolId(UUID rolId);

    List<PermisoJpa> findAllByOrderByModuloAscAccionAsc();
}
