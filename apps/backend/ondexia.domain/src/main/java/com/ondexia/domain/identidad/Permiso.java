package com.ondexia.domain.identidad;

import java.util.Objects;
import java.util.UUID;

/**
 * Una capacidad concreta del sistema, identificada por {@code (modulo, accion)}.
 *
 * <p><strong>El permiso es por acción, no por módulo.</strong> «Acceso a ventas»
 * no es útil: registrar, aprobar y anular son tres capacidades con tres
 * consecuencias distintas, y quien puede la primera casi nunca debe poder la
 * tercera. Anular un comprobante emitido tiene efecto tributario.
 *
 * <p>Catálogo global, no por cuenta: la lista de capacidades la define el
 * sistema. Lo que el cliente compone son sus roles, eligiendo de esta lista.
 */
public record Permiso(UUID id, String modulo, String accion, String descripcion) {

    public Permiso {
        Objects.requireNonNull(modulo, "modulo");
        Objects.requireNonNull(accion, "accion");
    }

    /** Forma canónica. Fuente única del separador: nadie más lo concatena. */
    public static String componerCodigo(String modulo, String accion) {
        return modulo + ":" + accion;
    }

    public String codigo() {
        return componerCodigo(modulo, accion);
    }
}
