package com.ondexia.domain.identidad;

import java.util.List;
import java.util.Optional;
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
}
