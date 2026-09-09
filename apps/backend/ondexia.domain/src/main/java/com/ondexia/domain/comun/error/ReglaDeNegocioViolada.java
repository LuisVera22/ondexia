package com.ondexia.domain.comun.error;

/**
 * La petición pedía algo que las reglas del negocio no permiten.
 *
 * <p>Puede llevar el nombre del campo de la petición al que pertenece, por el
 * mismo motivo que {@link Conflicto}: «el DNI son ocho dígitos» tiene que
 * aparecer debajo del cuadro del documento, no en un aviso de la esquina. La
 * mayoría de las reglas no señalan a ningún campo y no lo llevan.
 */
public class ReglaDeNegocioViolada extends ErrorDeDominio {

    private final String campo;

    public ReglaDeNegocioViolada(String codigo, String mensaje) {
        this(codigo, mensaje, null);
    }

    public ReglaDeNegocioViolada(String codigo, String mensaje, String campo) {
        super(codigo, mensaje);
        this.campo = campo;
    }

    /** @return el campo de la petición al que pertenece, o {@code null} */
    public String getCampo() {
        return campo;
    }
}
