package com.ondexia.domain.comun;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;

/**
 * Código de ubicación geográfica del INEI: departamento, provincia y distrito.
 *
 * <p>Seis dígitos, y el orden importa — {@code 150101} es Lima / Lima / Lima.
 * Aparece en el comprobante electrónico, así que un valor mal formado se
 * convierte en un rechazo de SUNAT.
 *
 * <p>Se valida la forma, no la existencia: la tabla de ubigeos es un catálogo
 * que cambia y que aún no tenemos cargado.
 */
public record Ubigeo(String valor) {

    public Ubigeo {
        if (valor == null || valor.isBlank()) {
            throw new ReglaDeNegocioViolada("ubigeo_requerido", "El ubigeo es obligatorio.");
        }
        valor = valor.trim();
        if (!valor.matches("\\d{6}")) {
            throw new ReglaDeNegocioViolada(
                    "ubigeo_invalido", "El ubigeo debe tener exactamente 6 dígitos.");
        }
    }

    public String departamento() {
        return valor.substring(0, 2);
    }

    public String provincia() {
        return valor.substring(2, 4);
    }

    public String distrito() {
        return valor.substring(4, 6);
    }

    @Override
    public String toString() {
        return valor;
    }
}
