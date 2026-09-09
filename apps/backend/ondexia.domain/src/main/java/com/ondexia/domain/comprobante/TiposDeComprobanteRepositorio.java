package com.ondexia.domain.comprobante;

import java.util.Set;

/**
 * Qué tipos de comprobante emite la empresa activa.
 *
 * <p>El puerto habla de <strong>lo desactivado</strong>, no de lo activo, y esa
 * asimetría es deliberada: la tabla guarda decisiones, y la ausencia de fila
 * significa habilitado. Ofrecer {@code activos()} obligaría al adaptador a
 * inventarse las filas que faltan, y entonces el valor predeterminado viviría en
 * dos sitios —la migración y el adaptador— que es como acaban divergiendo.
 *
 * <p>Aquí solo se responde qué se apartó de lo normal; quien quiera la lista
 * completa la compone contra {@link TipoDocumento#values()}, que es la fuente
 * única de qué tipos existen.
 */
public interface TiposDeComprobanteRepositorio {

    /** Los que la empresa activa decidió no emitir. Vacío = emite todos. */
    Set<TipoDocumento> desactivados();

    default boolean emite(TipoDocumento tipo) {
        return !desactivados().contains(tipo);
    }

    /** Deja constancia de la decisión. Guardar «activo» también es una decisión. */
    void fijar(TipoDocumento tipo, boolean activo);
}
