package com.ondexia.api.comun.seguridad;

import java.util.UUID;

/**
 * Fija el contexto de la petición desde una prueba, sin pasar por HTTP.
 *
 * <p>Vive en este paquete porque {@link ContextoActual#establecer} es de ámbito
 * de paquete a propósito: fuera de las pruebas, el único que debe establecerlo
 * es {@link ContextoInterceptor}. Si cualquier servicio pudiera hacerlo, el
 * contexto dejaría de ser «quién hizo esta petición» para pasar a ser «lo que
 * alguien decidió poner ahí», y con él se caería todo el aislamiento.
 *
 * <p>Es de ámbito de prueba, así que no se empaqueta.
 */
public final class ContextoDePrueba {

    private ContextoDePrueba() {
    }

    public static void comoUsuarioDe(UUID usuarioId, UUID cuentaId, UUID empresaId) {
        ContextoActual.establecer(new ContextoPeticion(
                usuarioId, cuentaId, 1L, empresaId, null, null, true, "127.0.0.1"));
    }

    public static void establecer(ContextoPeticion contexto) {
        ContextoActual.establecer(contexto);
    }

    public static void limpiar() {
        ContextoActual.limpiar();
    }
}
