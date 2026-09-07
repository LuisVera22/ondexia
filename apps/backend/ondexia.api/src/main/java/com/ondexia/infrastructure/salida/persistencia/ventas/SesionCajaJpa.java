package com.ondexia.infrastructure.salida.persistencia.ventas;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sesion_caja")
public class SesionCajaJpa extends EntidadJpaBase {

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Column(name = "caja_id", nullable = false, updatable = false)
    private UUID cajaId;

    @Column(name = "abierta_por", nullable = false, updatable = false)
    private UUID abiertaPor;

    @Column(name = "abierta_en", nullable = false, updatable = false)
    private Instant abiertaEn;

    @Column(name = "monto_inicial", nullable = false, updatable = false, precision = 18, scale = 6)
    private BigDecimal montoInicial;

    @Column(name = "cerrada_por")
    private UUID cerradaPor;

    @Column(name = "cerrada_en")
    private Instant cerradaEn;

    @Column(name = "estado", nullable = false, length = 10)
    private String estado;

    /** Por forma de pago, con la clave del enumerado. jsonb en la base. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "declarado", nullable = false)
    private Map<String, BigDecimal> declarado;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculado", nullable = false)
    private Map<String, BigDecimal> calculado;

    protected SesionCajaJpa() {
    }

    public SesionCajaJpa(UUID id, UUID empresaId, UUID cajaId, UUID abiertaPor, Instant abiertaEn,
            BigDecimal montoInicial) {
        this.id = id;
        this.empresaId = empresaId;
        this.cajaId = cajaId;
        this.abiertaPor = abiertaPor;
        this.abiertaEn = abiertaEn;
        this.montoInicial = montoInicial;
        this.estado = "ABIERTA";
        this.declarado = Map.of();
        this.calculado = Map.of();
    }

    public final void cerrarDesde(UUID cerradaPor, Instant cerradaEn, String estado,
            Map<String, BigDecimal> declarado, Map<String, BigDecimal> calculado) {
        this.cerradaPor = cerradaPor;
        this.cerradaEn = cerradaEn;
        this.estado = estado;
        this.declarado = declarado;
        this.calculado = calculado;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public UUID getCajaId() {
        return cajaId;
    }

    public UUID getAbiertaPor() {
        return abiertaPor;
    }

    public Instant getAbiertaEn() {
        return abiertaEn;
    }

    public BigDecimal getMontoInicial() {
        return montoInicial;
    }

    public UUID getCerradaPor() {
        return cerradaPor;
    }

    public Instant getCerradaEn() {
        return cerradaEn;
    }

    public String getEstado() {
        return estado;
    }

    public Map<String, BigDecimal> getDeclarado() {
        return declarado;
    }

    public Map<String, BigDecimal> getCalculado() {
        return calculado;
    }
}
