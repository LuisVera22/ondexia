package com.ondexia.infrastructure.salida.persistencia.identidad;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Fila de {@code usuario_empresa}. {@code sucursalId} nulo = todas. */
@Entity
@Table(name = "usuario_empresa")
public class UsuarioEmpresaJpa extends EntidadJpaBase {

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Column(name = "rol_id", nullable = false)
    private UUID rolId;

    @Column(name = "sucursal_id")
    private UUID sucursalId;

    protected UsuarioEmpresaJpa() {
    }

    public UsuarioEmpresaJpa(UUID id, UUID usuarioId, UUID empresaId, UUID rolId, UUID sucursalId) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.empresaId = empresaId;
        actualizarDesde(rolId, sucursalId);
    }

    public final void actualizarDesde(UUID rolId, UUID sucursalId) {
        this.rolId = rolId;
        this.sucursalId = sucursalId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public UUID getRolId() {
        return rolId;
    }

    public UUID getSucursalId() {
        return sucursalId;
    }
}
