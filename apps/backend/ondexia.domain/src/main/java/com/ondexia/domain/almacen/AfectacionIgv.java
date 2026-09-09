package com.ondexia.domain.almacen;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.util.Arrays;

/**
 * Afectación al IGV de un bien, catálogo 07 de SUNAT: las tres onerosas.
 *
 * <p>Las variantes gratuitas (11 a 17, 21, 31 a 37) no son un atributo del
 * producto sino de la operación —el mismo bien se vende gravado y se regala
 * gratuito— y se resolverán en la línea del documento cuando exista la venta.
 * Aquí solo lo que define al bien: si al venderlo se cobra IGV o no.
 */
public enum AfectacionIgv {

    GRAVADO("10", "Gravado", true),
    EXONERADO("20", "Exonerado", false),
    INAFECTO("30", "Inafecto", false);

    private final String codigo;
    private final String nombre;
    private final boolean llevaIgv;

    AfectacionIgv(String codigo, String nombre, boolean llevaIgv) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.llevaIgv = llevaIgv;
    }

    /** El código del catálogo 07 que va en el XML. */
    public String codigo() {
        return codigo;
    }

    public String nombre() {
        return nombre;
    }

    public boolean llevaIgv() {
        return llevaIgv;
    }

    public static AfectacionIgv porCodigo(String codigo) {
        return Arrays.stream(values())
                .filter(a -> a.codigo.equals(codigo) || a.name().equalsIgnoreCase(codigo))
                .findFirst()
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "afectacion_invalida",
                        "La afectación al IGV «" + codigo + "» no es gravado, exonerado ni inafecto."));
    }
}
