package com.ondexia.domain.consultas;

import com.ondexia.domain.comun.error.ErrorDeDominio;

/**
 * La verificación del RUC no se puede creer.
 *
 * <h2>Un solo error para varios motivos, a propósito</h2>
 *
 * <p>Firma que no cuadra, atestación caducada, formato irreconocible: todo llega
 * aquí. La tentación es separarlos para dar mejores mensajes, y para quien
 * manipula la atestación eso es exactamente la ayuda que no debe tener — «firma
 * inválida» frente a «formato inválido» le dice si va por buen camino.
 *
 * <p>El caso caducada sí se distingue en el texto, porque le ocurre a gente
 * normal que tardó en enviar el formulario y necesita saber que basta con volver
 * a consultar.
 *
 * <p>No lleva la causa hacia el cliente: se registra y se queda dentro.
 */
public class AtestacionInvalida extends ErrorDeDominio {

    public AtestacionInvalida(String mensaje) {
        super("atestacion_invalida", mensaje);
    }

    public AtestacionInvalida(String mensaje, Throwable causa) {
        super("atestacion_invalida", mensaje, causa);
    }
}
