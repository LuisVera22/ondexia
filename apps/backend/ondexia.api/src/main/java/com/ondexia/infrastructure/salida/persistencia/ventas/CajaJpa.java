package com.ondexia.infrastructure.salida.persistencia.ventas;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "caja")
public class CajaJpa extends EntidadJpaBase {

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Column(name = "sucursal_id", nullable = false, updatable = false)
    private UUID sucursalId;

    @Column(name = "codigo", nullable = false, updatable = false, length = 20)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected CajaJpa() {
    }

    public CajaJpa(UUID id, UUID empresaId, UUID sucursalId, String codigo, String nombre,
            boolean activo) {
        this.id = id;
        this.empresaId = empresaId;
        this.sucursalId = sucursalId;
        this.codigo = codigo;
        actualizarDesde(nombre, activo);
    }

    public final void actualizarDesde(String nombre, boolean activo) {
        this.nombre = nombre;
        this.activo = activo;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public UUID getSucursalId() {
        return sucursalId;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isActivo() {
        return activo;
    }
}
