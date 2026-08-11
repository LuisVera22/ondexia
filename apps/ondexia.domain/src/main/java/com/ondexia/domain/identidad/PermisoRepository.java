package com.ondexia.domain.identidad;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PermisoRepository extends JpaRepository<Permiso, UUID> {

    /**
     * Los codigos de permiso de un rol. Camino caliente de la autorizacion.
     *
     * <p>Devuelve cadenas y no entidades a proposito. Comprobar si alguien puede
     * ejecutar una accion es una pertenencia a conjunto; materializar 200
     * entidades gestionadas por el contexto de persistencia para despues leer
     * un campo de cada una es trabajo puro. El resultado va directo a un
     * {@code Set<String>} cacheado en memoria del contenedor.
     */
    @Query("select p.codigo from Rol r join r.permisos p where r.id = :rolId")
    Set<String> findCodigosByRolId(UUID rolId);

    List<Permiso> findAllByOrderByModuloAscAccionAsc();

    List<Permiso> findByModuloOrderByAccion(String modulo);
}
