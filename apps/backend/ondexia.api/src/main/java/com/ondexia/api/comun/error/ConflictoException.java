package com.ondexia.api.comun.error;

import org.springframework.http.HttpStatus;

/**
 * La operacion choca con el estado actual. Se responde 409.
 *
 * <p>Casos tipicos aqui: un RUC ya registrado, una serie y numero ya emitidos,
 * anular un comprobante que SUNAT ya rechazo.
 */
public class ConflictoException extends ExcepcionAplicacion {

    public ConflictoException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }

    @Override
    public HttpStatus getEstado() {
        return HttpStatus.CONFLICT;
    }
}
