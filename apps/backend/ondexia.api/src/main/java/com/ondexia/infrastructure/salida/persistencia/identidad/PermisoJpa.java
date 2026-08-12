package com.ondexia.infrastructure.salida.persistencia.identidad;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Fila de {@code permiso}. Catálogo global, no por cuenta. */
@Entity
@Table(name = "permiso")
public class PermisoJpa extends EntidadJpaBase {

    @Column(name = "codigo", nullable = false, unique = true, length = 100)
    private String codigo;

    @Column(name = "modulo", nullable = false, length = 60)
    private String modulo;

    @Column(name = "accion", nullable = false, length = 40)
    private String accion;

    @Column(name = "descripcion", length = 300)
    private String descripcion;

    protected PermisoJpa() {
    }

    public PermisoJpa(UUID id, String modulo, String accion, String descripcion) {
        this.id = id;
        this.modulo = modulo;
        this.accion = accion;
        this.codigo = modulo + ":" + accion;
        this.descripcion = descripcion;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getModulo() {
        return modulo;
    }

    public String getAccion() {
        return accion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
