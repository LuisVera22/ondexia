package com.ondexia.domain.comun.error;

/**
 * Raíz de los errores que el negocio produce a propósito.
 *
 * <h2>Por qué no llevan código HTTP</h2>
 *
 * La versión anterior tenía un {@code getEstado()} que devolvía
 * {@code HttpStatus.FORBIDDEN}. Era el dominio sabiendo que existe HTTP.
 *
 * <p>Eso rompe dos cosas. La primera es la dirección de las dependencias: el
 * dominio no debe conocer el mecanismo por el que llega la petición. La segunda
 * es práctica y pesa más — <strong>{@code ondexia-facturacion} no es una API
 * web</strong>. Consume de una cola. Si las excepciones del dominio traen un
 * código HTTP dentro, ese módulo hereda un concepto que no significa nada en su
 * contexto.
 *
 * <p>Cada adaptador decide cómo se representa el error en su medio: la capa web
 * lo traduce a un estado HTTP en {@code ManejadorGlobalErrores}, y un consumidor
 * de cola decidirá si reintenta o va a la cola de descartes. Es la misma
 * excepción con dos lecturas distintas, que es justamente lo que se quiere.
 *
 * <p>El {@code codigo} es estable y legible por máquina: el frontend distingue
 * «el RUC ya existe» de «el RUC no es válido» sin analizar texto en español.
 */
public abstract class ErrorDeDominio extends RuntimeException {

    private final String codigo;

    protected ErrorDeDominio(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    protected ErrorDeDominio(String codigo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
