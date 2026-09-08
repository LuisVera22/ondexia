package com.ondexia.domain.identidad;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Lo que la empresa sabe de su certificado digital, que no es el certificado.
 *
 * <p>El {@code .pfx} vive en el bucket del bus y su contraseña junto a la clave
 * SOL, en un objeto que la API puede escribir y no leer (doc 14 §4). Aquí queda
 * solo lo que sirve para la pantalla y para decidir si se puede emitir: cuándo
 * se cargó, qué dijo el Emisor al abrirlo y hasta cuándo vale.
 *
 * @param cargadoEn cuándo confirmó la pantalla que el archivo estaba en el bucket
 * @param verificadoEn cuándo el Emisor lo abrió con la contraseña; nulo hasta entonces
 * @param sujeto el {@code Subject} del certificado, para que la persona reconozca cuál es
 * @param venceEn su fecha de caducidad
 * @param error por qué no se pudo abrir, si fue el caso; nulo si abrió
 */
public record CertificadoDigital(
        Instant cargadoEn,
        Instant verificadoEn,
        String sujeto,
        LocalDate venceEn,
        String error) {

    /** Recién cargado, sin verificar todavía. */
    public static CertificadoDigital cargado(Instant ahora) {
        return new CertificadoDigital(ahora, null, null, null, null);
    }

    public CertificadoDigital verificado(Instant ahora, String sujeto, LocalDate venceEn) {
        return new CertificadoDigital(cargadoEn, ahora, sujeto, venceEn, null);
    }

    public CertificadoDigital fallido(Instant ahora, String motivo) {
        return new CertificadoDigital(cargadoEn, ahora, null, null, motivo);
    }

    /** Abrió con su contraseña y no ha caducado a la fecha dada. */
    public boolean vigente(LocalDate hoy) {
        return verificadoEn != null && error == null && venceEn != null && !venceEn.isBefore(hoy);
    }
}
