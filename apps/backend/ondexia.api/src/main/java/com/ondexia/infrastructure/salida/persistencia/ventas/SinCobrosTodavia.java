package com.ondexia.infrastructure.salida.persistencia.ventas;

import com.ondexia.domain.ventas.CobrosDeSesion;
import com.ondexia.domain.ventas.FormaDePago;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Hasta la iteración 4 no hay ventas, así que en una sesión no se cobra nada:
 * el arqueo compara lo declarado contra el monto inicial. Cuando exista
 * {@code documento_venta} con sus pagos, esta clase se sustituye por la que los
 * suma por forma de pago, y {@code SesionesDeCaja} no cambia.
 */
@Component
public class SinCobrosTodavia implements CobrosDeSesion {

    @Override
    public Map<FormaDePago, BigDecimal> cobradoEn(UUID sesionId) {
        return Map.of();
    }
}
