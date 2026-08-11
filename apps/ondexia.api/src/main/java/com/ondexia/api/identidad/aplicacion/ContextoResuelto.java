package com.ondexia.api.identidad.aplicacion;

import com.ondexia.domain.identidad.AsignacionEmpresa;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resultado de {@link ServicioContexto}. Modelo de la capa de aplicacion, no de
 * transporte — el DTO que sale por HTTP se construye a partir de este.
 *
 * @param empresas         todas a las que el usuario tiene acceso. Alimenta el
 *                         selector de empresa, que solo aparece si hay mas de
 *                         una (DTE §8.1)
 * @param permisos         codigos {@code modulo:accion} del rol en la empresa
 *                         activa. El frontend los usa para ocultar lo que no
 *                         corresponde, que es <strong>comodidad, no
 *                         seguridad</strong>: cada endpoint los vuelve a
 *                         comprobar
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
