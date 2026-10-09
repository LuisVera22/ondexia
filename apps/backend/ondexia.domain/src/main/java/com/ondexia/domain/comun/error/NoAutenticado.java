package com.ondexia.domain.comun.error;

/**
 * No sabemos quién es.
 *
 * <p>Distinto de {@link AccesoDenegado}, y la diferencia le importa al cliente:
 * ante esto conviene renovar el token y reintentar; ante una denegación,
 * reintentar no sirve de nada.
 */
public class NoAutenticado extends ErrorDeDominio {

    public NoAutenticado(String mensaje) {
        super("no_autenticado", mensaje);
    }
}
