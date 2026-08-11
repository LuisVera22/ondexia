package com.ondexia.domain.identidad;

/**
 * Estado comercial de la cuenta.
 *
 * <p>{@code SUSPENDIDA} y {@code CANCELADA} se distinguen a proposito: la
 * primera es reversible al pagar y conserva los datos; la segunda cierra la
 * relacion. Los comprobantes emitidos se conservan en ambos casos — la
 * obligacion de guardar XML y CDR cinco anos no depende de que el cliente siga
 * pagando (DTE §10.4).
 */
public enum EstadoSuscripcion {
    EN_PRUEBA,
    ACTIVA,
    SUSPENDIDA,
    CANCELADA
}
