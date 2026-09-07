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
@Table(name = "documento_venta_detalle")
public class DetalleVentaJpa {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_id", nullable = false, updatable = false)
    private DocumentoVentaJpa documento;

    @Column(name = "orden", nullable = false)
    private int orden;

    @Column(name = "producto_id", nullable = false)
    private UUID productoId;

    @Column(name = "codigo", nullable = false, length = 30)
    private String codigo;

    @Column(name = "descripcion", nullable = false, length = 300)
    private String descripcion;

    @Column(name = "unidad_medida", nullable = false, length = 5)
    private String unidadMedida;

    @Column(name = "cantidad", nullable = false, precision = 18, scale = 6)
    private BigDecimal cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 18, scale = 6)
    private BigDecimal precioUnitario;

    @Column(name = "valor_unitario", nullable = false, precision = 18, scale = 6)
    private BigDecimal valorUnitario;

    @Column(name = "descuento", nullable = false, precision = 18, scale = 6)
    private BigDecimal descuento;

    @Column(name = "afectacion_igv", nullable = false, length = 2)
    private String afectacionIgv;

    @Column(name = "valor_venta", nullable = false, precision = 18, scale = 6)
    private BigDecimal valorVenta;

    @Column(name = "igv", nullable = false, precision = 18, scale = 6)
    private BigDecimal igv;

    @Column(name = "total", nullable = false, precision = 18, scale = 6)
    private BigDecimal total;

    @Column(name = "descarga_existencias", nullable = false)
    private boolean descargaExistencias;

    protected DetalleVentaJpa() {
    }

    public DetalleVentaJpa(UUID id, UUID empresaId, int orden, UUID productoId, String codigo,
            String descripcion, String unidadMedida, BigDecimal cantidad, BigDecimal precioUnitario,
            BigDecimal valorUnitario, BigDecimal descuento, String afectacionIgv,
            BigDecimal valorVenta, BigDecimal igv, BigDecimal total, boolean descargaExistencias) {
        this.id = id;
        this.empresaId = empresaId;
        this.orden = orden;
        this.productoId = productoId;
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.unidadMedida = unidadMedida;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.valorUnitario = valorUnitario;
        this.descuento = descuento;
        this.afectacionIgv = afectacionIgv;
        this.valorVenta = valorVenta;
        this.igv = igv;
        this.total = total;
        this.descargaExistencias = descargaExistencias;
    }

    void asignarA(DocumentoVentaJpa documento) {
        this.documento = documento;
    }

    public int getOrden() {
        return orden;
    }

    public UUID getProductoId() {
        return productoId;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public BigDecimal getValorUnitario() {
        return valorUnitario;
    }

    public BigDecimal getDescuento() {
        return descuento;
    }

    public String getAfectacionIgv() {
        return afectacionIgv;
    }

    public BigDecimal getValorVenta() {
        return valorVenta;
    }

    public BigDecimal getIgv() {
        return igv;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public boolean isDescargaExistencias() {
        return descargaExistencias;
    }

    @Override
    public final boolean equals(Object otro) {
        return otro instanceof DetalleVentaJpa fila && id != null && id.equals(fila.id);
    }

    @Override
    public final int hashCode() {
        return DetalleVentaJpa.class.hashCode();
    }
}
