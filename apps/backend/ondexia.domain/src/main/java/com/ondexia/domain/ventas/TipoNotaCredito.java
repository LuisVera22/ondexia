package com.ondexia.domain.ventas;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.util.Arrays;

/**
 * Catálogo 09 de SUNAT: por qué se emite una nota de crédito.
 *
 * <p>El código de dos dígitos viaja en el XML, así que <strong>lo que se guarda
 * es el del catálogo</strong> y no el nombre de la constante, igual que en
 * {@link com.ondexia.domain.comprobante.TipoDocumento}.
 *
 * <p>Dos preguntas se responden desde aquí porque cambian lo que el sistema
 * hace, no solo lo que imprime:
 *
 * <ul>
 *   <li>{@link #anulaElDocumento()} — la nota cubre el comprobante entero y lo
 *       deja sin efecto. Entonces las líneas son todas las del original y el
 *       documento pasa a {@code ANULADO} cuando SUNAT acepta la nota.</li>
 *   <li>{@link #reponeExistencias()} — la mercadería vuelve. Un descuento
 *       posterior no devuelve nada al almacén, y sumarlo sería inventar
 *       existencias que no están en el estante.</li>
 * </ul>
 */
public enum TipoNotaCredito {

    ANULACION_DE_LA_OPERACION("01", "Anulación de la operación", true, true),

    /**
     * El comprobante salió a nombre de quien no era. Anula igual que la 01; lo
     * que cambia es el motivo que SUNAT ve.
     */
    ANULACION_POR_ERROR_EN_RUC("02", "Anulación por error en el RUC", true, true),

    /**
     * Corrige un dato del texto sin tocar importes ni mercadería. No anula: el
     * comprobante sigue vigente con la descripción corregida por la nota.
     */
    CORRECCION_POR_ERROR_EN_DESCRIPCION("03", "Corrección por error en la descripción", false, false),

    DESCUENTO_GLOBAL("04", "Descuento global", false, false),

    DESCUENTO_POR_ITEM("05", "Descuento por ítem", false, false),

    DEVOLUCION_TOTAL("06", "Devolución total", true, true),

    DEVOLUCION_POR_ITEM("07", "Devolución por ítem", false, true),

    BONIFICACION("08", "Bonificación", false, false),

    DISMINUCION_EN_EL_VALOR("09", "Disminución en el valor", false, false),

    OTROS_CONCEPTOS("10", "Otros conceptos", false, false);

    private final String codigo;
    private final String nombre;
    private final boolean anula;
    private final boolean repone;

    TipoNotaCredito(String codigo, String nombre, boolean anula, boolean repone) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.anula = anula;
        this.repone = repone;
    }

    /** El código del catálogo 09. Es lo que se persiste y lo que viaja en el XML. */
    public String codigo() {
        return codigo;
    }

    public String nombre() {
        return nombre;
    }

    /**
     * La nota deja sin efecto el comprobante entero. Obliga a que las líneas
     * sean todas las del original, y hace que el documento pase a
     * {@code ANULADO} cuando SUNAT acepta la nota — no antes: si SUNAT la
     * rechaza, la anulación no ocurrió.
     */
    public boolean anulaElDocumento() {
        return anula;
    }

    /** La mercadería vuelve al almacén. Un descuento no repone nada. */
    public boolean reponeExistencias() {
        return repone;
    }

    public static TipoNotaCredito porCodigo(String codigo) {
        return Arrays.stream(values())
                .filter(tipo -> tipo.codigo.equals(codigo))
                .findFirst()
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "motivo_nota_invalido",
                        "El motivo '" + codigo + "' no está en el catálogo 09 de SUNAT.",
                        "motivo"));
    }
}
