package com.ondexia.domain.comun.error;

/**
 * La operación choca con el estado actual.
 *
 * <p>Un RUC ya registrado, una serie y número ya emitidos, quitar al último
 * administrador de una cuenta.
 */
public class Conflicto extends ErrorDeDominio {

    public Conflicto(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
