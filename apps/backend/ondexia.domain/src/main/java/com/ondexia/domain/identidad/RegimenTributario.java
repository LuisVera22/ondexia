package com.ondexia.domain.identidad;

/**
 * Lo único del régimen tributario que cambia lo que la empresa puede emitir.
 *
 * <h2>Por qué solo dos valores</h2>
 *
 * <p>El padrón de SUNAT no informa el régimen en la consulta pública, así que el
 * sistema no puede saberlo solo: hay que preguntarlo. Y de los cuatro regímenes
 * —Nuevo RUS, Especial, MYPE Tributario y General— solo uno cambia algo aquí:
 * <strong>quien está en el Nuevo RUS no emite facturas</strong>, solo boletas y
 * tickets. Los otros tres emiten lo mismo entre sí. Preguntar cuál de los tres
 * es sería pedir un dato para no usarlo, y una pregunta más en el alta.
 *
 * <p>Por eso la pregunta del formulario es una sola, de sí o no: «¿Su negocio
 * está en el Nuevo RUS?». La responsabilidad de la respuesta es del
 * contribuyente, como en cualquier sistema del mercado; la nuestra es
 * preguntarlo bien y una vez, y no ofrecer después una factura que SUNAT
 * rechazaría (doc 12 §3.1).
 */
public enum RegimenTributario {

    NUEVO_RUS("Nuevo RUS", false),

    OTRO("Régimen general, MYPE tributario o especial", true);

    private final String nombre;
    private final boolean emiteFacturas;

    RegimenTributario(String nombre, boolean emiteFacturas) {
        this.nombre = nombre;
        this.emiteFacturas = emiteFacturas;
    }

    public String nombre() {
        return nombre;
    }

    public boolean emiteFacturas() {
        return emiteFacturas;
    }

    /** Lo que se asume de una empresa que no dijo nada: la mayoría no está en el RUS. */
    public static RegimenTributario porOmision() {
        return OTRO;
    }
}
