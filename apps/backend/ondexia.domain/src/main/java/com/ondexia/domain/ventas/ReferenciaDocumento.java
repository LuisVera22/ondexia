package com.ondexia.domain.ventas;

import com.ondexia.domain.comprobante.TipoDocumento;

/**
 * El documento al que otro se refiere: el que una nota de crédito modifica, o
 * la nota de venta de la que salió un comprobante por canje.
 *
 * <p>Va copiado y no resuelto por el identificador, por el mismo motivo que las
 * líneas copian la descripción del producto: <strong>es lo que se imprime y lo
 * que viaja en el XML</strong>, y tiene que decir lo mismo dentro de diez años
 * aunque el original cambie de estado. Además evita una consulta más en cada
 * lectura, que en un listado son tantas como filas.
 */
public record ReferenciaDocumento(TipoDocumento tipo, String serie, long numero) {

    public static ReferenciaDocumento de(DocumentoVenta documento) {
        return new ReferenciaDocumento(documento.tipo(), documento.serie(), documento.numero());
    }

    /** {@code B001-00000012}, tal como se imprime. */
    public String numeroCompleto() {
        return serie + "-" + String.format("%08d", numero);
    }
}
