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

    private RecursoNoEncontrado(String codigo, String mensaje, boolean explicito) {
        super(codigo, mensaje);
    }

    /**
     * Con código propio, para los casos que el frontend necesita distinguir.
     *
     * <p>Es un método y no un constructor a propósito. Un
     * {@code RecursoNoEncontrado(String, String)} sería <strong>ambiguo</strong>
     * con {@code (String recurso, Object identificador)} —toda cadena es un
     * Object— y Java elegiría el segundo sin avisar: el código quedaría en
     * {@code "no_encontrado"} y el mensaje saldría como «No se encontró
     * empresa_no_encontrada con identificador La empresa ya no existe».
     *
     * <p>No es hipotético: ocurrió en la Entrega 1 y solo se vio al leer esta
     * clase, porque las pruebas comprobaban el código HTTP y no el cuerpo.
     */
    public static RecursoNoEncontrado con(String codigo, String mensaje) {
        return new RecursoNoEncontrado(codigo, mensaje, true);
    }
}
