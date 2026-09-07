package com.ondexia.domain.ventas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El arqueo (doc 12 §3.4): el sistema calcula lo que debería haber por forma de
 * pago, quien cierra declara lo que contó, y la diferencia se guarda sin
 * corregir nada.
 */
class SesionCajaTest {

    private static final Instant AHORA = Instant.parse("2026-09-07T15:00:00Z");
    private static final UUID CAJERO = UUID.randomUUID();

    private static SesionCaja abierta(String montoInicial) {
        return SesionCaja.abrir(UUID.randomUUID(), UUID.randomUUID(), CAJERO,
                new BigDecimal(montoInicial), AHORA);
    }

    @Test
    @DisplayName("Nace abierta, con quién y cuándo, y sin cierre")
    void naceAbierta() {
        var sesion = abierta("100");

        assertThat(sesion.estaAbierta()).isTrue();
        assertThat(sesion.abiertaPor()).isEqualTo(CAJERO);
        assertThat(sesion.abiertaEn()).isEqualTo(AHORA);
        assertThat(sesion.cerradaPor()).isNull();
        assertThat(sesion.cerradaEn()).isNull();
        assertThat(sesion.declarado()).isEmpty();
        assertThat(sesion.calculado()).isEmpty();
    }

    @Test
    @DisplayName("El monto inicial no puede ser negativo; sin indicarlo, es cero")
    void montoInicial() {
        assertThatThrownBy(() -> abierta("-0.01"))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("negativo");

        var sinMonto = SesionCaja.abrir(UUID.randomUUID(), UUID.randomUUID(), CAJERO, null, AHORA);
        assertThat(sinMonto.montoInicial()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("El efectivo calculado parte del monto inicial; las demás formas, de cero")
    void calculadoPorFormaDePago() {
        var sesion = abierta("100");
        var relevo = UUID.randomUUID();

        sesion.cerrar(relevo,
                Map.of(FormaDePago.EFECTIVO, new BigDecimal("250.50"),
                        FormaDePago.TARJETA, new BigDecimal("80")),
                Map.of(FormaDePago.EFECTIVO, new BigDecimal("345"),
                        FormaDePago.TARJETA, new BigDecimal("80")),
                AHORA.plusSeconds(3600));

        assertThat(sesion.estaAbierta()).isFalse();
        assertThat(sesion.estado()).isEqualTo(SesionCaja.Estado.CERRADA);
        // Quien cierra puede no ser quien abrió: turnos y relevos existen.
        assertThat(sesion.cerradaPor()).isEqualTo(relevo);
        assertThat(sesion.cerradaEn()).isEqualTo(AHORA.plusSeconds(3600));

        assertThat(sesion.calculado().get(FormaDePago.EFECTIVO)).isEqualByComparingTo("350.50");
        assertThat(sesion.calculado().get(FormaDePago.TARJETA)).isEqualByComparingTo("80");
        assertThat(sesion.calculado().get(FormaDePago.TRANSFERENCIA)).isEqualByComparingTo("0");
        assertThat(sesion.calculado().get(FormaDePago.BILLETERA_DIGITAL)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("La diferencia es lo declarado menos lo calculado, y lo no declarado vale cero")
    void diferencia() {
        var sesion = abierta("100");

        sesion.cerrar(CAJERO,
                Map.of(FormaDePago.EFECTIVO, new BigDecimal("50"),
                        FormaDePago.TRANSFERENCIA, new BigDecimal("20")),
                // Contó 145 en efectivo (faltan 5) y no dijo nada de la transferencia.
                Map.of(FormaDePago.EFECTIVO, new BigDecimal("145")),
                AHORA.plusSeconds(60));

        var diferencia = sesion.diferencia();
        assertThat(diferencia.get(FormaDePago.EFECTIVO)).isEqualByComparingTo("-5");
        assertThat(diferencia.get(FormaDePago.TRANSFERENCIA)).isEqualByComparingTo("-20");
        assertThat(diferencia.get(FormaDePago.TARJETA)).isEqualByComparingTo("0");
        assertThat(sesion.declarado().get(FormaDePago.TRANSFERENCIA)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Lo declarado no puede ser negativo")
    void declaradoNegativo() {
        var sesion = abierta("0");

        assertThatThrownBy(() -> sesion.cerrar(CAJERO, Map.of(),
                Map.of(FormaDePago.TARJETA, new BigDecimal("-1")), AHORA))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("tarjeta");
        assertThat(sesion.estaAbierta()).isTrue();
    }

    @Test
    @DisplayName("Una sesión cerrada no se vuelve a cerrar")
    void cerradaEsFinal() {
        var sesion = abierta("10");
        sesion.cerrar(CAJERO, Map.of(), Map.of(), AHORA);

        assertThatThrownBy(() -> sesion.cerrar(CAJERO, Map.of(), Map.of(), AHORA))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("cerrada");
    }
}
