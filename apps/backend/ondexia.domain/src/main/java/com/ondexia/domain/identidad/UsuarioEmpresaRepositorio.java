package com.ondexia.domain.identidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioEmpresaRepositorio {

    /**
     * Todas las empresas que alcanza un usuario, resueltas de una vez.
     *
     * <p>Devuelve la proyección y no las asignaciones porque es el camino más
     * transitado del sistema: cada petición autenticada necesita saber a qué
     * empresas llega el usuario. Navegando asociaciones serían cinco empresas =
     * dieciséis viajes a la base, y en Lambda cada viaje es latencia sobre una
     * conexión que no está en la misma máquina.
     */
    List<AsignacionEmpresa> listarAsignacionesDe(UUID usuarioId);

    Optional<UsuarioEmpresa> buscarAsignacion(UUID usuarioId, UUID empresaId);

    List<UsuarioEmpresa> listarDeEmpresa(UUID empresaId);

    /**
     * Quién entra en esta empresa, con su rol y su alcance, en una consulta.
     *
     * <p>Es la pantalla de usuarios. Se resuelve con una proyección por el mismo
     * motivo que {@link #listarAsignacionesDe}: navegar asociaciones sería una
     * consulta por miembro más una por rol más una por sucursal.
     */
    List<MiembroEmpresa> listarMiembrosDe(UUID empresaId);

    Optional<UsuarioEmpresa> buscarPorId(UUID id);

    UsuarioEmpresa guardar(UsuarioEmpresa asignacion);

    /**
     * Retira el acceso de alguien a una empresa.
     *
     * <p>Esto sí se borra, y es la excepción a la regla de desactivar en vez de
     * eliminar. La diferencia está en qué representa la fila: un almacén o una
     * serie aparecen en documentos ya emitidos, y borrarlos dejaría esos
     * documentos apuntando a nada. Una asignación no aparece en ningún
     * comprobante — es un permiso vigente, y un permiso retirado no tiene por qué
     * seguir existiendo. Quién hizo qué queda en la bitácora, que es donde debe
     * quedar.
     */
    void eliminar(UUID id);
}
