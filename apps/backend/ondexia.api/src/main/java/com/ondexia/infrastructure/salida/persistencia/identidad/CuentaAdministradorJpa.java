package com.ondexia.infrastructure.salida.persistencia.identidad;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Fila de {@code cuenta_administrador}.
 *
 * <p>El invariante «la cuenta no puede quedarse sin administrador» lo defiende
 * un disparador diferido en la migración V1, no este código: dos borrados
 * concurrentes pasarían ambos una comprobación en Java.
 */
@Entity
@Table(name = "cuenta_administrador")
public class CuentaAdministradorJpa extends EntidadJpaBase {

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private UUID cuentaId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    protected CuentaAdministradorJpa() {
    }

    public CuentaAdministradorJpa(UUID id, UUID cuentaId, UUID usuarioId) {
        this.id = id;
        this.cuentaId = cuentaId;
        this.usuarioId = usuarioId;
    }

    public UUID getCuentaId() {
        return cuentaId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }
}
