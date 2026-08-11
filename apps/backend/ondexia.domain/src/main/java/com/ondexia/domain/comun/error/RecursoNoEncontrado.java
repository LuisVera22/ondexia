package com.ondexia.domain.comun.error;

/**
 * No existe lo que se pidió.
 *
 * <p>Con Row Level Security activo, un recurso de otra empresa llega también
 * aquí: la consulta no lo encuentra porque la política lo filtró. Que un
 * identificador ajeno responda «no encontrado» y no «prohibido» es lo correcto
 * — lo segundo confirmaría que ese identificador existe en alguna parte del
 * sistema.
 */
public class RecursoNoEncontrado extends ErrorDeDominio {

    public RecursoNoEncontrado(String recurso, Object identificador) {
        super("no_encontrado", "No se encontró " + recurso + " con identificador " + identificador);
    }

    public RecursoNoEncontrado(String mensaje) {
        super("no_encontrado", mensaje);
    }
}
