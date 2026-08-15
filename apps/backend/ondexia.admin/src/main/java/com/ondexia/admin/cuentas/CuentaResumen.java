package com.ondexia.admin.cuentas;

import java.time.Instant;
import java.util.UUID;

/**
 * Una cuenta cliente vista desde el panel, con su consumo frente a los límites.
 *
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
