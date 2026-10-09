package com.ondexia.consultas.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CuotaPorSolicitanteTest {

    /** Un reloj que se mueve cuando la prueba lo dice. */
    private static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-09-07T12:00:00Z");

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }
    }

    @Test
    @DisplayName("La sexta consulta de la misma identidad en una hora se rechaza; otra identidad sigue")
    void laSextaSeRechaza() {
        var reloj = new RelojManual();
        var cuota = new CuotaPorSolicitante(5, reloj);

        for (int i = 0; i < 5; i++) {
            cuota.registrar("sub-a");
        }

        assertThatThrownBy(() -> cuota.registrar("sub-a"))
                .isInstanceOf(CuotaPorSolicitante.CuotaAgotada.class)
                .hasMessageContaining("máximo de 5")
                .extracting(e -> ((CuotaPorSolicitante.CuotaAgotada) e).segundosParaReintentar())
                .isEqualTo(3600L);

        // La cuota es por identidad, no global: otro usuario no paga por el primero.
        assertThatCode(() -> cuota.registrar("sub-b")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Pasada la hora, la ventana se libera")
    void laVentanaSeLibera() {
        var reloj = new RelojManual();
        var cuota = new CuotaPorSolicitante(2, reloj);
        cuota.registrar("sub-a");
        reloj.avanzar(Duration.ofMinutes(30));
        cuota.registrar("sub-a");

        assertThatThrownBy(() -> cuota.registrar("sub-a"))
                .isInstanceOf(CuotaPorSolicitante.CuotaAgotada.class)
                // La primera vence en 30 minutos, no en una hora.
                .extracting(e -> ((CuotaPorSolicitante.CuotaAgotada) e).segundosParaReintentar())
                .isEqualTo(1800L);

        reloj.avanzar(Duration.ofMinutes(31));
        assertThatCode(() -> cuota.registrar("sub-a")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Una cuota menor que uno no tiene sentido")
    void cuotaInvalida() {
        assertThatThrownBy(() -> new CuotaPorSolicitante(0, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(CuotaPorSolicitante.VENTANA).isEqualTo(Duration.ofHours(1));
    }
}
