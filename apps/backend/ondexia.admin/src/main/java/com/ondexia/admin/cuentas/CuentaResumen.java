package com.ondexia.admin.cuentas;

import java.time.Instant;
import java.util.UUID;

/**
 * Una cuenta cliente vista desde el panel, con su consumo frente a los límites.
 *
 * @param titular         el correo de quien abrió la cuenta. <strong>Es lo que
 *                        la identifica</strong>: {@code nombre} se repite
 * @param nombre          la razón social de su PRIMERA empresa, que es con lo
 *                        que el registro rellena {@code cuenta.nombre}. No es un
 *                        nombre de cuenta: una cuenta con tres empresas sigue
 *                        llamándose como la primera, y dos cuentas distintas
 *                        pueden llamarse igual
 * @param limiteEmpresas  {@code null} significa <strong>sin límite</strong>, no
 *                        cero. Es lo que necesita el plan negociable del doc 04
 *                        §2.2, y la pantalla debe pintarlo como «sin límite» y no
 *                        como «0 disponibles»
 * @param empresas        cuántas tiene ahora mismo
 * @param usuarios        usuarios ACTIVOS. Los desactivados no consumen plan:
 *                        cobrar por una persona que ya no entra seria cobrar por
 *                        una fila
 */
public record CuentaResumen(
        UUID id,
        String nombre,
        String titular,
        String planCodigo,
        String planNombre,
        String estadoSuscripcion,
        long empresas,
        Integer limiteEmpresas,
        long usuarios,
        Integer limiteUsuarios,
        Instant creadoEn) {

    /** Si ya no admite otra empresa. Con límite nulo nunca lo alcanza. */
    public boolean alcanzoLimiteDeEmpresas() {
        return limiteEmpresas != null && empresas >= limiteEmpresas;
    }

    public boolean alcanzoLimiteDeUsuarios() {
        return limiteUsuarios != null && usuarios >= limiteUsuarios;
    }
}
