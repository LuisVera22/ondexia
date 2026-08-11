package com.ondexia.domain.identidad;

import java.util.UUID;

/**
 * Una empresa a la que un usuario tiene acceso, con el rol y el alcance que le
 * corresponden.
 *
 * <p>Es una proyeccion de solo lectura, no una entidad. Se construye con una
 * sola consulta que une usuario_empresa, empresa, rol y sucursal.
 *
 * <p>Existe para evitar el problema N+1 en el camino mas transitado del
 * sistema: cada peticion autenticada necesita saber a que empresas alcanza el
 * usuario. Navegando asociaciones JPA serian una consulta por la lista mas una
 * por empresa, mas una por rol, mas una por sucursal — cinco empresas son
 * dieciseis viajes a la base, y en Lambda cada viaje es latencia sobre una
 * conexion que no esta en la misma maquina.
 *
 * @param sucursalId     {@code null} si el usuario alcanza todas las sucursales
 * @param sucursalNombre {@code null} en el mismo caso
 */
public record AsignacionEmpresa(
        UUID empresaId,
        String ruc,
        String razonSocial,
        String nombreComercial,
        ModoSunat modoSunat,
        UUID rolId,
        String rolCodigo,
        String rolNombre,
        UUID sucursalId,
        String sucursalNombre) {

    public boolean alcanzaTodasLasSucursales() {
        return sucursalId == null;
    }
}
