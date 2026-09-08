package com.ondexia.facturacion.sunat;

import java.util.List;

/**
 * Lo que SUNAT contestó a un {@code sendBill}, ya leído (doc 14 §5).
 *
 * <ul>
 *   <li>Con CDR: SUNAT procesó el comprobante. {@code codigo} 0 es aceptado,
 *       2000–3999 rechazado; {@code notas} son las observaciones (4000+).</li>
 *   <li>Fallo SOAP: SUNAT no lo procesó y dice por qué en el {@code faultcode}.
 *       0100–1999 son problemas del servicio o de la sesión; 2000–3999, del
 *       comprobante o de las credenciales.</li>
 *   <li>Sin respuesta legible: red, HTTP inesperado, HTML de mantenimiento.</li>
 * </ul>
 *
 * @param cdr el ZIP tal como llegó, para guardarlo; nulo si no hubo CDR
 */
public record RespuestaSunat(Tipo tipo, String codigo, String descripcion, List<String> notas,
        byte[] cdr) {

    public enum Tipo { CDR, FALLO, SIN_RESPUESTA }

    public RespuestaSunat {
        notas = notas == null ? List.of() : List.copyOf(notas);
    }

    public static RespuestaSunat sinRespuesta(String codigo, String descripcion) {
        return new RespuestaSunat(Tipo.SIN_RESPUESTA, codigo, descripcion, List.of(), null);
    }

    /** Aceptado: CDR con código 0. Todo lo demás no lo es. */
    public boolean aceptado() {
        return tipo == Tipo.CDR && "0".equals(codigo);
    }

    /**
     * Rechazado por el contenido o las credenciales: código 2000–3999, venga
     * en el CDR o en un fallo SOAP. Se corrige y se reenvía con el mismo número.
     */
    public boolean rechazado() {
        int numero = codigoNumerico();
        return numero >= 2000 && numero <= 3999;
    }

    int codigoNumerico() {
        try {
            return Integer.parseInt(codigo);
        } catch (NumberFormatException noEsNumero) {
            return -1;
        }
    }
}
