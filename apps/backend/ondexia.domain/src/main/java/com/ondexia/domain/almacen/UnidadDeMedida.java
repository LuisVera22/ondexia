package com.ondexia.domain.almacen;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.util.Arrays;

/**
 * Unidades de medida del catálogo 03 de SUNAT (UN/ECE Recomendación 20) que
 * un negocio de mostrador usa de verdad.
 *
 * <p>Enumerado y no tabla: el catálogo lo publica SUNAT, no lo edita el
 * cliente, y una unidad que no esté aquí no puede ir en un comprobante. Si un
 * cliente necesita otra, se añade una constante; hasta entonces la lista corta
 * evita el desplegable de trescientas filas en el que nadie encuentra «Unidad».
 * El código es el que viaja en el XML (cbc:InvoicedQuantity/@unitCode).
 */
public enum UnidadDeMedida {

    NIU("Unidad"),
    ZZ("Servicio"),
    KGM("Kilogramo"),
    GRM("Gramo"),
    LTR("Litro"),
    MLT("Mililitro"),
    MTR("Metro"),
    CMT("Centímetro"),
    MTK("Metro cuadrado"),
    MTQ("Metro cúbico"),
    GLL("Galón"),
    TNE("Tonelada"),
    BX("Caja"),
    BG("Bolsa"),
    PK("Paquete"),
    PR("Par"),
    DZN("Docena"),
    SET("Juego"),
    CEN("Ciento"),
    MIL("Millar"),
    HUR("Hora"),
    DAY("Día");

    private final String nombre;

    UnidadDeMedida(String nombre) {
        this.nombre = nombre;
    }

    /** El código del catálogo 03 es el propio nombre de la constante. */
    public String codigo() {
        return name();
    }

    public String nombre() {
        return nombre;
    }

    public static UnidadDeMedida porCodigo(String codigo) {
        return Arrays.stream(values())
                .filter(unidad -> unidad.name().equalsIgnoreCase(codigo == null ? "" : codigo.trim()))
                .findFirst()
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "unidad_invalida",
                        "La unidad de medida «" + codigo + "» no está en el catálogo admitido."));
    }
}
