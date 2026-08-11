package com.ondexia.api.comun.error;

import org.springframework.http.HttpStatus;

/**
 * Raiz de los errores que el sistema produce a proposito.
 *
 * <p>La distincion util no es «error de negocio» contra «error tecnico», sino
 * <strong>error previsto</strong> contra <strong>error no previsto</strong>.
 * Todo lo que hereda de aqui se anticipo al escribir el codigo y sabe que
 * respuesta merece; cualquier otra excepcion es un defecto y sale como 500 sin
 * revelar su contenido.
 *
 * <p>Cada error lleva un {@code codigo} estable ademas del mensaje. El mensaje
 * es para una persona y puede reescribirse; el codigo es para el frontend, que
 * necesita distinguir «el RUC ya existe» de «el RUC no es valido» sin analizar
 * texto en espanol.
 */
public abstract class ExcepcionAplicacion extends RuntimeException {

    private final String codigo;

    protected ExcepcionAplicacion(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    protected ExcepcionAplicacion(String codigo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }

    public abstract HttpStatus getEstado();
}
