package com.ondexia.domain.comprobante;

/**
 * Dónde está un comprobante ante SUNAT (doc 14 §3).
 *
 * <p>Es distinto del estado del documento de venta ({@code EstadoDocumento}),
 * que mira al negocio: una boleta puede estar {@code PENDIENTE} para la tienda
 * mientras aquí pasa por {@code EN_COLA}, {@code ERROR_ENVIO} y {@code EN_COLA}
 * otra vez. Solo {@code ACEPTADO} la convierte en {@code EMITIDO}.
 *
 * <ul>
 *   <li>{@code EN_COLA}: la orden está en el bus o a punto de entrar. El Emisor
 *       todavía no respondió.</li>
 *   <li>{@code EN_PROCESO}: solo en los envíos asíncronos —la comunicación de
 *       baja—. SUNAT recibió el archivo y devolvió un ticket; la respuesta
 *       llega al consultarlo. Un comprobante nunca pasa por aquí: su envío es
 *       síncrono y trae el CDR en la misma llamada.</li>
 *   <li>{@code ACEPTADO}: SUNAT devolvió un CDR con código 0. Puede traer
 *       observaciones (códigos 4000+), que no lo invalidan.</li>
 *   <li>{@code RECHAZADO}: CDR o fallo SOAP con código 2000–3999. Para SUNAT el
 *       comprobante no existe, así que se corrige lo que diga el código y se
 *       reintenta con el mismo número.</li>
 *   <li>{@code ERROR_ENVIO}: no llegó a SUNAT o SUNAT no respondió: red, servicio
 *       caído (códigos 0100–1999), certificado que no abre. Se reintenta tal
 *       cual.</li>
 *   <li>{@code ANULADO}: por comunicación de baja o resumen (iteración 6).</li>
 * </ul>
 */
public enum EstadoSunat {
    EN_COLA,
    EN_PROCESO,
    ACEPTADO,
    RECHAZADO,
    ERROR_ENVIO,
    ANULADO;

    /** Si desde aquí tiene sentido volver a enviar. */
    public boolean admiteReintento() {
        return this == RECHAZADO || this == ERROR_ENVIO;
    }

    /** Si sigue en camino: nadie ha dicho todavía qué pasó. */
    public boolean estaEnCurso() {
        return this == EN_COLA || this == EN_PROCESO;
    }

    /** Si ya no cambiará por sí solo: SUNAT dijo la última palabra. */
    public boolean esFinal() {
        return this == ACEPTADO || this == ANULADO;
    }
}
