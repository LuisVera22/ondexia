package com.ondexia.domain.comprobante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La máquina de estados de doc 14 §3, transición por transición, incluidas las
 * prohibidas. Es la prueba que la regla 1 de CLAUDE.md pide para el comentario
 * de {@link ComprobanteElectronico}.
 */
class ComprobanteElectronicoTest {

    private static final Instant T0 = Instant.parse("2026-09-08T15:00:00Z");

    private static ComprobanteElectronico enCola() {
        return ComprobanteElectronico.encolar(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), TipoDocumento.BOLETA, "B001", 12, T0);
    }

    private static ResultadoDeEmision resultado(ComprobanteElectronico c, EstadoSunat estado,
            String codigo, String cdr) {
        return new ResultadoDeEmision(c.id(), c.empresaId(), OrdenDeEmision.Operacion.EMITIR, estado, codigo,
                "descripción", List.of("4252 - observación"), "documentos/x.xml", cdr, "abc=",
                null, null, T0.plusSeconds(30));
    }

    @Test
    @DisplayName("Nace en cola con un intento y sin respuesta")
    void naceEnCola() {
        var c = enCola();
        assertThat(c.estado()).isEqualTo(EstadoSunat.EN_COLA);
        assertThat(c.intentos()).isEqualTo(1);
        assertThat(c.encoladoEn()).isEqualTo(T0);
        assertThat(c.respondidoEn()).isNull();
    }

    @Test
    @DisplayName("Una nota de venta no se encola: no se declara")
    void notaDeVentaNoSeEncola() {
        assertThatThrownBy(() -> ComprobanteElectronico.encolar(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), TipoDocumento.NOTA_VENTA, "N001", 1, T0))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("no se declara");
    }

    @Test
    @DisplayName("Aceptado: guarda CDR, código, observaciones y resumen de la firma")
    void aceptado() {
        var c = enCola();
        assertThat(c.aplicar(resultado(c, EstadoSunat.ACEPTADO, "0", "documentos/R-x.zip"))).isTrue();
        assertThat(c.estado()).isEqualTo(EstadoSunat.ACEPTADO);
        assertThat(c.estaAceptado()).isTrue();
        assertThat(c.codigoSunat()).isEqualTo("0");
        assertThat(c.claveCdr()).isEqualTo("documentos/R-x.zip");
        assertThat(c.claveXml()).isEqualTo("documentos/x.xml");
        assertThat(c.resumenFirma()).isEqualTo("abc=");
        assertThat(c.observaciones()).containsExactly("4252 - observación");
        assertThat(c.respondidoEn()).isEqualTo(T0.plusSeconds(30));
    }

    @Test
    @DisplayName("Un aceptado no se vuelve a enviar ni acepta otro resultado")
    void aceptadoEsFinal() {
        var c = enCola();
        c.aplicar(resultado(c, EstadoSunat.ACEPTADO, "0", "cdr"));

        assertThatThrownBy(() -> c.reintentar(T0.plus(Duration.ofDays(1))))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("reintento_no_admitido");
        // Un resultado tardío no pisa lo que ya se sabe.
        assertThat(c.aplicar(resultado(c, EstadoSunat.RECHAZADO, "2335", null))).isFalse();
        assertThat(c.estado()).isEqualTo(EstadoSunat.ACEPTADO);
    }

    @Test
    @DisplayName("Rechazado y error de envío vuelven a la cola al reintentar, contando el intento")
    void rechazadoYErrorSeReintentan() {
        for (EstadoSunat fallo : List.of(EstadoSunat.RECHAZADO, EstadoSunat.ERROR_ENVIO)) {
            var c = enCola();
            c.aplicar(resultado(c, fallo, "2335", null));
            assertThat(c.estado()).isEqualTo(fallo);
            assertThat(c.claveXml()).as("el XML se conserva para entender el fallo").isNotNull();

            c.reintentar(T0.plusSeconds(60));
            assertThat(c.estado()).isEqualTo(EstadoSunat.EN_COLA);
            assertThat(c.intentos()).isEqualTo(2);
            assertThat(c.respondidoEn()).isNull();
            assertThat(c.claveCdr()).isNull();
        }
    }

    @Test
    @DisplayName("En cola no se reintenta hasta que pasa la espera máxima")
    void enColaEsperaAntesDeReintentar() {
        var c = enCola();
        assertThatThrownBy(() -> c.reintentar(T0.plusSeconds(60)))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("emision_en_curso");

        c.reintentar(T0.plus(ComprobanteElectronico.ESPERA_MAXIMA_EN_COLA));
        assertThat(c.intentos()).isEqualTo(2);
    }

    @Test
    @DisplayName("Un resultado de otro comprobante no se aplica")
    void resultadoAjeno() {
        var c = enCola();
        var otro = enCola();
        assertThatThrownBy(() -> c.aplicar(resultado(otro, EstadoSunat.ACEPTADO, "0", "cdr")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Un resultado no puede dejar el comprobante en cola ni anulado")
    void resultadoConEstadoImposible() {
        var c = enCola();
        assertThatThrownBy(() -> c.aplicar(resultado(c, EstadoSunat.EN_COLA, "0", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> c.aplicar(resultado(c, EstadoSunat.ANULADO, "0", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
