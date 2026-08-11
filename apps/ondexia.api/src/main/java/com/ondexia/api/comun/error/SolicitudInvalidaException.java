package com.ondexia.api.comun.error;

import org.springframework.http.HttpStatus;

/** La peticion esta mal formada o incumple una regla de negocio. Se responde 400. */
public class SolicitudInvalidaException extends ExcepcionAplicacion {

    public SolicitudInvalidaException(String mensaje) {
        super("solicitud_invalida", mensaje);
    }

    public SolicitudInvalidaException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }

    @Override
    public HttpStatus getEstado() {
        return HttpStatus.BAD_REQUEST;
    }
}
