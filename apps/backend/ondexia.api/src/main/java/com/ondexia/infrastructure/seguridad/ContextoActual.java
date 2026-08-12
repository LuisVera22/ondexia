package com.ondexia.infrastructure.seguridad;

import com.ondexia.domain.comun.ContextoOperacion;
import java.util.Optional;

/**
 * Almacén del contexto de la petición en curso, atado al hilo.
 *
 * <p>Detalle de infraestructura: un servidor que atiende una petición por hilo
 * es justo donde un {@code ThreadLocal} corresponde. Fuera de este paquete nadie
 * lo ve — el resto del sistema usa el puerto {@code ProveedorDeContexto}.
 *
 * <h2>Aquí vive el error más peligroso del diseño</h2>
 *
 * Los hilos se reutilizan: el del servidor entre peticiones, y en Lambda el
 * contenedor entero entre invocaciones. Un {@code ThreadLocal} que no se limpia
 * deja el contexto del cliente A visible para la petición del cliente B —
 * exactamente la fuga que todo este mecanismo existe para impedir.
 *
 * <p>Por eso {@link ContextoInterceptor} limpia en {@code afterCompletion}, que
 * Spring garantiza que se ejecuta aunque el controlador lance excepción.
 */
public final class ContextoActual {

    private static final ThreadLocal<ContextoOperacion> CONTEXTO = new ThreadLocal<>();

    private ContextoActual() {
    }

    static void establecer(ContextoOperacion contexto) {
        CONTEXTO.set(contexto);
    }

    /**
     * Limpia con {@code remove} y no con {@code set(null)}: poner nulo deja la
     * entrada en el mapa del hilo y, con hilos de larga vida, nunca se libera.
     */
    static void limpiar() {
        CONTEXTO.remove();
    }

    static Optional<ContextoOperacion> obtener() {
        return Optional.ofNullable(CONTEXTO.get());
    }
}
