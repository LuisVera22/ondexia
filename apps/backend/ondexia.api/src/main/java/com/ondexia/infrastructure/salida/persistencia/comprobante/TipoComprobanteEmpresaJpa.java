package com.ondexia.infrastructure.salida.persistencia.comprobante;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Fila de {@code tipo_comprobante_empresa}: una decisión explícita sobre un tipo.
 *
 * <p>Que no haya fila no significa «no decidido y por tanto apagado», sino
 * habilitado. Ver la cabecera de la V5.
 */
@Entity
@Table(name = "tipo_comprobante_empresa")
public class TipoComprobanteEmpresaJpa extends EntidadJpaBase {

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Column(name = "tipo_documento", nullable = false, updatable = false, length = 2)
    private String tipoDocumento;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected TipoComprobanteEmpresaJpa() {
    }

    public TipoComprobanteEmpresaJpa(UUID id, UUID empresaId, String tipoDocumento,
            boolean activo) {
        this.id = id;
        this.empresaId = empresaId;
        this.tipoDocumento = tipoDocumento;
        this.activo = activo;
    }

    public void cambiarEstado(boolean activo) {
        this.activo = activo;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public boolean isActivo() {
        return activo;
    }
}
