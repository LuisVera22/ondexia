package com.ondexia.domain.comprobante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IdentidadResultadoEmisionTest {
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID EMPRESA = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID OTRA = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final Instant MOMENTO = Instant.parse("2026-10-11T03:00:00Z");

    static Stream<Arguments> incompatibles() {
        return Stream.of(EstadoSunat.ACEPTADO, EstadoSunat.RECHAZADO, EstadoSunat.ERROR_ENVIO)
                .flatMap(estado -> Stream.of(
                        Arguments.of(OTRA, OrdenDeEmision.Operacion.EMITIR, estado),
                        Arguments.of(null, OrdenDeEmision.Operacion.EMITIR, estado),
                        Arguments.of(EMPRESA, OrdenDeEmision.Operacion.VERIFICAR_CREDENCIALES, estado),
                        Arguments.of(EMPRESA, OrdenDeEmision.Operacion.ENVIAR_BAJA, estado),
                        Arguments.of(EMPRESA, OrdenDeEmision.Operacion.CONSULTAR_TICKET, estado),
                        Arguments.of(EMPRESA, null, estado)));
    }

    @ParameterizedTest
    @MethodSource("incompatibles")
    void resultadoIncompatibleNoModificaNingunDato(UUID empresa, OrdenDeEmision.Operacion operacion,
            EstadoSunat estado) {
        var comprobante = ComprobanteElectronico.reconstruir(ID, EMPRESA, OTRA,
                TipoDocumento.FACTURA, "F001", 15, EstadoSunat.EN_COLA, 2, MOMENTO,
                null, "error-anterior", "Descripción anterior", List.of("Nota anterior"),
                "xml-anterior", null, "firma-anterior");
        var resultado = resultado(empresa, operacion, estado);

        assertThatThrownBy(() -> comprobante.aplicar(resultado))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(comprobante.estado()).isEqualTo(EstadoSunat.EN_COLA);
        assertThat(comprobante.respondidoEn()).isNull();
        assertThat(comprobante.codigoSunat()).isEqualTo("error-anterior");
        assertThat(comprobante.descripcionSunat()).isEqualTo("Descripción anterior");
        assertThat(comprobante.observaciones()).containsExactly("Nota anterior");
        assertThat(comprobante.claveXml()).isEqualTo("xml-anterior");
        assertThat(comprobante.claveCdr()).isNull();
        assertThat(comprobante.resumenFirma()).isEqualTo("firma-anterior");
        assertThat(comprobante.intentos()).isEqualTo(2);
        assertThat(comprobante.encoladoEn()).isEqualTo(MOMENTO);
    }

    static Stream<EstadoSunat> estadosValidos() {
        return Stream.of(EstadoSunat.ACEPTADO, EstadoSunat.RECHAZADO, EstadoSunat.ERROR_ENVIO);
    }

    @ParameterizedTest
    @MethodSource("estadosValidos")
    void resultadoPropioMantieneLasTransicionesValidas(EstadoSunat estado) {
        var comprobante = ComprobanteElectronico.encolar(ID, EMPRESA, OTRA,
                TipoDocumento.FACTURA, "F001", 15, MOMENTO);
        assertThat(comprobante.aplicar(resultado(EMPRESA, OrdenDeEmision.Operacion.EMITIR, estado))).isTrue();
        assertThat(comprobante.estado()).isEqualTo(estado);
        assertThat(comprobante.codigoSunat()).isEqualTo(switch (estado) {
            case ACEPTADO -> "0";
            case RECHAZADO -> "2335";
            case ERROR_ENVIO -> "SIN_CONEXION";
            default -> throw new AssertionError("Estado fuera de este caso");
        });
        assertThat(comprobante.respondidoEn()).isEqualTo(MOMENTO.plusSeconds(30));
        assertThat(comprobante.claveXml()).isEqualTo("xml-nuevo");
        assertThat(comprobante.claveCdr()).isEqualTo("cdr-nuevo");
    }

    @Test
    void otroComprobanteDeLaMismaEmpresaNoSeAplica() {
        var comprobante = ComprobanteElectronico.encolar(ID, EMPRESA, OTRA,
                TipoDocumento.FACTURA, "F001", 15, MOMENTO);
        var resultado = new ResultadoDeEmision(OTRA, EMPRESA, OrdenDeEmision.Operacion.EMITIR,
                EstadoSunat.ACEPTADO, "0", "Aceptación sintética", List.of(), "xml-ajeno",
                "cdr-ajeno", "firma-ajena", null, null, MOMENTO.plusSeconds(30));
        assertThatThrownBy(() -> comprobante.aplicar(resultado)).isInstanceOf(IllegalArgumentException.class);
        assertThat(comprobante.estado()).isEqualTo(EstadoSunat.EN_COLA);
        assertThat(comprobante.claveXml()).isNull();
    }

    private static ResultadoDeEmision resultado(UUID empresa, OrdenDeEmision.Operacion operacion,
            EstadoSunat estado) {
        String codigo = switch (estado) {
            case ACEPTADO -> "0";
            case RECHAZADO -> "2335";
            case ERROR_ENVIO -> "SIN_CONEXION";
            default -> throw new AssertionError("Estado fuera de este fixture");
        };
        return new ResultadoDeEmision(ID, empresa, operacion, estado, codigo,
                "Descripción nueva", List.of("Nota nueva"), "xml-nuevo", "cdr-nuevo",
                "firma-nueva", null, null, MOMENTO.plusSeconds(30));
    }
}
