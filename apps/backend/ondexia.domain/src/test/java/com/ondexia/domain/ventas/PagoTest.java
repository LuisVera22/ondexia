package com.ondexia.domain.ventas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El billete y el importe.
 *
 * <p>Observación del propietario del 2026-09-08: cobrar S/ 98 con un billete de
 * 100 es el caso más común del mostrador y hasta entonces era un error de
 * validación. Lo que se comprueba aquí es que el vuelto no contamina el importe
 * —del que dependen el comprobante ante SUNAT y el arqueo de la sesión— y que
 * solo el efectivo lo admite.
 */
class PagoTest {

    private static BigDecimal soles(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    @DisplayName("un billete de 100 sobre 98 deja el importe en 98 y dos soles de vuelto")
    void billeteDeCienSobreNoventaYOcho() {
        var pago = new Pago(FormaDePago.EFECTIVO, soles("98.00"), null, soles("100.00"));

        assertThat(pago.monto()).isEqualByComparingTo("98.00");
        assertThat(pago.entregado()).isEqualByComparingTo("100.00");
        assertThat(pago.vuelto()).isEqualByComparingTo("2.00");
        assertThat(pago.loRecibido()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("pagar justo no es entregar de más: no queda constancia de billete")
    void pagarJustoNoDejaEntregado() {
        var pago = new Pago(FormaDePago.EFECTIVO, soles("98.00"), null, soles("98.00"));

        // `entregado` en null significa «pagó justo». Guardar 98 sería decir que
        // hubo vuelto de cero, y entonces una fila con entregado ya no
        // significaría nada.
        assertThat(pago.entregado()).isNull();
        assertThat(pago.vuelto()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("sin entregado, el pago es justo y el vuelto es cero")
    void sinEntregadoElVueltoEsCero() {
        var pago = new Pago(FormaDePago.EFECTIVO, soles("32.50"), null);

        assertThat(pago.entregado()).isNull();
        assertThat(pago.vuelto()).isEqualByComparingTo("0");
        assertThat(pago.loRecibido()).isEqualByComparingTo("32.50");
    }

    @Test
    @DisplayName("una tarjeta no da vuelto")
    void soloElEfectivoDaVuelto() {
        assertThatThrownBy(
                        () -> new Pago(FormaDePago.TARJETA, soles("98.00"), "VISA 4321", soles("100.00")))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("efectivo");
    }

    @Test
    @DisplayName("lo entregado no puede quedarse por debajo del importe")
    void entregadoNoCubreElPago() {
        assertThatThrownBy(() -> new Pago(FormaDePago.EFECTIVO, soles("98.00"), null, soles("90.00")))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .satisfies(fallo -> assertThat(((ReglaDeNegocioViolada) fallo).getCodigo())
                        .isEqualTo("entregado_insuficiente"));
    }

    @Test
    @DisplayName("lo entregado se expresa en céntimos")
    void entregadoEnCentimos() {
        assertThatThrownBy(
                        () -> new Pago(FormaDePago.EFECTIVO, soles("98.00"), null, soles("100.001")))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .satisfies(fallo -> assertThat(((ReglaDeNegocioViolada) fallo).getCodigo())
                        .isEqualTo("entregado_invalido"));
    }
}
