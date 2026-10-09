package com.ondexia.domain.consultas;

/**
 * Condición del domicilio fiscal.
 *
 * <p>Es la segunda puerta del registro, y es independiente del estado: un RUC
 * puede estar {@code ACTIVO} y {@code NO_HABIDO} a la vez. Significa que SUNAT
 * no encontró a nadie en la dirección declarada, y un no habido tiene el crédito
 * fiscal de sus clientes en entredicho. Registrarlo sería vender un problema.
 */
public enum CondicionDomicilio {

    HABIDO,

    /** SUNAT verificó y no halló actividad; es el caso con consecuencias. */
    NO_HABIDO,

    /** Se intentó notificar y no se localizó. Paso previo al no habido. */
    NO_HALLADO,

    /** Recién inscrito, sin verificar todavía. */
    POR_VERIFICAR;

    /** Solo {@link #HABIDO}, por el mismo motivo que {@code permiteEmitir}. */
    public boolean esHabido() {
        return this == HABIDO;
    }
}
