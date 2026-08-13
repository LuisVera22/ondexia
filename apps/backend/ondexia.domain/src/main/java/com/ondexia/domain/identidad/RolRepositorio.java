package com.ondexia.domain.identidad;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Roles disponibles para una cuenta.
 *
 * <p>Hay dos clases y conviene no mezclarlas. Los <strong>predefinidos</strong>
 * —Administrador, Vendedor, Almacenero— tienen {@code cuenta_id} nulo y los
 * comparten todas las cuentas; son inmutables a propósito, porque si cada
 * cliente pudiera editar «Vendedor» la palabra dejaría de significar lo mismo
 * entre clientes. Los <strong>a medida</strong> pertenecen a una cuenta y salen
 * de duplicar uno predefinido (Entrega 5).
 */
public interface RolRepositorio {

    /**
     * Busca un rol predefinido por su código.
     *
     * @param codigo {@code ADMINISTRADOR}, {@code VENDEDOR} o {@code ALMACENERO}
     */
    Optional<Rol> buscarPredefinido(String codigo);

    /** Los predefinidos más los propios de la cuenta. */
    List<Rol> listarDisponibles(UUID cuentaId);

    Optional<Rol> buscarPorId(UUID id);

    /** ¿Ya existe un rol con ese código en la cuenta? Evita el choque del índice único. */
    boolean existeCodigoEnCuenta(UUID cuentaId, String codigo);

    Rol guardar(Rol rol);

    /**
     * Los identificadores de permiso que tiene el rol.
     *
     * <p>Devuelve identificadores y no códigos, al revés que
     * {@link PermisoRepositorio#permisosDelRol}. No es duplicación: aquel sirve
     * al camino caliente de la autorización, donde lo que se hace es comprobar
     * pertenencia a un conjunto de cadenas; este sirve a la matriz de edición,
     * donde hay que marcar casillas que el cliente devolverá por identificador.
     */
    Set<UUID> permisosDe(UUID rolId);

    /**
     * Deja el rol exactamente con estos permisos: añade los que faltan y quita
     * los que sobran.
     *
     * <p>Reemplazo completo y no «añadir» y «quitar» por separado, porque la
     * pantalla que lo usa es una matriz de casillas: manda el estado final, y
     * calcular el diferencial en el cliente sería una segunda fuente de verdad
     * sobre qué permisos tiene el rol.
     */
    void reemplazarPermisos(UUID rolId, Set<UUID> permisoIds);

    /** Si alguien lo tiene asignado en cualquier empresa de la cuenta. */
    boolean estaAsignadoAAlguien(UUID rolId);

    void eliminar(UUID id);
}
