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
    private String apellido;
    private String telefono;
    private boolean activo;

    public Usuario(UUID id, UUID cuentaId, String email, String nombre, String apellido) {
        this.id = Objects.requireNonNull(id, "id");
        this.cuentaId = Objects.requireNonNull(cuentaId, "cuentaId");
        this.email = email;
        this.nombre = nombre;
        this.apellido = apellido;
        this.activo = true;
    }

    public Usuario(UUID id, UUID cuentaId, String cognitoSub, String email, String nombre,
            String apellido, String telefono, boolean activo) {
        this.id = id;
        this.cuentaId = cuentaId;
        this.cognitoSub = cognitoSub;
        this.email = email;
        this.nombre = nombre;
        this.apellido = apellido;
        this.telefono = telefono;
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

    /**
     * Nulo en las filas anteriores a la V11, que conservan el nombre completo en
     * {@link #nombre()}. No se rellenaron por migración: partir «María del
     * Carmen Rojas» por el primer espacio inventa un dato que parece correcto,
     * y eso es más difícil de detectar que uno ausente.
     */
    public String apellido() {
        return apellido;
    }

    /**
     * Como se muestra la persona en cualquier pantalla.
     *
     * <p>Existe aquí y no en cada DTO para que unir las dos columnas sea una
     * sola decisión. Con {@code apellido} nulo devuelve el nombre tal cual, que
     * es exactamente lo que se veía antes de partir el campo: las filas viejas
     * se siguen mostrando igual sin que nadie las toque.
     */
    public String nombreCompleto() {
        return apellido == null || apellido.isBlank() ? nombre : nombre + " " + apellido;
    }

    /**
     * Contacto, no credencial. Opcional y sin formato impuesto: conviven el
     * móvil de nueve dígitos, el fijo con área, el internacional y los anexos.
     */
    public String telefono() {
        return telefono;
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
     * Lo que cada persona edita de sí misma. No incluye el correo, y no es un
     * olvido: el correo es la credencial con la que se entra a Cognito, así que
     * cambiarlo aquí dejaría la fila apuntando a un buzón con el que ya no se
     * puede iniciar sesión. Tampoco incluye rol ni empresas — los define el
     * administrador de la cuenta.
     *
     * <p>El teléfono en blanco se guarda como nulo. Distinguir «vacío» de «sin
     * dato» en una columna opcional solo produce dos formas de escribir lo
     * mismo y consultas que se olvidan de una.
     */
    public void actualizarPerfil(String nombre, String apellido, String telefono) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.telefono = telefono == null || telefono.isBlank() ? null : telefono.trim();
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
