package com.ondexia.infrastructure.salida.persistencia.almacen;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "producto_local")
public class DisponibilidadJpa extends EntidadJpaBase {

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Column(name = "producto_id", nullable = false, updatable = false)
    private UUID productoId;

    @Column(name = "sucursal_id", nullable = false, updatable = false)
    private UUID sucursalId;

    @Column(name = "disponible", nullable = false)
    private boolean disponible;

    @Column(name = "precio", precision = 18, scale = 6)
    private BigDecimal precio;

    protected DisponibilidadJpa() {
    }

    public DisponibilidadJpa(UUID id, UUID empresaId, UUID productoId, UUID sucursalId) {
        this.id = id;
        this.empresaId = empresaId;
        this.productoId = productoId;
        this.sucursalId = sucursalId;
    }

    public final void fijar(boolean disponible, BigDecimal precio) {
        this.disponible = disponible;
        this.precio = precio;
    }

    public UUID getProductoId() {
        return productoId;
    }

    public UUID getSucursalId() {
        return sucursalId;
    }

    public boolean isDisponible() {
        return disponible;
    }

    public BigDecimal getPrecio() {
        return precio;
    }
}
