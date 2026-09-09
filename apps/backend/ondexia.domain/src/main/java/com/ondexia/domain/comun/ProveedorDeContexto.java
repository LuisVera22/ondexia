package com.ondexia.domain.comun;

import java.util.Optional;

/**
 * Puerto: de dónde sale el contexto de la operación en curso.
 *
 * <p><strong>Existe para que el dominio y los casos de uso no llamen a un
 * {@code ThreadLocal} estático.</strong> Antes cualquier clase podía hacer
 * {@code ContextoActual.obtener()}, con dos consecuencias: la dependencia era
 * invisible en la firma —había que leer el cuerpo del método para saber que
 * necesitaba un contexto— y probar un caso de uso obligaba a manipular una
 * variable global.
 *
 * <p>Con el puerto, el contexto se inyecta, la dependencia se ve, y una prueba
 * pasa el que quiera sin levantar Spring ni fingir una petición HTTP.
 *
 * <p>El adaptador HTTP sigue usando un {@code ThreadLocal} por debajo, porque
 * es lo que corresponde a un servidor que atiende una petición por hilo. Pero
 * eso ahora es un detalle de infraestructura, escondido donde debe estar.
 */
public interface ProveedorDeContexto {

    /**
     * Devuelve {@code Optional} y no el valor directo a propósito: obliga a cada
     * lector a decidir qué hace sin contexto, en vez de asumir que siempre lo
     * hay. Hay código legítimo que corre fuera de una petición —migraciones,
     * tareas programadas— y ahí no lo hay.
     */
    Optional<ContextoOperacion> actual();

    /**
     * El contexto, o error.
     *
     * <p>Para el código que solo corre dentro de una petición autenticada. Si
     * esto falla es un defecto de configuración, no una situación que el
     * llamador deba manejar.
     */
    default ContextoOperacion obligatorio() {
        return actual().orElseThrow(() -> new IllegalStateException(
                "No hay contexto de operación. Este código espera ejecutarse dentro de "
                        + "una petición autenticada."));
    }
}
