package com.ondexia.domain.consultas;

import java.time.Instant;

/**
 * Lo que RENIEC dice de un DNI, tal como lo devuelve el proveedor.
 *
 * <p>Sin atestación, a diferencia del RUC. El nombre de una persona natural no
 * decide nada fiscal: SUNAT no valida el nombre del adquirente de una boleta,
 * solo el número, y el número lo teclea el cliente. La consulta es una
 * comodidad para no teclear el nombre, y una comodidad no necesita firma.
 */
public record DatosDeDni(
        String dni,
        String nombres,
        String apellidoPaterno,
        String apellidoMaterno,
        Instant consultadoEn) {

    /** «Apellidos Nombres», como lo imprime la boleta. */
    public String nombreCompleto() {
        return String.join(" ", java.util.stream.Stream.of(apellidoPaterno, apellidoMaterno, nombres)
                .filter(parte -> parte != null && !parte.isBlank())
                .map(String::trim)
                .toList());
    }
}
