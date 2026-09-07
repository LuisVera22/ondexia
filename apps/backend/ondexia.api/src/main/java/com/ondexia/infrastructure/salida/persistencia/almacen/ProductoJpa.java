package com.ondexia.infrastructure.salida.persistencia.almacen;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "producto")
public class ProductoJpa extends EntidadJpaBase {

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Column(name = "codigo", nullable = false, updatable = false, length = 30)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 300)
    private String nombre;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "unidad_medida", nullable = false, length = 5)
    private String unidadMedida;

    @Column(name = "afectacion_igv", nullable = false, length = 2)
    private String afectacionIgv;

    @Column(name = "precio_lista", nullable = false, precision = 18, scale = 6)
    private BigDecimal precioLista;

    @Column(name = "controla_stock", nullable = false)
    private boolean controlaStock;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected ProductoJpa() {
    }

    public ProductoJpa(UUID id, UUID empresaId, String codigo) {
        this.id = id;
        this.empresaId = empresaId;
        this.codigo = codigo;
    }

    public final void actualizarDesde(String nombre, String descripcion, String unidadMedida,
            String afectacionIgv, BigDecimal precioLista, boolean controlaStock, boolean activo) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.unidadMedida = unidadMedida;
        this.afectacionIgv = afectacionIgv;
        this.precioLista = precioLista;
        this.controlaStock = controlaStock;
        this.activo = activo;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public String getAfectacionIgv() {
        return afectacionIgv;
    }

    public BigDecimal getPrecioLista() {
        return precioLista;
    }

    public boolean isControlaStock() {
        return controlaStock;
    }

    public boolean isActivo() {
        return activo;
    }
}
