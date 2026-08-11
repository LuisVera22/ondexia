package com.ondexia.api.comun.seguridad;

import java.util.Optional;

/**
 * Portador del contexto de la peticion en curso.
 *
 * <p>Se usa un {@code ThreadLocal} y no un bean de ambito peticion porque el
 * gestor de transacciones tambien lo necesita, y ese es un componente de
 * infraestructura que puede correr fuera de una peticion HTTP —arranque,
 * migraciones, tareas programadas—. Un proxy de ambito peticion inyectado ahi
 * lanzaria una excepcion en cuanto no hubiera peticion.
 *
 * <p><strong>Aqui vive el error mas peligroso del diseno.</strong> Los hilos se
 * reutilizan: el del servidor entre peticiones, y en Lambda el contenedor
 * entero entre invocaciones. Un {@code ThreadLocal} que no se limpia deja el
 * contexto del cliente A visible para la peticion del cliente B — exactamente
 * la fuga que todo este mecanismo existe para impedir.
 *
 * <p>Por eso {@link ContextoInterceptor} limpia en {@code afterCompletion},
 * que Spring garantiza que se ejecuta aunque el controlador lance excepcion. Y
 * por eso {@link #obtener()} devuelve {@code Optional} en lugar de nulo: cada
 * lector se ve obligado a decidir que hace sin contexto, en vez de asumir que
 * siempre lo hay.
 */
public final class ContextoActual {

    private static final ThreadLocal<ContextoPeticion> CONTEXTO = new ThreadLocal<>();

    private ContextoActual() {
    }

    static void establecer(ContextoPeticion contexto) {
        CONTEXTO.set(contexto);
    }

    /**
     * Limpia con {@code remove} y no con {@code set(null)}.
     *
     * <p>Poner nulo deja la entrada en el mapa del hilo y, con hilos de larga
     * vida, esa entrada nunca se libera. Es una fuga de memoria lenta y muy
     * dificil de atribuir.
     */
    static void limpiar() {
        CONTEXTO.remove();
    }

    public static Optional<ContextoPeticion> obtener() {
        return Optional.ofNullable(CONTEXTO.get());
    }

    /**
     * El contexto, o error si no lo hay.
     *
     * <p>Para el codigo de aplicacion, que solo corre dentro de una peticion
     * autenticada. Si esto falla es un defecto de configuracion —un endpoint
     * que quedo fuera del interceptor—, no una situacion que el llamador deba
     * manejar.
     */
    public static ContextoPeticion obtenerObligatorio() {
        ContextoPeticion contexto = CONTEXTO.get();
        if (contexto == null) {
            throw new IllegalStateException(
                    "No hay contexto de peticion. Este codigo espera ejecutarse dentro de una "
                            + "peticion autenticada que pase por ContextoInterceptor.");
        }
        return contexto;
    }
}
