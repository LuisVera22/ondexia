package com.ondexia.domain.comprobante;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo que el Emisor devuelve por el bus para una orden (doc 14 §2).
 *
 * <p>Un solo tipo para las dos operaciones. Para {@code EMITIR}, lo que dijo
 * SUNAT y dónde quedaron el XML y el CDR; para {@code VERIFICAR_CREDENCIALES},
 * lo que se pudo leer del certificado. Los campos que no aplican van nulos.
 *
 * @param ordenId el de la {@link OrdenDeEmision}; para una emisión es el
 *                identificador del comprobante
 * @param empresaId el de la orden, copiado: con él se compone la clave del objeto
 * @param estado {@code ACEPTADO}, {@code RECHAZADO} o {@code ERROR_ENVIO}. En
 *               una verificación, {@code ACEPTADO} significa «el certificado
 *               abre con esa contraseña» y {@code ERROR_ENVIO} lo contrario
 * @param codigo el código de SUNAT ({@code 0}, {@code 2xxx}, {@code 0100}…) o
 *               uno propio del Emisor en un fallo local
 * @param descripcion legible; es lo que ve la persona en pantalla
 * @param observaciones notas del CDR (códigos 4000+): aceptado con reparos
 * @param claveXml objeto del XML firmado en el bus; nulo si no se llegó a firmar
 * @param claveCdr objeto del CDR; nulo si SUNAT no lo devolvió
 * @param resumenFirma el {@code DigestValue} de la firma, que va impreso y en el QR
 * @param certificadoSujeto el {@code Subject} del certificado, solo al verificar
 * @param certificadoVenceEn su {@code notAfter}, solo al verificar
 */
public record ResultadoDeEmision(
        java.util.UUID ordenId,
        java.util.UUID empresaId,
        OrdenDeEmision.Operacion operacion,
        EstadoSunat estado,
        String codigo,
        String descripcion,
        List<String> observaciones,
        String claveXml,
        String claveCdr,
        String resumenFirma,
        String certificadoSujeto,
        LocalDate certificadoVenceEn,
        Instant procesadoEn) {

    public ResultadoDeEmision {
        observaciones = observaciones == null ? List.of() : List.copyOf(observaciones);
    }
}
