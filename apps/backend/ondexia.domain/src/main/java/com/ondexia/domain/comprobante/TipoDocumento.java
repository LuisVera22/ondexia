package com.ondexia.domain.comprobante;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.util.Arrays;

/**
 * Catálogo 01 de SUNAT: qué clase de documento es.
 *
 * <p>No es un enumerado de conveniencia. El código de dos dígitos viaja en el XML
 * del comprobante y en el libro electrónico, así que <strong>el valor guardado es
 * el del catálogo</strong> y no el nombre de la constante de Java. Guardar
 * {@code FACTURA} obligaría a una tabla de traducción el día que haya que emitir
 * de verdad.
 *
 * <p>La v1 no envía nada a SUNAT (plan 07 §1.2), pero los documentos se numeran
 * igual y el libro se lleva igual. Estos códigos hacen falta desde ahora.
 */
public enum TipoDocumento {

    /** Factura. Solo a quien tiene RUC. */
    FACTURA("01", "Factura", 'F'),

    /** Boleta de venta. Consumidor final. */
    BOLETA("03", "Boleta de venta", 'B'),

    /**
     * Nota de crédito. Corrige a la baja un comprobante ya emitido.
     *
     * <p>Admite serie que empiece por F o por B: la nota hereda la letra del
     * documento que modifica, porque una nota sobre una boleta con serie F
     * apuntaría a un documento que no existe en el libro de boletas.
     */
    NOTA_CREDITO("07", "Nota de crédito", 'F', 'B'),

    /** Nota de débito. Corrige al alza. Misma regla de serie que la de crédito. */
    NOTA_DEBITO("08", "Nota de débito", 'F', 'B'),

    /** Guía de remisión remitente. Traslado de mercadería. */
    GUIA_REMISION("09", "Guía de remisión", 'T');

    private final String codigo;
    private final String nombre;
    private final char[] letrasDeSerie;

    TipoDocumento(String codigo, String nombre, char... letrasDeSerie) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.letrasDeSerie = letrasDeSerie;
    }

    /** El código del catálogo 01. Es lo que se persiste y lo que viaja en el XML. */
    public String codigo() {
        return codigo;
    }

    public String nombre() {
        return nombre;
    }

    public static TipoDocumento porCodigo(String codigo) {
        return Arrays.stream(values())
                .filter(tipo -> tipo.codigo.equals(codigo))
                .findFirst()
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "tipo_documento_invalido",
                        "El tipo de documento '" + codigo + "' no está en el catálogo 01 de SUNAT."));
    }

    /**
     * Valida el formato de la serie para este tipo y la devuelve normalizada.
     *
     * <p>Cuatro caracteres, y <strong>la primera la fija SUNAT según el tipo</strong>:
     * F para factura, B para boleta, T para guía. Una serie con la letra
     * equivocada no la rechaza nadie hasta el envío, y entonces el rechazo llega
     * con todos los comprobantes de esa serie ya emitidos y numerados.
     */
    public String validarSerie(String serie) {
        if (serie == null || serie.isBlank()) {
            throw new ReglaDeNegocioViolada("serie_requerida", "La serie es obligatoria.");
        }

        String limpia = serie.trim().toUpperCase();

        if (!limpia.matches("[A-Z][A-Z0-9]{3}")) {
            throw new ReglaDeNegocioViolada(
                    "serie_invalida",
                    "La serie son cuatro caracteres: una letra y tres letras o dígitos, "
                            + "por ejemplo F001.");
        }

        if (!admiteLetra(limpia.charAt(0))) {
            throw new ReglaDeNegocioViolada(
                    "serie_invalida",
                    "La serie de " + nombre.toLowerCase() + " empieza por " + letrasEsperadas()
                            + ", no por " + limpia.charAt(0) + ".");
        }

        return limpia;
    }

    private boolean admiteLetra(char letra) {
        for (char admitida : letrasDeSerie) {
            if (admitida == letra) {
                return true;
            }
        }
        return false;
    }

    private String letrasEsperadas() {
        if (letrasDeSerie.length == 1) {
            return String.valueOf(letrasDeSerie[0]);
        }
        var partes = new StringBuilder();
        for (int i = 0; i < letrasDeSerie.length; i++) {
            partes.append(i == 0 ? "" : i == letrasDeSerie.length - 1 ? " o " : ", ")
                    .append(letrasDeSerie[i]);
        }
        return partes.toString();
    }
}
