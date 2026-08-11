package com.ondexia.domain.identidad;

import com.ondexia.domain.comun.error.Conflicto;
import java.util.Objects;
import java.util.UUID;

/**
 * Persona que usa el sistema. Pertenece a una cuenta.
 *
 * <p><strong>Esta es la fuente de verdad, no Cognito.</strong> Cognito autentica
 * y nada más; pertenencia, estado y permisos viven aquí. Esa separación es lo
 * que hace reversible DT-05: salir de Cognito cuesta restablecer contraseñas,
 * no reconstruir el modelo de usuarios.
 *
 * <p>No hay contraseña en esta clase, y no debe haberla nunca.
 */
public class Usuario {

    private final UUID id;
    private final UUID cuentaId;
    private String cognitoSub;
    private String email;
    private String nombre;
    private boolean activo;

    public Usuario(UUID id, UUID cuentaId, String email, String nombre) {
        this.id = Objects.requireNonNull(id, "id");
        this.cuentaId = Objects.requireNonNull(cuentaId, "cuentaId");
        this.email = email;
        this.nombre = nombre;
        this.activo = true;
    }

    public Usuario(UUID id, UUID cuentaId, String cognitoSub, String email, String nombre,
            boolean activo) {
        this.id = id;
        this.cuentaId = cuentaId;
        this.cognitoSub = cognitoSub;
        this.email = email;
        this.nombre = nombre;
        this.activo = activo;
    }

    public UUID id() {
        return id;
    }

    public UUID cuentaId() {
        return cuentaId;
    }

    /**
     * El {@code sub} del token: la única referencia al proveedor de identidad en
     * todo el modelo. Se usa el {@code sub} y no el correo porque el correo
     * cambia y el {@code sub} es inmutable.
     *
     * <p>Admite nulo: el administrador puede dar de alta a alguien antes de que
     * complete su registro. Se vincula en el primer acceso.
     */
    public String cognitoSub() {
        return cognitoSub;
    }

    public String email() {
        return email;
    }

    public String nombre() {
        return nombre;
    }

    public boolean estaActivo() {
        return activo;
    }

    /**
     * Vincula la identidad en el primer acceso. Solo una vez: reasignar el
     * {@code sub} de un usuario ya vinculado haría que otra persona heredara su
     * historial de auditoría, que es lo que la auditoría existe para impedir.
     */
    public void vincularIdentidad(String cognitoSub) {
        if (this.cognitoSub != null) {
            throw new Conflicto("identidad_ya_vinculada",
                    "El usuario ya está vinculado a una identidad de Cognito.");
        }
        this.cognitoSub = cognitoSub;
    }

    public void renombrar(String nombre) {
        this.nombre = nombre;
    }

    /**
     * Un usuario desactivado no entra aunque su token siga vigente. Es la única
     * forma de revocar el acceso antes de que caduque el JWT — Cognito no
     * revoca un token de acceso ya emitido.
     */
    public void desactivar() {
        this.activo = false;
    }

    public void activar() {
        this.activo = true;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Usuario otro2 && id.equals(otro2.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
