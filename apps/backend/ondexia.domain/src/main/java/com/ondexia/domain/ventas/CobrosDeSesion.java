package com.ondexia.domain.ventas;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Cuánto se cobró durante una sesión de caja, por forma de pago.
 *
 * <p>Es un puerto y no un método de {@link SesionCaja} porque la sesión no
 * conoce las ventas: las conoce quien las registra. Hasta que exista el
 * documento de venta (iteración 4 del doc 12) la única implementación devuelve
 * cero en todo, y el arqueo compara lo declarado contra el monto inicial. Cuando
 * las ventas existan, cambia el adaptador y no la sesión.
 */
public interface CobrosDeSesion {

    Map<FormaDePago, BigDecimal> cobradoEn(UUID sesionId);
}
