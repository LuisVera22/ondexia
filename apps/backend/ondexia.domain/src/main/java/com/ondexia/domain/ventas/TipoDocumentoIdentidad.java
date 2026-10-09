package com.ondexia.domain.ventas;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.util.Arrays;

/**
 * Documento de identidad del adquirente, catálogo 06 de SUNAT.
 *
 * <p>Falta a propósito el «0 · sin documento»: el cliente sin documento no es
 * una fila de {@code cliente}, es la ausencia de cliente en la venta (doc 12
 * §4.3). Cada tipo sabe validar su número, porque un RUC mal tecleado en una
 * factura es un comprobante rechazado.
 */
public enum TipoDocumentoIdentidad {

    DNI("1", "DNI") {
        @Override
        String normalizar(String numero) {
            String limpio = sinEspacios(numero);
            if (!limpio.matches("\\d{8}")) {
                throw new ReglaDeNegocioViolada(
                        "documento_invalido", "El DNI son ocho dígitos.", "numeroDocumento");
            }
            return limpio;
        }
    },
    CARNET_EXTRANJERIA("4", "Carné de extranjería") {
        @Override
        String normalizar(String numero) {
            return alfanumerico(numero, 12);
        }
    },
    RUC("6", "RUC") {
        @Override
        String normalizar(String numero) {
            // Con dígito verificador (doc 12 §3.2): la clase Ruc lo comprueba.
            return new Ruc(sinEspacios(numero)).valor();
        }
    },
    PASAPORTE("7", "Pasaporte") {
        @Override
        String normalizar(String numero) {
            return alfanumerico(numero, 12);
        }
    };

    private final String codigo;
    private final String nombre;

    TipoDocumentoIdentidad(String codigo, String nombre) {
        this.codigo = codigo;
        this.nombre = nombre;
    }

    /** El código del catálogo 06. */
    public String codigo() {
        return codigo;
    }

    public String nombre() {
        return nombre;
    }

    /** El número limpio y válido para este tipo, o una regla violada con el campo. */
    abstract String normalizar(String numero);

    public static TipoDocumentoIdentidad porCodigo(String codigo) {
        return Arrays.stream(values())
                .filter(t -> t.codigo.equals(codigo) || t.name().equalsIgnoreCase(codigo))
                .findFirst()
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "tipo_documento_invalido",
                        "El tipo de documento «" + codigo + "» no está en el catálogo admitido."));
    }

    private static String sinEspacios(String numero) {
        return numero == null ? "" : numero.replaceAll("\\s", "");
    }

    private static String alfanumerico(String numero, int maximo) {
        String limpio = sinEspacios(numero).toUpperCase();
        if (!limpio.matches("[A-Z0-9]{1," + maximo + "}")) {
            throw new ReglaDeNegocioViolada(
                    "documento_invalido",
                    "El número admite letras y dígitos, hasta " + maximo + " caracteres.",
                    "numeroDocumento");
        }
        return limpio;
    }
}
