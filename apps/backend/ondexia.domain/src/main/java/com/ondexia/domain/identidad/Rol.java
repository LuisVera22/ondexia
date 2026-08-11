package com.ondexia.domain.identidad;

import com.ondexia.domain.comun.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Conjunto de permisos con nombre, que se asigna a un usuario sobre una
 * empresa.
 *
 * <p><strong>Roles del sistema y roles de la cuenta.</strong> Cuando
 * {@code cuentaId} es {@code null} el rol es predefinido: lo define Ondexia,
 * lo ven todas las cuentas y ninguna puede modificarlo. Esa inmutabilidad es
 * intencionada — si un cliente pudiera editar «Vendedor», el significado de la
 * palabra dejaria de ser el mismo entre clientes y el soporte se volveria
 * adivinanza.
 *
 * <p>Lo que si puede hacer un cliente es <strong>duplicar</strong> un rol
 * predefinido y ajustar la copia, que queda con su {@code cuentaId}.
 */
@Entity
@Table(name = "rol")
public class Rol extends EntidadBase {

    /** {@code null} = rol predefinido del sistema, comun a todas las cuentas. */
    @Column(name = "cuenta_id", updatable = false)
    private UUID cuentaId;

    @NotBlank
    @Column(name = "codigo", nullable = false, length = 60)
    private String codigo;

    @NotBlank
    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "descripcion", length = 300)
    private String descripcion;

    /**
     * Carga perezosa a proposito.
     *
     * <p>El camino caliente —resolver si un usuario puede hacer una accion— no
     * pasa por aqui: usa una consulta con proyeccion que devuelve solo los
     * codigos ({@code PermisoRepository#findCodigosByRolId}). Esta asociacion
     * existe para la pantalla de administracion de roles, que se abre pocas
     * veces al dia.
     *
     * <p>Cargarla con {@code EAGER} traeria 200 filas de permiso en cada
     * consulta que toque un rol, incluidas las que solo quieren su nombre.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "rol_permiso",
            joinColumns = @JoinColumn(name = "rol_id"),
            inverseJoinColumns = @JoinColumn(name = "permiso_id"))
    private Set<Permiso> permisos = new HashSet<>();

    protected Rol() {
        // Requerido por JPA.
    }

    public Rol(UUID cuentaId, String codigo, String nombre, String descripcion) {
        this.cuentaId = cuentaId;
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
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

    public Set<Permiso> getPermisos() {
        return Set.copyOf(permisos);
    }

    public boolean esDelSistema() {
        return cuentaId == null;
    }

    private void exigirModificable() {
        if (esDelSistema()) {
            throw new IllegalStateException(
                    "El rol predefinido '" + codigo + "' no se modifica: duplicalo y ajusta la copia");
        }
    }

    public void renombrar(String nombre, String descripcion) {
        exigirModificable();
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    /**
     * Reemplaza el conjunto completo de permisos.
     *
     * <p>Se reemplaza entero y no se agregan o quitan de uno en uno porque la
     * pantalla que lo usa presenta una matriz de casillas: el usuario ve el
     * estado final y lo envia. Aplicar diferencias desde el cliente abriria la
     * puerta a perder una casilla desmarcada si dos administradores editan a la
     * vez.
     *
     * <p>Quien llame a esto debe incrementar despues
     * {@link Cuenta#invalidarCachePermisos()}, o el cambio no se vera hasta que
     * los contenedores de Lambda se reciclen.
     */
    public void reemplazarPermisos(Set<Permiso> nuevos) {
        exigirModificable();
        this.permisos = new HashSet<>(nuevos);
    }

    /** Copia este rol como rol propio de una cuenta, ya modificable. */
    public Rol duplicarPara(UUID cuentaId, String codigo, String nombre) {
        Rol copia = new Rol(cuentaId, codigo, nombre, this.descripcion);
        copia.permisos = new HashSet<>(this.permisos);
        return copia;
    }
}
