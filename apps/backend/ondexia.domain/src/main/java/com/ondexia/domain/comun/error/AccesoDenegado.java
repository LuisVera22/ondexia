package com.ondexia.domain.comun.error;

/**
 * Sabemos quién es y no puede hacer esto.
 *
 * <p>El mensaje debe decir qué falta, nunca qué existe. «No tienes acceso a la
 * empresa solicitada» es correcto; «la empresa X pertenece a la cuenta Y» sería
 * una fuga: convierte el endpoint en un buscador de qué RUC están dados de alta
 * en Ondexia.
 */
public class AccesoDenegado extends ErrorDeDominio {

    public AccesoDenegado(String mensaje) {
        super("acceso_denegado", mensaje);
    }
}
