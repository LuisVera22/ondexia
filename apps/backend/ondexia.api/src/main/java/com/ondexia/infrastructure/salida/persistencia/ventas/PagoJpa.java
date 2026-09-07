package com.ondexia.infrastructure.salida.persistencia.ventas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "pago")
public class PagoJpa {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_id", nullable = false, updatable = false)
    private DocumentoVentaJpa documento;

    @Column(name = "forma", nullable = false, length = 20)
    private String forma;

    @Column(name = "monto", nullable = false, precision = 18, scale = 6)
    private BigDecimal monto;

    @Column(name = "referencia", length = 100)
    private String referencia;

    protected PagoJpa() {
    }

    public PagoJpa(UUID id, UUID empresaId, String forma, BigDecimal monto, String referencia) {
        this.id = id;
        this.empresaId = empresaId;
        this.forma = forma;
        this.monto = monto;
        this.referencia = referencia;
    }

    void asignarA(DocumentoVentaJpa documento) {
        this.documento = documento;
    }

    public String getForma() {
        return forma;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public String getReferencia() {
        return referencia;
    }

    @Override
    public final boolean equals(Object otro) {
        return otro instanceof PagoJpa fila && id != null && id.equals(fila.id);
    }

    @Override
    public final int hashCode() {
        return PagoJpa.class.hashCode();
    }
}
