package com.ondexia.domain.identidad;

import java.util.List;
import java.util.UUID;

public interface PermisoRepositorio {

    /**
     * Los permisos de un rol. Camino caliente de la autorización.
     *
     * <p>Devuelve el value object y no entidades: comprobar si alguien puede
     * ejecutar una acción es una pertenencia a conjunto, y materializar 200
     * entidades gestionadas para leer un campo de cada una es trabajo puro.
     */
    Permisos permisosDelRol(UUID rolId);

    List<Permiso> listarCatalogo();
}
