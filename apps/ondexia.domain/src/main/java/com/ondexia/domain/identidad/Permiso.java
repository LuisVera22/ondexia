package com.ondexia.domain.identidad;

import com.ondexia.domain.comun.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

/**
 * Una capacidad concreta del sistema, identificada por el par
 * {@code (modulo, accion)}.
 *
 * <p><strong>El permiso es por accion, no por modulo.</strong> «Acceso a
 * ventas» no es un permiso util: registrar una venta, aprobarla y anularla son
 * tres capacidades con tres consecuencias distintas, y quien puede hacer la
 * primera casi nunca debe poder hacer la tercera. Anular un comprobante
 * emitido tiene efecto tributario.
 *
 * <p>Catalogo <strong>global</strong>, no por cuenta: la lista de capacidades
 * la define el sistema, no el cliente. Lo que el cliente compone son sus roles
 * ({@link Rol}), eligiendo de esta lista.
 *
 * <p>Con ~50 submodulos por 3-5 acciones salen unos 200 permisos. Ese numero es
 * la razon por la que no viajan en el token: no caben en una cabecera que va en
 * cada llamada.
 */
@Entity
@Table(name = "permiso")
public class Permiso extends EntidadBase {

    /**
     * Forma canonica {@code modulo.submodulo:accion}, por ejemplo
     * {@code almacen.producto:registrar}.
     *
     * <p>Se guarda tambien descompuesto en {@code modulo} y {@code accion} para
     * poder consultar por cualquiera de los dos sin recurrir a {@code LIKE}
     * sobre el codigo, que no usa indice y ademas convierte un separador en
     * parte del contrato de la consulta.
     */
    @NotBlank
    @Column(name = "codigo", nullable = false, unique = true, length = 100)
    private String codigo;

    @NotBlank
    @Column(name = "modulo", nullable = false, length = 60)
    private String modulo;

    @NotBlank
    @Column(name = "accion", nullable = false, length = 40)
    private String accion;

    @Column(name = "descripcion", length = 300)
    private String descripcion;

    protected Permiso() {
        // Requerido por JPA.
    }

    public Permiso(String modulo, String accion, String descripcion) {
        this.modulo = modulo;
        this.accion = accion;
        this.codigo = componerCodigo(modulo, accion);
        this.descripcion = descripcion;
    }

    /** Fuente unica de la forma del codigo. Nadie mas concatena el separador. */
    public static String componerCodigo(String modulo, String accion) {
        return modulo + ":" + accion;
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
