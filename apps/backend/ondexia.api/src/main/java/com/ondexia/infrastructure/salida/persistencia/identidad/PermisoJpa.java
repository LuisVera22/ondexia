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

    /** MODULO, SUBMODULO o FUNCION. La autorización exige los tres (V6). */
    @Column(name = "nivel", nullable = false, length = 10)
    private String nivel;

    /** Como se muestra en la matriz. Vive aquí y no en un mapa del frontend. */
    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "descripcion", length = 300)
    private String descripcion;

    protected PermisoJpa() {
    }

    public PermisoJpa(UUID id, String nivel, String modulo, String accion, String nombre,
            String descripcion) {
        this.id = id;
        this.nivel = nivel;
        this.modulo = modulo;
        this.accion = accion;
        this.codigo = modulo + ":" + accion;
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public String getNivel() {
        return nivel;
    }

    public String getNombre() {
        return nombre;
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
