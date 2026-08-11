package com.ondexia.domain.auditoria;

import java.util.UUID;

/**
 * Una entrada de la bitácora: quién cambió qué y cuándo.
 *
 * <p>Se llama {@code Anotacion} y no {@code Auditoria} porque la auditoría es el
 * mecanismo y esto es un registro suyo. El puerto que la escribe sí se llama
 * {@link RegistroDeAuditoria}.
 *
 * <p>Los datos van en JSON <strong>completo, no como diferencia calculada</strong>:
 * el cálculo depende de la versión del código que lo hizo, y dentro de cinco
 * años —el plazo de conservación fiscal— ese código no existe. El JSON crudo se
 * sigue leyendo.
 *
 * @param empresaId nulo solo en acciones anteriores a toda empresa: crear la
 *                  cuenta, dar de alta al primer administrador
 */
public record Anotacion(
        UUID id,
        UUID empresaId,
        UUID usuarioId,
        String entidad,
        UUID entidadId,
        String accion,
        String datosAntes,
        String datosDespues,
        String ip) {

    public static final String CREAR = "crear";
    public static final String ACTUALIZAR = "actualizar";
    public static final String DESACTIVAR = "desactivar";
    public static final String ACTIVAR = "activar";
    public static final String ELIMINAR = "eliminar";
}
