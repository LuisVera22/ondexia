package com.ondexia.domain.comun.error;

/**
 * La operación choca con el estado actual.
 *
 * <p>Un RUC ya registrado, una serie y número ya emitidos, quitar al último
 * administrador de una cuenta.
 *
 * <h2>Por qué puede llevar el nombre de un campo</h2>
 *
 * <p>Porque la mayoría de los conflictos son de un dato concreto: el RUC que se
 * repite, el código de establecimiento que ya existe. El cliente coloca los
 * mensajes de validación junto al campo al que pertenecen, y sin este dato un
 * «Ya existe un establecimiento con ese código» acababa en un aviso de la
 * esquina — lejos del único campo que hay que corregir.
 *
 * <p>Es opcional, y no por comodidad: hay conflictos que de verdad no son de
 * ningún campo. Quitar al último administrador no señala a un cuadro de texto.
 *
 * <p>El nombre es el del campo <strong>de la petición</strong>, no el de la
 * columna: es lo que el cliente puede encontrar en su formulario. Que a veces
 * coincidan es casualidad.
 */
public class Conflicto extends ErrorDeDominio {

    private final String campo;

    public Conflicto(String codigo, String mensaje) {
        this(codigo, mensaje, null);
    }

    public Conflicto(String codigo, String mensaje, String campo) {
        super(codigo, mensaje);
        this.campo = campo;
    }

    /** @return el campo al que pertenece, o {@code null} si no es de ninguno */
    public String getCampo() {
        return campo;
    }
}
