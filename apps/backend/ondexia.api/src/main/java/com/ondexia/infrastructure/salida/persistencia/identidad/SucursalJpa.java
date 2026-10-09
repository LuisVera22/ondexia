package com.ondexia.infrastructure.salida.persistencia.identidad;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Fila de {@code sucursal}. */
@Entity
@Table(name = "sucursal")
public class SucursalJpa extends EntidadJpaBase {

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    /** Código del establecimiento anexo ante SUNAT, no un número interno. */
    @Column(name = "codigo", nullable = false, length = 10)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "direccion", nullable = false, length = 400)
    private String direccion;

    @Column(name = "ubigeo", length = 6)
    private String ubigeo;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected SucursalJpa() {
    }

    public SucursalJpa(UUID id, UUID empresaId, String codigo, String nombre, String direccion,
            String ubigeo, boolean activo) {
        this.id = id;
        this.empresaId = empresaId;
        this.codigo = codigo;
        actualizarDesde(nombre, direccion, ubigeo, activo);
    }

    public final void actualizarDesde(String nombre, String direccion, String ubigeo,
            boolean activo) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.ubigeo = ubigeo;
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

    public String getDireccion() {
        return direccion;
    }

    public String getUbigeo() {
        return ubigeo;
    }

    public boolean isActivo() {
        return activo;
    }
}
