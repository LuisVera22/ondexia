package com.ondexia.infrastructure.salida.persistencia.identidad;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Fila de {@code usuario}. Nunca guarda contraseña: de eso se ocupa Cognito. */
@Entity
@Table(name = "usuario")
public class UsuarioJpa extends EntidadJpaBase {

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private UUID cuentaId;

    /** Nulo mientras la persona no complete su registro en Cognito. */
    @Column(name = "cognito_sub", unique = true, length = 64)
    private String cognitoSub;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected UsuarioJpa() {
    }

    public UsuarioJpa(UUID id, UUID cuentaId, String cognitoSub, String email, String nombre,
            boolean activo) {
        this.id = id;
        this.cuentaId = cuentaId;
        actualizarDesde(cognitoSub, email, nombre, activo);
    }

    public final void actualizarDesde(String cognitoSub, String email, String nombre,
            boolean activo) {
        this.cognitoSub = cognitoSub;
        this.email = email;
        this.nombre = nombre;
        this.activo = activo;
    }

    public UUID getCuentaId() {
        return cuentaId;
    }

    public String getCognitoSub() {
        return cognitoSub;
    }

    public String getEmail() {
        return email;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isActivo() {
        return activo;
    }
}
