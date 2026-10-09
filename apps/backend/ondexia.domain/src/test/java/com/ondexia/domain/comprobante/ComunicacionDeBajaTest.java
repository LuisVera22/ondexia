package com.ondexia.domain.comprobante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La comunicación de baja (doc 13 §6): qué admite, el plazo de SUNAT y la
 * máquina de estados asíncrona, que es la que la distingue de un comprobante.
 */
class ComunicacionDeBajaTest {

    private static final Instant T0 = Instant.parse("2026-09-09T15:00:00Z");
    private static final LocalDate EMITIDAS = LocalDate.of(2026, 9, 8);
    private static final LocalDate HOY = LocalDate.of(2026, 9, 9);

    private static ComunicacionDeBaja.Renglon factura() {
        return new ComunicacionDeBaja.Renglon(UUID.randomUUID(), TipoDocumento.FACTURA, "F001", 7,
                "Emitida al cliente equivocado");
    }

    private static ComunicacionDeBaja enCola() {
        return ComunicacionDeBaja.crear(UUID.randomUUID(), UUID.randomUUID(), List.of(factura()), 1,
                EMITIDAS, HOY, UUID.randomUUID(), T0);
    }

    @Test
    @DisplayName("Nace en cola, con el identificador que SUNAT usa")
    void naceEnCola() {
        var comunicacion = enCola();
        assertThat(comunicacion.estado()).isEqualTo(EstadoSunat.EN_COLA);
        assertThat(comunicacion.intentos()).isEqualTo(1);
        assertThat(comunicacion.identificador()).isEqualTo("RA-20260909-1");
        assertThat(comunicacion.ticket()).isNull();
        assertThat(comunicacion.comprobantes()).hasSize(1);
    }

    @Test
    @DisplayName("Una boleta no se da de baja por esta vía: se anula con nota de crédito")
    void soloFacturasYSusNotas() {
        var boleta = new ComunicacionDeBaja.Renglon(UUID.randomUUID(), TipoDocumento.BOLETA,
                "B001", 12, "Error");
        assertThatThrownBy(() -> ComunicacionDeBaja.crear(UUID.randomUUID(), UUID.randomUUID(),
                List.of(boleta), 1, EMITIDAS, HOY, UUID.randomUUID(), T0))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("tipo_no_dado_de_baja");

        // Una nota de crédito de una factura sí: va con ella.
        var nota = new ComunicacionDeBaja.Renglon(UUID.randomUUID(), TipoDocumento.NOTA_CREDITO,
                "FC01", 1, "Error");
        assertThat(ComunicacionDeBaja.crear(UUID.randomUUID(), UUID.randomUUID(), List.of(nota), 1,
                EMITIDAS, HOY, UUID.randomUUID(), T0).comprobantes()).hasSize(1);
    }

    @Test
    @DisplayName("Cada comprobante lleva su motivo; sin él no se admite")
    void motivoObligatorio() {
        assertThatThrownBy(() -> new ComunicacionDeBaja.Renglon(UUID.randomUUID(),
                TipoDocumento.FACTURA, "F001", 7, "  "))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("motivo_requerido");
    }

    @Test
    @DisplayName("El plazo llega hasta el séptimo día del mes siguiente, y el mensaje dice qué hacer")
    void plazo() {
        // Emitida el 8 de septiembre: se puede comunicar hasta el 7 de octubre.
        var alLimite = ComunicacionDeBaja.crear(UUID.randomUUID(), UUID.randomUUID(),
                List.of(factura()), 1, EMITIDAS, LocalDate.of(2026, 10, 7), UUID.randomUUID(), T0);
        assertThat(alLimite.diasDePlazoDesde(LocalDate.of(2026, 10, 7))).isZero();

        assertThatThrownBy(() -> ComunicacionDeBaja.crear(UUID.randomUUID(), UUID.randomUUID(),
                List.of(factura()), 1, EMITIDAS, LocalDate.of(2026, 10, 8), UUID.randomUUID(), T0))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("fuera_de_plazo");
    }

    @Test
    @DisplayName("Enviada: SUNAT da un ticket y la comunicación queda en proceso")
    void ticket() {
        var comunicacion = enCola();

        comunicacion.anotarTicket("1554895", "documentos/x.xml", T0.plusSeconds(20));

        assertThat(comunicacion.estado()).isEqualTo(EstadoSunat.EN_PROCESO);
        assertThat(comunicacion.estado().estaEnCurso()).isTrue();
        assertThat(comunicacion.ticket()).isEqualTo("1554895");
        assertThat(comunicacion.claveXml()).isEqualTo("documentos/x.xml");
    }

    @Test
    @DisplayName("Con ticket no se reenvía: lo que falta es consultarlo")
    void conTicketNoSeReenvia() {
        var comunicacion = enCola();
        comunicacion.anotarTicket("1554895", null, T0.plusSeconds(20));

        assertThatThrownBy(() -> comunicacion.reintentar(T0.plus(Duration.ofHours(1))))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("consulta_pendiente");
    }

    @Test
    @DisplayName("La consulta resuelve: aceptada, y una respuesta tardía ya no la toca")
    void aceptada() {
        var comunicacion = enCola();
        comunicacion.anotarTicket("1554895", "documentos/x.xml", T0.plusSeconds(20));

        assertThat(comunicacion.resolver(EstadoSunat.ACEPTADO, "0", "La comunicación fue aceptada",
                "documentos/R-ticket-1554895.zip", T0.plusSeconds(90))).isTrue();
        assertThat(comunicacion.fueAceptada()).isTrue();
        assertThat(comunicacion.claveCdr()).isEqualTo("documentos/R-ticket-1554895.zip");
        assertThat(comunicacion.estado().estaEnCurso()).isFalse();

        assertThat(comunicacion.resolver(EstadoSunat.RECHAZADO, "2000", "tarde", null,
                T0.plusSeconds(120))).isFalse();
        assertThat(comunicacion.estado()).isEqualTo(EstadoSunat.ACEPTADO);
    }

    @Test
    @DisplayName("Rechazada: se reintenta y el ticket viejo se olvida")
    void rechazadaYReintento() {
        var comunicacion = enCola();
        comunicacion.anotarTicket("1554895", "documentos/x.xml", T0.plusSeconds(20));
        comunicacion.resolver(EstadoSunat.RECHAZADO, "2324", "El comprobante no existe", null,
                T0.plusSeconds(90));

        comunicacion.reintentar(T0.plusSeconds(200));

        assertThat(comunicacion.estado()).isEqualTo(EstadoSunat.EN_COLA);
        assertThat(comunicacion.intentos()).isEqualTo(2);
        assertThat(comunicacion.ticket()).isNull();
        assertThat(comunicacion.claveXml()).as("el XML se conserva").isNotNull();
    }

    @Test
    @DisplayName("En cola no se reenvía hasta que pasa la espera máxima")
    void enColaEsperaAntesDeReintentar() {
        var comunicacion = enCola();

        assertThatThrownBy(() -> comunicacion.reintentar(T0.plusSeconds(60)))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("envio_en_curso");

        comunicacion.reintentar(T0.plus(ComunicacionDeBaja.ESPERA_MAXIMA_EN_COLA));
        assertThat(comunicacion.intentos()).isEqualTo(2);
    }

    @Test
    @DisplayName("No se consulta un ticket recién dado: se espera antes de preguntar")
    void esperaEntreConsultas() {
        var comunicacion = enCola();
        comunicacion.anotarTicket("1554895", null, T0);

        assertThat(comunicacion.llevaEsperando(Duration.ofSeconds(45), T0.plusSeconds(10))).isFalse();
        assertThat(comunicacion.llevaEsperando(Duration.ofSeconds(45), T0.plusSeconds(60))).isTrue();
    }

    @Test
    @DisplayName("Una respuesta no puede dejarla en cola ni en proceso")
    void respuestaConEstadoImposible() {
        var comunicacion = enCola();
        assertThatThrownBy(() -> comunicacion.resolver(EstadoSunat.EN_COLA, "0", "x", null, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> comunicacion.resolver(EstadoSunat.EN_PROCESO, "98", "x", null, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Sin comprobantes no hay nada que comunicar")
    void sinComprobantes() {
        assertThatThrownBy(() -> ComunicacionDeBaja.crear(UUID.randomUUID(), UUID.randomUUID(),
                List.of(), 1, EMITIDAS, HOY, UUID.randomUUID(), T0))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("sin_comprobantes");
    }
}
