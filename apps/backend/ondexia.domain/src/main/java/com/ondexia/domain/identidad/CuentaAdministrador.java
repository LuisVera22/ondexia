package com.ondexia.domain.identidad;

import com.ondexia.domain.comun.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Quien gobierna la cuenta: factura, crea empresas, asigna usuarios y gestiona
 * roles.
 *
 * <p><strong>Por que no es un rol mas de la matriz.</strong> Tres razones, y
 * cada una por si sola basta:
 *
 * <ol>
 *   <li>Existe <em>antes</em> que cualquier empresa. La matriz de permisos se
 *       evalua sobre el par (usuario, empresa), y al crear la cuenta todavia no
 *       hay ninguna empresa contra la que evaluar.</li>
 *   <li>Gobierna la facturacion de la suscripcion, que no es un modulo del
 *       sistema y por tanto no tiene entrada {@code (modulo, accion)}.</li>
 *   <li>Necesita el invariante «no puede quedar vacia», que la matriz no puede
 *       expresar: quitar el ultimo permiso de un rol es legitimo; quitar el
 *       ultimo administrador deja la cuenta sin quien la gobierne, y sin nadie
 *       capaz de arreglarlo desde dentro.</li>
 * </ol>
 *
 * <p>Ese invariante se defiende en la base con un disparador, no solo en el
 * servicio. Dos administradores renunciando a la vez en transacciones paralelas
 * pasarian ambos la comprobacion en Java —cada uno ve al otro todavia
 * presente— y la cuenta quedaria huerfana.
 */
@Entity
@Table(name = "cuenta_administrador")
public class CuentaAdministrador extends EntidadBase {

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private UUID cuentaId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    protected CuentaAdministrador() {
        // Requerido por JPA.
    }

    public CuentaAdministrador(UUID cuentaId, UUID usuarioId) {
        this.cuentaId = cuentaId;
        this.usuarioId = usuarioId;
    }

    public UUID getCuentaId() {
        return cuentaId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }
}
