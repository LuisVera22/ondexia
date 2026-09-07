package com.ondexia.domain.ventas;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Un cobro dentro de un documento. Varios por documento: pago mixto.
 *
 * @param referencia los últimos dígitos de la tarjeta, el número de operación
 *                   de la transferencia o el de la billetera. Libre y opcional
 */
public record Pago(FormaDePago forma, BigDecimal monto, String referencia) {

    public Pago {
        Objects.requireNonNull(forma, "forma");
        if (monto == null || monto.signum() <= 0) {
            throw new ReglaDeNegocioViolada(
                    "pago_invalido", "Cada pago tiene que ser mayor que cero.", "pagos");
        }
        if (monto.stripTrailingZeros().scale() > 2) {
            throw new ReglaDeNegocioViolada(
                    "pago_invalido", "Un pago se expresa en céntimos: dos decimales.", "pagos");
        }
        referencia = referencia == null || referencia.isBlank() ? null : referencia.trim();
    }
}
