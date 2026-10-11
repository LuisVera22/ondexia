package com.ondexia.domain.comprobante;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F17: controles vigentes; no acredita correlación de respuestas por intento. */
class ReintentosYDuplicadosEmisionTest {
    private static final UUID COMPROBANTE = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID EMPRESA = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID DOCUMENTO = UUID.fromString("10000000-0000-4000-8000-000000000003");
    private static final Instant INICIO = Instant.parse("2026-10-11T12:00:00Z");
    private static final Instant RESPUESTA = Instant.parse("2026-10-11T12:00:30Z");
    private static final List<String> OBSERVACIONES = List.of("4252 - Observación original");

    @ParameterizedTest
    @CsvSource({
        "ACEPTADO,ACEPTADO", "ACEPTADO,RECHAZADO", "ACEPTADO,ERROR_ENVIO",
        "RECHAZADO,ACEPTADO", "RECHAZADO,RECHAZADO", "RECHAZADO,ERROR_ENVIO",
        "ERROR_ENVIO,ACEPTADO", "ERROR_ENVIO,RECHAZADO", "ERROR_ENVIO,ERROR_ENVIO"
    })
    void otraRespuestaNoSobrescribeLaRegistrada(EstadoSunat original, EstadoSunat posterior) {
        var comprobante = enCola();
        comprobante.aplicar(respuestaOriginal(original));
        var otra = new ResultadoDeEmision(COMPROBANTE, EMPRESA, OrdenDeEmision.Operacion.EMITIR,
                posterior, codigo(posterior), "Otra respuesta", List.of("Otra observación"),
                "otro.xml", "otro.zip", "otro-resumen", null, null,
                Instant.parse("2026-10-11T12:00:45Z"));

        assertThat(comprobante.aplicar(otra)).isFalse();
        exigirRespuestaOriginal(comprobante, original);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoSunat.class, names = {"ACEPTADO", "ANULADO"})
    void estadoFinalNoAdmiteReintentoNiCambiaDatos(EstadoSunat estado) {
        var comprobante = ComprobanteElectronico.reconstruir(COMPROBANTE, EMPRESA, DOCUMENTO,
                TipoDocumento.FACTURA, "F001", 42, estado, 1, INICIO, RESPUESTA, codigo(estado),
                "Respuesta original", OBSERVACIONES, "original.xml", "original.zip", "resumen-original");

        exigirRechazoDeReintento(comprobante, INICIO.plusSeconds(86400), "reintento_no_admitido");
        exigirRespuestaOriginal(comprobante, estado);
    }

    @ParameterizedTest
    @CsvSource({"599999999999,false", "600000000000,true", "600000000001,true"})
    void fronteraDeEsperaEsDiezMinutos(long nanosegundos, boolean permitido) {
        var comprobante = enCola();
        var instante = INICIO.plusNanos(nanosegundos);
        if (permitido) {
            comprobante.reintentar(instante);
            exigirEnCola(comprobante, 2, instante);
        } else {
            exigirRechazoDeReintento(comprobante, instante, "emision_en_curso");
            exigirEnCola(comprobante, 1, INICIO);
        }
    }

    @Test
    void reintentoReiniciaLaEsperaDesdeElUltimoEncolado() {
        var comprobante = enCola();
        var segundoIntento = Instant.parse("2026-10-11T12:10:00Z");
        comprobante.reintentar(segundoIntento);

        exigirRechazoDeReintento(comprobante,
                Instant.parse("2026-10-11T12:10:01Z"), "emision_en_curso");
        exigirEnCola(comprobante, 2, segundoIntento);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoSunat.class, names = {"RECHAZADO", "ERROR_ENVIO"})
    void falloAdmiteReintentoYConservaIdentidadYXml(EstadoSunat fallo) {
        var comprobante = enCola();
        comprobante.aplicar(respuestaOriginal(fallo));
        var instante = Instant.parse("2026-10-11T12:00:45Z");

        comprobante.reintentar(instante);

        exigirIdentidad(comprobante);
        exigirEnCola(comprobante, 2, instante);
        assertThat(comprobante.claveXml()).isEqualTo("original.xml");
        assertThat(comprobante.resumenFirma()).isEqualTo("resumen-original");
    }

    @Test
    void esperaConfiguradaCoincideConLaReglaDocumentada() {
        assertThat(ComprobanteElectronico.ESPERA_MAXIMA_EN_COLA).isEqualTo(Duration.ofMinutes(10));
    }

    private static ComprobanteElectronico enCola() {
        return ComprobanteElectronico.encolar(COMPROBANTE, EMPRESA, DOCUMENTO,
                TipoDocumento.FACTURA, "F001", 42, INICIO);
    }

    private static ResultadoDeEmision respuestaOriginal(EstadoSunat estado) {
        return new ResultadoDeEmision(COMPROBANTE, EMPRESA, OrdenDeEmision.Operacion.EMITIR,
                estado, codigo(estado), "Respuesta original", OBSERVACIONES,
                "original.xml", estado == EstadoSunat.ERROR_ENVIO ? null : "original.zip",
                "resumen-original", null, null, RESPUESTA);
    }

    private static String codigo(EstadoSunat estado) {
        return switch (estado) {
            case ACEPTADO, ANULADO -> "0";
            case RECHAZADO -> "2335";
            case ERROR_ENVIO -> "0100";
            default -> throw new IllegalArgumentException("Estado no previsto por la prueba");
        };
    }

    private static void exigirRechazoDeReintento(ComprobanteElectronico comprobante,
            Instant instante, String codigoEsperado) {
        assertThatThrownBy(() -> comprobante.reintentar(instante))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo(codigoEsperado);
    }

    private static void exigirIdentidad(ComprobanteElectronico comprobante) {
        assertThat(comprobante.id()).isEqualTo(COMPROBANTE);
        assertThat(comprobante.empresaId()).isEqualTo(EMPRESA);
        assertThat(comprobante.documentoId()).isEqualTo(DOCUMENTO);
        assertThat(comprobante.tipo()).isEqualTo(TipoDocumento.FACTURA);
        assertThat(comprobante.serie()).isEqualTo("F001");
        assertThat(comprobante.numero()).isEqualTo(42);
    }

    private static void exigirRespuestaOriginal(ComprobanteElectronico comprobante, EstadoSunat estado) {
        exigirIdentidad(comprobante);
        assertThat(comprobante.estado()).isEqualTo(estado);
        assertThat(comprobante.intentos()).isEqualTo(1);
        assertThat(comprobante.encoladoEn()).isEqualTo(INICIO);
        assertThat(comprobante.respondidoEn()).isEqualTo(RESPUESTA);
        assertThat(comprobante.codigoSunat()).isEqualTo(codigo(estado));
        assertThat(comprobante.descripcionSunat()).isEqualTo("Respuesta original");
        assertThat(comprobante.observaciones()).containsExactly("4252 - Observación original");
        assertThat(comprobante.claveXml()).isEqualTo("original.xml");
        assertThat(comprobante.claveCdr())
                .isEqualTo(estado == EstadoSunat.ERROR_ENVIO ? null : "original.zip");
        assertThat(comprobante.resumenFirma()).isEqualTo("resumen-original");
    }

    private static void exigirEnCola(ComprobanteElectronico comprobante, int intentos, Instant instante) {
        assertThat(comprobante.estado()).isEqualTo(EstadoSunat.EN_COLA);
        assertThat(comprobante.intentos()).isEqualTo(intentos);
        assertThat(comprobante.encoladoEn()).isEqualTo(instante);
        assertThat(comprobante.respondidoEn()).isNull();
        assertThat(comprobante.claveCdr()).isNull();
    }
}
