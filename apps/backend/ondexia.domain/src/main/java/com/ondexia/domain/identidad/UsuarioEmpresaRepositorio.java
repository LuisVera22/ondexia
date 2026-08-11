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

    UsuarioEmpresa guardar(UsuarioEmpresa asignacion);
}
