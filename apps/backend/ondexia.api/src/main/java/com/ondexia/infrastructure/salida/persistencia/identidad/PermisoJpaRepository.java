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

    /**
     * El catálogo entero, ordenado de forma que los tres niveles de una misma
     * rama salgan juntos y en orden: el módulo, su submódulo, y las funciones.
     *
     * <p>El orden por {@code nivel} descendente no es capricho: alfabéticamente
     * es {@code FUNCION < MODULO < SUBMODULO}, y al revés queda
     * {@code SUBMODULO, MODULO, FUNCION} — tampoco sirve. Se ordena explícitamente
     * con un CASE para que el árbol se pueda construir de una pasada.
     */
    @Query("""
            select p from PermisoJpa p
            order by
                case p.nivel when 'MODULO' then 0 when 'SUBMODULO' then 1 else 2 end,
                p.modulo asc,
                p.accion asc
            """)
    List<PermisoJpa> findCatalogoOrdenado();
}
