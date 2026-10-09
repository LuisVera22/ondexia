package com.ondexia.domain.identidad;

import java.util.Objects;
import java.util.UUID;

/**
 * Una entrada del catálogo de permisos, en cualquiera de sus tres niveles.
 *
 * <p><strong>El permiso es por acción, no por módulo.</strong> «Acceso a ventas»
 * no es útil por sí solo: registrar, aprobar y anular son tres capacidades con
 * tres consecuencias distintas, y quien puede la primera casi nunca debe poder
 * la tercera. Anular un comprobante emitido tiene efecto tributario.
 *
 * <p>Pero el acceso al módulo y al submódulo <em>también</em> se conceden, y son
 * necesarios: la evaluación es conjuntiva (ver {@link Permisos#puede}). Eso da el
 * interruptor de área que retira todo un bloque de una vez, sin dejar de permitir
 * el detalle.
 *
 * <p>Catálogo global, no por cuenta: la lista de capacidades la define el
 * sistema. Lo que el cliente compone son sus roles, eligiendo de esta lista.
 *
 * @param modulo para un {@link NivelPermiso#MODULO} es {@code almacen}; para los
 *               otros dos, {@code almacen.producto}
 * @param accion {@code acceder} en módulo y submódulo — así el código se compone
 *               igual en los tres niveles y no hay dos formas de construirlo
 * @param nombre como se muestra en la matriz. Viene de la base y no de un mapa
 *               en el frontend, para que un módulo nuevo no exija tocar dos
 *               repositorios
 */
public record Permiso(
        UUID id,
        NivelPermiso nivel,
        String modulo,
        String accion,
        String nombre,
        String descripcion) {

    /** La acción de los niveles que no son una función concreta. */
    public static final String ACCEDER = "acceder";

    public Permiso {
        Objects.requireNonNull(nivel, "nivel");
        Objects.requireNonNull(modulo, "modulo");
        Objects.requireNonNull(accion, "accion");
    }

    /** Forma canónica. Fuente única del separador: nadie más lo concatena. */
    public static String componerCodigo(String modulo, String accion) {
        return modulo + ":" + accion;
    }

    /** {@code almacen.producto} → {@code almacen}. Vacío si ya es un módulo. */
    public static String moduloDe(String codigoDeSubmodulo) {
        int punto = codigoDeSubmodulo.indexOf('.');
        return punto < 0 ? "" : codigoDeSubmodulo.substring(0, punto);
    }

    public String codigo() {
        return componerCodigo(modulo, accion);
    }

    /**
     * El código del padre, o vacío si es un módulo.
     *
     * <p>Un submódulo cuelga de su módulo; una función, de su submódulo.
     */
    public String codigoDelPadre() {
        return switch (nivel) {
            case MODULO -> "";
            case SUBMODULO -> componerCodigo(moduloDe(modulo), ACCEDER);
            case FUNCION -> componerCodigo(modulo, ACCEDER);
        };
    }
}
