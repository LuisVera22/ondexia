package com.ondexia.domain.ventas;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Un cobro dentro de un documento. Varios por documento: pago mixto.
 *
 * <h2>El billete y el importe no son lo mismo</h2>
 *
 * <p>{@code monto} es lo que este pago aplica al documento; {@code entregado} es
 * lo que el cliente puso sobre el mostrador cuando fue mas. Cobrar S/ 98 con un
 * billete de 100 son {@code monto = 98} y {@code entregado = 100}, y el
 * {@link #vuelto()} sale de restarlos.
 *
 * <p>La separacion no es un capricho de nombres. Del {@code monto} dependen el
 * total del comprobante que va a SUNAT —donde el billete del cliente no pinta
 * nada— y el arqueo de la sesion de caja, que compara lo cobrado con lo contado.
 * Si el vuelto entrara en el monto, la caja cuadraria de menos cada vez que
 * alguien paga con un billete grande.
 *
 * <p>El vuelto no se guarda en ningun sitio: es una resta. Un valor derivado que
 * se almacena es un valor que algun dia contradice a sus operandos.
 *
 * @param referencia los ultimos digitos de la tarjeta, el numero de operacion
 *                   de la transferencia o el de la billetera. Libre y opcional
 * @param entregado  lo que el cliente entrego, si fue mas que el monto.
 *                   {@code null} cuando pago justo. Solo en efectivo: una
 *                   tarjeta cobra el importe exacto
 */
public record Pago(FormaDePago forma, BigDecimal monto, String referencia, BigDecimal entregado) {

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
        entregado = normalizarEntregado(forma, monto, entregado);
    }

    /** Pago justo: lo entregado es exactamente el monto. */
    public Pago(FormaDePago forma, BigDecimal monto, String referencia) {
        this(forma, monto, referencia, null);
    }

    /**
     * Lo que hay que devolver. Cero cuando el pago fue justo, que es el caso de
     * cualquier forma de pago que no sea efectivo.
     */
    public BigDecimal vuelto() {
        return entregado == null ? BigDecimal.ZERO : entregado.subtract(monto);
    }

    /** Lo que salio del cajon o del datafono: el monto, salvo que se diera vuelto. */
    public BigDecimal loRecibido() {
        return entregado == null ? monto : entregado;
    }

    private static BigDecimal normalizarEntregado(
            FormaDePago forma, BigDecimal monto, BigDecimal entregado) {
        if (entregado == null) {
            return null;
        }
        if (forma != FormaDePago.EFECTIVO) {
            throw new ReglaDeNegocioViolada(
                    "vuelto_solo_en_efectivo",
                    "Solo un pago en efectivo puede entregar de más y recibir vuelto.", "pagos");
        }
        if (entregado.stripTrailingZeros().scale() > 2) {
            throw new ReglaDeNegocioViolada(
                    "entregado_invalido",
                    "Lo entregado se expresa en céntimos: dos decimales.", "pagos");
        }
        int comparacion = entregado.compareTo(monto);
        if (comparacion < 0) {
            throw new ReglaDeNegocioViolada(
                    "entregado_insuficiente",
                    "Lo entregado (S/ " + entregado.toPlainString() + ") no cubre el pago de S/ "
                            + monto.toPlainString() + ". Un cobro parcial se registra bajando el "
                            + "importe del pago, no lo entregado.", "pagos");
        }
        // Entregar justo no es entregar de mas: se guarda como si no se hubiera
        // dicho nada, para que una fila con `entregado` signifique siempre que
        // hubo vuelto.
        return comparacion == 0 ? null : entregado;
    }
}
