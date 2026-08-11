package com.ondexia.api.comun.error;

import org.springframework.http.HttpStatus;

/**
 * No sabemos quien eres. Se responde 401.
 *
 * <p>Distinto de {@link AccesoDenegadoException}, que es «sabemos quien eres y
 * no puedes». La diferencia importa para el cliente: ante un 401 conviene
 * renovar el token y reintentar; ante un 403 reintentar no sirve de nada.
 */
public class NoAutenticadoException extends ExcepcionAplicacion {

    public NoAutenticadoException(String mensaje) {
        super("no_autenticado", mensaje);
    }

    @Override
    public HttpStatus getEstado() {
        return HttpStatus.UNAUTHORIZED;
    }
}
