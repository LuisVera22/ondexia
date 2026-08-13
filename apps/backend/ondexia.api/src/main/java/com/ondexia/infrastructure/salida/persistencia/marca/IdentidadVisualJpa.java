package com.ondexia.infrastructure.salida.persistencia.marca;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Fila de {@code identidad_visual}: claves de S3, nunca bytes. */
@Entity
@Table(name = "identidad_visual")
public class IdentidadVisualJpa extends EntidadJpaBase {

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Column(name = "logo_principal", length = 400)
    private String logoPrincipal;

    @Column(name = "logo_ticket", length = 400)
    private String logoTicket;

    @Column(name = "simbolo", length = 400)
    private String simbolo;

    protected IdentidadVisualJpa() {
    }

    public IdentidadVisualJpa(UUID id, UUID empresaId, String logoPrincipal, String logoTicket,
            String simbolo) {
        this.id = id;
        this.empresaId = empresaId;
        actualizarDesde(logoPrincipal, logoTicket, simbolo);
    }

    public final void actualizarDesde(String logoPrincipal, String logoTicket, String simbolo) {
        this.logoPrincipal = logoPrincipal;
        this.logoTicket = logoTicket;
        this.simbolo = simbolo;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public String getLogoPrincipal() {
        return logoPrincipal;
    }

    public String getLogoTicket() {
        return logoTicket;
    }

    public String getSimbolo() {
        return simbolo;
    }
}
