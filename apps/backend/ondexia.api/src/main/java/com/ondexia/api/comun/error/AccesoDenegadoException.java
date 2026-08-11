package com.ondexia.api.comun.error;

import org.springframework.http.HttpStatus;

/**
 * Sabemos quien eres y no puedes hacer esto. Se responde 403.
 *
 * <p>El mensaje debe decir que falta, nunca que existe. «No tienes acceso a la
 * empresa solicitada» es correcto; «la empresa X pertenece a la cuenta Y» seria
 * una fuga: convierte el endpoint en un buscador de que RUC estan dados de alta
 * en Ondexia.
 */
public class AccesoDenegadoException extends ExcepcionAplicacion {

    public AccesoDenegadoException(String mensaje) {
        super("acceso_denegado", mensaje);
    }

    @Override
    public HttpStatus getEstado() {
        return HttpStatus.FORBIDDEN;
    }
}
