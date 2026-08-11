package com.ondexia.domain.identidad;

import com.ondexia.domain.comun.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * Establecimiento anexo de una empresa.
 *
 * <p>No es organizacion cosmetica: la sucursal decide <strong>que serie</strong>
 * lleva el comprobante y <strong>que almacen</strong> se descarga. Por eso el
 * alcance de un usuario puede acotarse a una sucursal concreta — ver
 * {@link UsuarioEmpresa}.
 *
 * <p>El {@code codigo} es el del establecimiento anexo ante SUNAT, no un
 * numero interno. Aparece en el comprobante y SUNAT lo valida contra su
 * registro.
 */
@Entity
@Table(name = "sucursal")
public class Sucursal extends EntidadBase {

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @NotBlank
    @Column(name = "codigo", nullable = false, length = 10)
    private String codigo;

    @NotBlank
    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @NotBlank
    @Column(name = "direccion", nullable = false, length = 400)
    private String direccion;

    @Pattern(regexp = "\\d{6}", message = "El ubigeo debe tener 6 digitos")
    @Column(name = "ubigeo", length = 6)
    private String ubigeo;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected Sucursal() {
        // Requerido por JPA.
    }

    public Sucursal(UUID empresaId, String codigo, String nombre, String direccion) {
        this.empresaId = empresaId;
        this.codigo = codigo;
        this.nombre = nombre;
        this.direccion = direccion;
        this.activo = true;
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

    public boolean estaActiva() {
        return activo;
    }

    public void actualizar(String nombre, String direccion, String ubigeo) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.ubigeo = ubigeo;
    }

    public void desactivar() {
        this.activo = false;
    }
}
