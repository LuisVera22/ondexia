package com.ondexia.domain.identidad;

import com.ondexia.domain.comun.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Persona que usa el sistema. Pertenece a una cuenta.
 *
 * <p><strong>Esta tabla es la fuente de verdad, no Cognito.</strong> Cognito
 * autentica y nada mas; la pertenencia a cuenta, el estado y los permisos viven
 * aqui. Esa separacion es lo que hace reversible la decision DT-05: salir de
 * Cognito cuesta restablecer contrasenas, no reconstruir el modelo de usuarios.
 *
 * <p>No hay contrasena en esta tabla, y no debe haberla nunca. Guardar hashes
 * en dos sitios es garantizar que uno de los dos quede obsoleto.
 */
@Entity
@Table(name = "usuario")
public class Usuario extends EntidadBase {

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private UUID cuentaId;

    /**
     * El {@code sub} del token de Cognito: la <strong>unica</strong> referencia
     * al proveedor de identidad en todo el modelo.
     *
     * <p>Se usa el {@code sub} y no el correo porque el correo cambia y el
     * {@code sub} es inmutable. Un usuario que actualiza su direccion no puede
     * convertirse en otro usuario distinto.
     *
     * <p>Admite nulo: el administrador de la cuenta puede dar de alta a alguien
     * antes de que esa persona complete su registro en Cognito. Queda vinculado
     * en el primer inicio de sesion.
     */
    @Column(name = "cognito_sub", unique = true, length = 64)
    private String cognitoSub;

    @Email
    @NotBlank
    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @NotBlank
    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    /**
     * Un usuario desactivado no entra, aunque su token siga vigente.
     *
     * <p>Se comprueba en cada peticion. Es la unica forma de revocar el acceso
     * antes de que caduque el JWT — Cognito no ofrece revocacion inmediata del
     * token de acceso.
     */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected Usuario() {
        // Requerido por JPA.
    }

    public Usuario(UUID cuentaId, String email, String nombre) {
        this.cuentaId = cuentaId;
        this.email = email;
        this.nombre = nombre;
        this.activo = true;
    }

    public UUID getCuentaId() {
        return cuentaId;
    }

    public String getCognitoSub() {
        return cognitoSub;
    }

    /**
     * Vincula la identidad de Cognito en el primer inicio de sesion.
     *
     * <p>Solo se permite una vez. Reasignar el {@code sub} de un usuario ya
     * vinculado significaria que otra persona hereda su historial de auditoria,
     * que es precisamente lo que la auditoria existe para impedir.
     */
    public void vincularIdentidad(String cognitoSub) {
        if (this.cognitoSub != null) {
            throw new IllegalStateException(
                    "El usuario " + getId() + " ya esta vinculado a una identidad de Cognito");
        }
        this.cognitoSub = cognitoSub;
    }

    public String getEmail() {
        return email;
    }

    public String getNombre() {
        return nombre;
    }

    public void renombrar(String nombre) {
        this.nombre = nombre;
    }

    public boolean estaActivo() {
        return activo;
    }

    public void desactivar() {
        this.activo = false;
    }

    public void activar() {
        this.activo = true;
    }
}
