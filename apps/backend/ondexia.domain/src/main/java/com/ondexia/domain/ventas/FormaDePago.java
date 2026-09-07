package com.ondexia.domain.ventas;

/**
 * Con qué se cobra. Catálogo cerrado (doc 12 §3.4, decisión 8).
 *
 * <p>Cuatro valores que no cambian por cliente: una tabla para cuatro filas que
 * nadie edita sería una tabla de más. El día que un cliente pida una quinta
 * forma de pago —crédito a plazos, canje— es el momento de convertirlo en
 * tabla, y no antes.
 *
 * <p>Es lo que el arqueo de caja compara: por cada forma de pago, lo que el
 * sistema calcula frente a lo que la persona cuenta o consulta en su terminal.
 */
public enum FormaDePago {

    EFECTIVO("Efectivo"),

    TARJETA("Tarjeta"),

    TRANSFERENCIA("Transferencia"),

    BILLETERA_DIGITAL("Billetera digital");

    private final String nombre;

    FormaDePago(String nombre) {
        this.nombre = nombre;
    }

    public String nombre() {
        return nombre;
    }
}
