package com.ondexia.domain.marca;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Los logos de una empresa. Guarda <strong>claves de S3</strong>, nunca bytes.
 *
 * <p>Un archivo dentro de la base aparece en cada respaldo y en cada volcado de
 * desarrollo, y engorda algo que se restaura entero cuando hay una urgencia.
 *
 * <p>Cada subida estrena clave: colocar un logo nuevo no sobrescribe el
 * anterior, lo sustituye en esta fila y deja el objeto viejo donde estaba. Es lo
 * que permite que un PDF emitido el año pasado siga mostrando el logo que tenía
 * entonces — reemplazar el objeto reescribiría la historia en silencio.
 */
public class IdentidadVisual {

    private final UUID id;
    private final UUID empresaId;
    private final Map<LogoDeEmpresa, String> claves = new EnumMap<>(LogoDeEmpresa.class);

    public IdentidadVisual(UUID id, UUID empresaId) {
        this.id = Objects.requireNonNull(id, "id");
        this.empresaId = Objects.requireNonNull(empresaId, "empresaId");
    }

    /** Reconstrucción desde persistencia. */
    public IdentidadVisual(UUID id, UUID empresaId, String logoPrincipal, String logoTicket,
            String simbolo) {
        this(id, empresaId);
        colocarSiHay(LogoDeEmpresa.PRINCIPAL, logoPrincipal);
        colocarSiHay(LogoDeEmpresa.TICKET, logoTicket);
        colocarSiHay(LogoDeEmpresa.SIMBOLO, simbolo);
    }

    public UUID id() {
        return id;
    }

    public UUID empresaId() {
        return empresaId;
    }

    public Optional<String> clave(LogoDeEmpresa logo) {
        return Optional.ofNullable(claves.get(logo));
    }

    /**
     * Pone la clave nueva y devuelve la que había, para que quien llama decida
     * qué hacer con el objeto anterior.
     *
     * <p>No se borra desde aquí: el agregado no conoce S3, y además borrarlo sin
     * más rompería los documentos que lo referencian. La decisión es del caso de
     * uso.
     */
    public Optional<String> colocar(LogoDeEmpresa logo, String claveNueva) {
        Objects.requireNonNull(claveNueva, "claveNueva");
        return Optional.ofNullable(claves.put(logo, claveNueva));
    }

    /** Quita la referencia y devuelve la clave que tenía, si tenía alguna. */
    public Optional<String> quitar(LogoDeEmpresa logo) {
        return Optional.ofNullable(claves.remove(logo));
    }

    private void colocarSiHay(LogoDeEmpresa logo, String clave) {
        if (clave != null && !clave.isBlank()) {
            claves.put(logo, clave);
        }
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof IdentidadVisual otra && id.equals(otra.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
