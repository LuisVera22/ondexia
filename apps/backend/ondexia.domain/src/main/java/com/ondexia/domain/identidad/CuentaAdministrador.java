package com.ondexia.domain.identidad;

import java.util.Objects;
import java.util.UUID;

/**
 * Quien gobierna la cuenta: factura, crea empresas, asigna usuarios.
 *
 * <p><strong>No es un rol de la matriz de permisos</strong>, por tres razones y
 * cada una basta: existe antes que cualquier empresa (y la matriz se evalúa
 * sobre el par usuario-empresa); gobierna la facturación de la suscripción, que
 * no es un módulo; y necesita el invariante «no puede quedar vacía», que la
 * matriz no puede expresar.
 *
 * <p>Ese invariante lo defiende un disparador en la base, no este código: dos
 * administradores renunciando en transacciones paralelas pasarían ambos la
 * comprobación en Java. Ver la migración V1.
 */
public class CuentaAdministrador {

    private final UUID id;
    private final UUID cuentaId;
    private final UUID usuarioId;

    public CuentaAdministrador(UUID id, UUID cuentaId, UUID usuarioId) {
        this.id = Objects.requireNonNull(id, "id");
        this.cuentaId = Objects.requireNonNull(cuentaId, "cuentaId");
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
    }

    public UUID id() {
        return id;
    }

    public UUID cuentaId() {
        return cuentaId;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof CuentaAdministrador otra && id.equals(otra.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
