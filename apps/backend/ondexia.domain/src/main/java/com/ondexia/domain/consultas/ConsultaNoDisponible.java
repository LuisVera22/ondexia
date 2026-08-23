package com.ondexia.domain.consultas;

import com.ondexia.domain.comun.error.ErrorDeDominio;

/**
 * No se pudo preguntar. Distinto de que la respuesta sea «no existe».
 *
 * <h2>Por qué la distinción tiene consecuencias</h2>
 *
 * <p>Un RUC que el padrón no conoce es una respuesta: el número está mal y hay
 * que corregirlo. Un proveedor caído no es una respuesta: el RUC puede estar
 * perfectamente bien. Si las dos cosas llegaran igual, la aplicación diría «ese
 * RUC no existe» a alguien cuyo RUC existe — y esa persona intentaría corregir
 * un número correcto.
 *
 * <p>Por eso el puerto devuelve {@code Optional} para la ausencia y lanza esto
 * para la indisponibilidad.
 *
 * <h2>{@code reintentable}</h2>
 *
 * <p>Es el {@code retryable} que apiperu.dev devuelve en su respuesta, elevado a
 * concepto del dominio en vez de quedarse en su adaptador. Sin él, quien
 * orquesta la cascada tiene que <strong>adivinar</strong> por el código HTTP si
 * conviene reintentar o pasar al siguiente proveedor, y las dos formas de
 * adivinar mal cuestan: reintentar lo que nunca va a funcionar hace esperar en
 * balde a quien se está registrando, y rendirse ante un fallo pasajero lo manda
 * a un error que no existía.
 *
 * <p>Un adaptador que no sepa distinguirlo debe decir {@code false}. Suponer que
 * se puede reintentar es lo que produce la espera inútil.
 */
public class ConsultaNoDisponible extends ErrorDeDominio {

    private final boolean reintentable;

    public ConsultaNoDisponible(String codigo, String mensaje, boolean reintentable) {
        super(codigo, mensaje);
        this.reintentable = reintentable;
    }

    public ConsultaNoDisponible(String codigo, String mensaje, boolean reintentable,
            Throwable causa) {
        super(codigo, mensaje, causa);
        this.reintentable = reintentable;
    }

    /** Si tiene sentido volver a intentarlo con el mismo proveedor. */
    public boolean esReintentable() {
        return reintentable;
    }
}
