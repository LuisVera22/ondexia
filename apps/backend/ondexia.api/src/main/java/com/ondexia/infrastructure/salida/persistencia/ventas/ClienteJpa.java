package com.ondexia.infrastructure.salida.persistencia.ventas;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cliente")
public class ClienteJpa extends EntidadJpaBase {

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Column(name = "tipo_documento", nullable = false, updatable = false, length = 1)
    private String tipoDocumento;

    @Column(name = "numero_documento", nullable = false, updatable = false, length = 15)
    private String numeroDocumento;

    @Column(name = "nombre", nullable = false, length = 300)
    private String nombre;

    @Column(name = "direccion", length = 300)
    private String direccion;

    @Column(name = "correo", length = 200)
    private String correo;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "verificado_en")
    private Instant verificadoEn;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected ClienteJpa() {
    }

    public ClienteJpa(UUID id, UUID empresaId, String tipoDocumento, String numeroDocumento) {
        this.id = id;
        this.empresaId = empresaId;
        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
    }

    public final void actualizarDesde(String nombre, String direccion, String correo,
            String telefono, Instant verificadoEn, boolean activo) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.correo = correo;
        this.telefono = telefono;
        this.verificadoEn = verificadoEn;
        this.activo = activo;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getCorreo() {
        return correo;
    }

    public String getTelefono() {
        return telefono;
    }

    public Instant getVerificadoEn() {
        return verificadoEn;
    }

    public boolean isActivo() {
        return activo;
    }
}
