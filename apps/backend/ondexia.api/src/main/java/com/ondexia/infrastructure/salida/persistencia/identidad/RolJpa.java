package com.ondexia.infrastructure.salida.persistencia.identidad;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Fila de {@code rol}. {@code cuentaId} nulo = rol predefinido del sistema. */
@Entity
@Table(name = "rol")
public class RolJpa extends EntidadJpaBase {

    @Column(name = "cuenta_id", updatable = false)
    private UUID cuentaId;

    @Column(name = "codigo", nullable = false, length = 60)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "descripcion", length = 300)
    private String descripcion;

    /**
     * Perezosa a propósito: el camino caliente —resolver si alguien puede hacer
     * algo— no pasa por aquí, sino por una consulta que devuelve solo los
     * códigos. Con {@code EAGER}, cualquier consulta que tocara un rol traería
     * sus 200 permisos.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "rol_permiso",
            joinColumns = @JoinColumn(name = "rol_id"),
            inverseJoinColumns = @JoinColumn(name = "permiso_id"))
    private Set<PermisoJpa> permisos = new HashSet<>();

    protected RolJpa() {
    }

    public RolJpa(UUID id, UUID cuentaId, String codigo, String nombre, String descripcion) {
        this.id = id;
        this.cuentaId = cuentaId;
        this.codigo = codigo;
        renombrar(nombre, descripcion);
    }

    public final void renombrar(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    /**
     * Deja el rol exactamente con estos permisos.
     *
     * <p>Se muta la colección existente en vez de asignar una nueva: Hibernate
     * sigue la instancia que él gestiona, y sustituirla por otra le hace borrar
     * todas las filas de {@code rol_permiso} e insertarlas de vuelta en cada
     * guardado, aunque no haya cambiado nada.
     */
    public void reemplazarPermisos(Set<PermisoJpa> nuevos) {
        permisos.clear();
        permisos.addAll(nuevos);
    }

    public UUID getCuentaId() {
        return cuentaId;
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

    public Set<PermisoJpa> getPermisos() {
        return permisos;
    }
}
