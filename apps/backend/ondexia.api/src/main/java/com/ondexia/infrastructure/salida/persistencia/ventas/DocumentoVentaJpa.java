package com.ondexia.infrastructure.salida.persistencia.ventas;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * El documento con sus líneas y pagos. Se inserta una vez y no se edita: el
 * disparador de la V19 lo garantiza aunque el código quisiera otra cosa.
 */
@Entity
@Table(name = "documento_venta")
public class DocumentoVentaJpa extends EntidadJpaBase {

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Column(name = "sucursal_id", nullable = false, updatable = false)
    private UUID sucursalId;

    @Column(name = "sesion_caja_id", nullable = false, updatable = false)
    private UUID sesionCajaId;

    @Column(name = "tipo_documento", nullable = false, updatable = false, length = 2)
    private String tipoDocumento;

    @Column(name = "fiscal", nullable = false, updatable = false)
    private boolean fiscal;

    @Column(name = "serie", nullable = false, updatable = false, length = 4)
    private String serie;

    @Column(name = "numero", nullable = false, updatable = false)
    private long numero;

    @Column(name = "cliente_id", updatable = false)
    private UUID clienteId;

    @Column(name = "fecha_emision", nullable = false, updatable = false)
    private LocalDate fechaEmision;

    @Column(name = "emitido_en", nullable = false, updatable = false)
    private Instant emitidoEn;

    @Column(name = "emitido_por", nullable = false, updatable = false)
    private UUID emitidoPor;

    @Column(name = "moneda", nullable = false, updatable = false, length = 3)
    private String moneda = "PEN";

    @Column(name = "total_gravado", nullable = false, updatable = false, precision = 18, scale = 6)
    private BigDecimal totalGravado;

    @Column(name = "total_exonerado", nullable = false, updatable = false, precision = 18, scale = 6)
    private BigDecimal totalExonerado;

    @Column(name = "total_inafecto", nullable = false, updatable = false, precision = 18, scale = 6)
    private BigDecimal totalInafecto;

    @Column(name = "total_descuento", nullable = false, updatable = false, precision = 18, scale = 6)
    private BigDecimal totalDescuento;

    @Column(name = "total_igv", nullable = false, updatable = false, precision = 18, scale = 6)
    private BigDecimal totalIgv;

    @Column(name = "total", nullable = false, updatable = false, precision = 18, scale = 6)
    private BigDecimal total;

    @Column(name = "observaciones", updatable = false)
    private String observaciones;

    @Column(name = "documento_origen_id", updatable = false)
    private UUID documentoOrigenId;

    /** Catálogo 09, solo en una nota de crédito. Lo exige documento_venta_motivo_coherente. */
    @Column(name = "motivo_nota", updatable = false, length = 2)
    private String motivoNota;

    // El documento al que este se refiere, copiado. Va entero o no va: lo
    // impone documento_venta_origen_completo.

    @Column(name = "origen_tipo", updatable = false, length = 2)
    private String origenTipo;

    @Column(name = "origen_serie", updatable = false, length = 4)
    private String origenSerie;

    @Column(name = "origen_numero", updatable = false)
    private Long origenNumero;

    @Column(name = "estado", nullable = false, length = 12)
    private String estado;

    @OneToMany(mappedBy = "documento", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("orden asc")
    private List<DetalleVentaJpa> lineas = new ArrayList<>();

    @OneToMany(mappedBy = "documento", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<PagoJpa> pagos = new ArrayList<>();

    protected DocumentoVentaJpa() {
    }

    public DocumentoVentaJpa(UUID id, UUID empresaId, UUID sucursalId, UUID sesionCajaId,
            String tipoDocumento, boolean fiscal, String serie, long numero, UUID clienteId,
            LocalDate fechaEmision, Instant emitidoEn, UUID emitidoPor, BigDecimal totalGravado,
            BigDecimal totalExonerado, BigDecimal totalInafecto, BigDecimal totalDescuento,
            BigDecimal totalIgv, BigDecimal total, String observaciones, UUID documentoOrigenId,
            String motivoNota, String origenTipo, String origenSerie, Long origenNumero,
            String estado) {
        this.id = id;
        this.empresaId = empresaId;
        this.sucursalId = sucursalId;
        this.sesionCajaId = sesionCajaId;
        this.tipoDocumento = tipoDocumento;
        this.fiscal = fiscal;
        this.serie = serie;
        this.numero = numero;
        this.clienteId = clienteId;
        this.fechaEmision = fechaEmision;
        this.emitidoEn = emitidoEn;
        this.emitidoPor = emitidoPor;
        this.totalGravado = totalGravado;
        this.totalExonerado = totalExonerado;
        this.totalInafecto = totalInafecto;
        this.totalDescuento = totalDescuento;
        this.totalIgv = totalIgv;
        this.total = total;
        this.observaciones = observaciones;
        this.documentoOrigenId = documentoOrigenId;
        this.motivoNota = motivoNota;
        this.origenTipo = origenTipo;
        this.origenSerie = origenSerie;
        this.origenNumero = origenNumero;
        this.estado = estado;
    }

    public void agregarLinea(DetalleVentaJpa linea) {
        linea.asignarA(this);
        lineas.add(linea);
    }

    public void agregarPago(PagoJpa pago) {
        pago.asignarA(this);
        pagos.add(pago);
    }

    public void cambiarEstado(String estado) {
        this.estado = estado;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public UUID getSucursalId() {
        return sucursalId;
    }

    public UUID getSesionCajaId() {
        return sesionCajaId;
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

    public UUID getClienteId() {
        return clienteId;
    }

    public LocalDate getFechaEmision() {
        return fechaEmision;
    }

    public Instant getEmitidoEn() {
        return emitidoEn;
    }

    public UUID getEmitidoPor() {
        return emitidoPor;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public UUID getDocumentoOrigenId() {
        return documentoOrigenId;
    }

    public String getMotivoNota() {
        return motivoNota;
    }

    public String getOrigenTipo() {
        return origenTipo;
    }

    public String getOrigenSerie() {
        return origenSerie;
    }

    public Long getOrigenNumero() {
        return origenNumero;
    }

    public String getEstado() {
        return estado;
    }

    public List<DetalleVentaJpa> getLineas() {
        return lineas;
    }

    public List<PagoJpa> getPagos() {
        return pagos;
    }
}
