package com.ondexia.infrastructure.salida.persistencia.comprobante;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "comunicacion_baja_item")
public class ComunicacionDeBajaItemJpa {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comunicacion_id", nullable = false, updatable = false)
    private ComunicacionDeBajaJpa comunicacion;

    @Column(name = "documento_id", nullable = false, updatable = false)
    private UUID documentoId;

    @Column(name = "tipo_documento", nullable = false, updatable = false, length = 2)
    private String tipoDocumento;

    @Column(name = "serie", nullable = false, updatable = false, length = 4)
    private String serie;

    @Column(name = "numero", nullable = false, updatable = false)
    private long numero;

    @Column(name = "motivo", nullable = false, updatable = false, length = 300)
    private String motivo;

    protected ComunicacionDeBajaItemJpa() {
    }

    public ComunicacionDeBajaItemJpa(UUID id, UUID empresaId, UUID documentoId,
            String tipoDocumento, String serie, long numero, String motivo) {
        this.id = id;
        this.empresaId = empresaId;
        this.documentoId = documentoId;
        this.tipoDocumento = tipoDocumento;
        this.serie = serie;
        this.numero = numero;
        this.motivo = motivo;
    }

    void asignarA(ComunicacionDeBajaJpa comunicacion) {
        this.comunicacion = comunicacion;
    }

    public UUID getDocumentoId() {
        return documentoId;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public String getSerie() {
        return serie;
    }

    public long getNumero() {
        return numero;
    }

    public String getMotivo() {
        return motivo;
    }
}
