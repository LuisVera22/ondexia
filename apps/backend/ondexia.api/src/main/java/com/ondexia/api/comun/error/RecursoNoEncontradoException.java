package com.ondexia.api.comun.error;

import org.springframework.http.HttpStatus;

/**
 * El recurso no existe. Se responde 404.
 *
 * <p>Con RLS activo, un recurso de otra empresa tambien llega aqui: la consulta
 * no lo encuentra porque la politica lo filtro. Que un identificador ajeno
 * responda 404 y no 403 es lo correcto — un 403 confirmaria que ese
 * identificador existe en alguna parte del sistema.
 */
public class RecursoNoEncontradoException extends ExcepcionAplicacion {

    public RecursoNoEncontradoException(String recurso, Object identificador) {
        super("no_encontrado", "No se encontro " + recurso + " con identificador " + identificador);
    }

    public RecursoNoEncontradoException(String mensaje) {
        super("no_encontrado", mensaje);
    }

    @Override
    public HttpStatus getEstado() {
        return HttpStatus.NOT_FOUND;
    }
}
