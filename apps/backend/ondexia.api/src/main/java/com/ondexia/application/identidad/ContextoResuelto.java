package com.ondexia.application.identidad;

import com.ondexia.domain.identidad.AsignacionEmpresa;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Salida de {@link ConsultarContexto}. Modelo de la capa de aplicación, no de
 * transporte: el DTO que sale por HTTP se construye a partir de este.
 *
 * @param empresas todas a las que el usuario tiene acceso. Alimenta el selector,
 *                 que solo aparece si hay más de una
 * @param permisos códigos {@code modulo:accion} del rol en la empresa activa. El
 *                 frontend los usa para ocultar lo que no corresponde, que es
 *                 <strong>comodidad, no seguridad</strong>: cada endpoint los
 *                 vuelve a comprobar
 */
public record ContextoResuelto(
        UUID usuarioId,
        String nombre,
        String email,
        UUID cuentaId,
        boolean esAdministradorCuenta,
        UUID empresaActivaId,
        UUID sucursalActivaId,
        List<AsignacionEmpresa> empresas,
        Set<String> permisos) {
}
