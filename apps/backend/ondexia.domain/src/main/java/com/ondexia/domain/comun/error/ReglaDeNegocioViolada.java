package com.ondexia.domain.comun.error;

/**
 * Se intentó algo que las reglas del negocio no permiten.
 *
 * <p>Por ejemplo: un RUC con el verificador equivocado, pasar una empresa a
 * producción sin certificado, anular un comprobante que SUNAT ya rechazó.
 *
 * <p>La capa web la traduce a 400. Es la excepción que más se va a usar.
 */
public class ReglaDeNegocioViolada extends ErrorDeDominio {

    public ReglaDeNegocioViolada(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
