package com.ondexia.domain.ventas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.almacen.AfectacionIgv;
import com.ondexia.domain.almacen.UnidadDeMedida;
import com.ondexia.domain.comprobante.TipoDocumento;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La aritmética del comprobante y las tres reglas de §3.2 del doc 12, con las
 * pruebas que ese apartado pide por nombre.
 */
class DocumentoVentaTest {

    private static final UUID EMPRESA = UUID.randomUUID();
    private static final UUID LOCAL = UUID.randomUUID();
    private static final UUID SESION = UUID.randomUUID();
    private static final UUID CAJERO = UUID.randomUUID();
    private static final Instant AHORA = Instant.parse("2026-09-07T20:00:00Z");

    private static LineaDeVenta gravada(int orden, String cantidad, String precio) {
        return LineaDeVenta.calcular(orden, UUID.randomUUID(), "P-" + orden, "Producto " + orden,
                UnidadDeMedida.NIU, AfectacionIgv.GRAVADO, new BigDecimal(cantidad),
                new BigDecimal(precio), null, true);
    }

    private static Cliente conDni() {
        return new Cliente(UUID.randomUUID(), EMPRESA, TipoDocumentoIdentidad.DNI, "45678912",
                "Rosa Quispe", null, null, null);
    }

    private static Cliente conRuc() {
        return new Cliente(UUID.randomUUID(), EMPRESA, TipoDocumentoIdentidad.RUC, "20601030013",
                "EMPRESA S.A.C.", null, null, null);
    }

    private static DocumentoVenta emitir(TipoDocumento tipo, Cliente cliente,
            List<LineaDeVenta> lineas, List<Pago> pagos) {
        return DocumentoVenta.emitir(UUID.randomUUID(), EMPRESA, LOCAL, SESION, tipo, "N001", 1,
                cliente, LocalDate.of(2026, 9, 7), AHORA, CAJERO, lineas, pagos, null);
    }

    private static List<Pago> efectivo(String monto) {
        return List.of(new Pago(FormaDePago.EFECTIVO, new BigDecimal(monto), null));
    }

    @Test
    @DisplayName("El total es cantidad por precio con IGV; el valor de venta y el IGV salen de él")
    void aritmeticaDeLaLinea() {
        // Dos bolsas de cemento a S/ 32.50 cuestan S/ 65.00 exactos. El valor de
        // venta es 65 / 1.18 = 55.08 y el IGV la diferencia, 9.92; calcularlo
        // al revés daría 64.99, un céntimo que el cliente no acepta.
        var linea = gravada(1, "2", "32.50");

        assertThat(linea.valorUnitario()).isEqualByComparingTo("27.542373");
        assertThat(linea.valorVenta()).isEqualByComparingTo("55.08");
        assertThat(linea.igv()).isEqualByComparingTo("9.92");
        assertThat(linea.total()).isEqualByComparingTo("65.00");

        var exonerada = LineaDeVenta.calcular(2, UUID.randomUUID(), "X", "Libro", UnidadDeMedida.NIU,
                AfectacionIgv.EXONERADO, BigDecimal.ONE, new BigDecimal("40"), new BigDecimal("5"), false);
        assertThat(exonerada.valorUnitario()).isEqualByComparingTo("40");
        assertThat(exonerada.valorVenta()).isEqualByComparingTo("35");
        assertThat(exonerada.igv()).isEqualByComparingTo("0");

        var documento = emitir(TipoDocumento.NOTA_VENTA, null, List.of(linea, exonerada), efectivo("100.00"));
        assertThat(documento.totalGravado()).isEqualByComparingTo("55.08");
        assertThat(documento.totalExonerado()).isEqualByComparingTo("35");
        assertThat(documento.totalIgv()).isEqualByComparingTo("9.92");
        assertThat(documento.totalDescuento()).isEqualByComparingTo("5");
        assertThat(documento.total()).isEqualByComparingTo("100.00");
        assertThat(documento.numeroCompleto()).isEqualTo("N001-00000001");
        assertThat(documento.estado()).isEqualTo(EstadoDocumento.EMITIDO);
        assertThat(documento.esFiscal()).isFalse();
    }

    @Test
    @DisplayName("Una boleta de S/ 701 sin DNI no se emite, y el mensaje dice el motivo y la cifra")
    void unaBoletaDe701SolesSinDniNoSeEmite() {
        var lineas = List.of(gravada(1, "1", "701"));

        assertThatThrownBy(() -> emitir(TipoDocumento.BOLETA, null, lineas, efectivo("701")))
                .isInstanceOfSatisfying(ReglaDeNegocioViolada.class, error -> {
                    assertThat(error.getCodigo()).isEqualTo("boleta_exige_adquirente");
                    assertThat(error.getMessage()).contains("700").contains("701");
                    assertThat(error.getCampo()).isEqualTo("clienteId");
                });

        // Con el adquirente identificado sí, y queda pendiente de SUNAT.
        var boleta = emitir(TipoDocumento.BOLETA, conDni(), lineas, efectivo("701"));
        assertThat(boleta.estado()).isEqualTo(EstadoDocumento.PENDIENTE);
        assertThat(boleta.esFiscal()).isTrue();

        // Hasta S/ 700, sin documento.
        assertThat(emitir(TipoDocumento.BOLETA, null, List.of(gravada(1, "1", "700")), efectivo("700")))
                .isNotNull();
    }

    @Test
    @DisplayName("Una factura a un cliente sin RUC no se emite")
    void unaFacturaSinRucNoSeEmite() {
        var lineas = List.of(gravada(1, "1", "100"));

        assertThatThrownBy(() -> emitir(TipoDocumento.FACTURA, conDni(), lineas, efectivo("100")))
                .isInstanceOfSatisfying(ReglaDeNegocioViolada.class,
                        error -> assertThat(error.getCodigo()).isEqualTo("factura_sin_ruc"));
        assertThatThrownBy(() -> emitir(TipoDocumento.FACTURA, null, lineas, efectivo("100")))
                .isInstanceOf(ReglaDeNegocioViolada.class);

        assertThat(emitir(TipoDocumento.FACTURA, conRuc(), lineas, efectivo("100")).esFiscal()).isTrue();
    }

    @Test
    @DisplayName("Los pagos suman exactamente el total; con varios es pago mixto")
    void pagosCuadrados() {
        var lineas = List.of(gravada(1, "1", "100"));

        assertThatThrownBy(() -> emitir(TipoDocumento.NOTA_VENTA, null, lineas, efectivo("99")))
                .isInstanceOfSatisfying(ReglaDeNegocioViolada.class,
                        error -> assertThat(error.getCodigo()).isEqualTo("pagos_no_cuadran"));
        assertThatThrownBy(() -> emitir(TipoDocumento.NOTA_VENTA, null, lineas, List.of()))
                .isInstanceOfSatisfying(ReglaDeNegocioViolada.class,
                        error -> assertThat(error.getCodigo()).isEqualTo("sin_pagos"));

        var mixto = emitir(TipoDocumento.NOTA_VENTA, null, lineas, List.of(
                new Pago(FormaDePago.EFECTIVO, new BigDecimal("40"), null),
                new Pago(FormaDePago.TARJETA, new BigDecimal("60"), "VISA 4321")));
        assertThat(mixto.pagos()).hasSize(2);
    }

    @Test
    @DisplayName("Sin líneas, con cantidad cero o con descuento mayor que la línea, no hay documento")
    void lineasInvalidas() {
        assertThatThrownBy(() -> emitir(TipoDocumento.NOTA_VENTA, null, List.of(), efectivo("1")))
                .isInstanceOfSatisfying(ReglaDeNegocioViolada.class,
                        error -> assertThat(error.getCodigo()).isEqualTo("sin_lineas"));
        assertThatThrownBy(() -> gravada(1, "0", "10"))
                .isInstanceOf(ReglaDeNegocioViolada.class);
        assertThatThrownBy(() -> LineaDeVenta.calcular(1, UUID.randomUUID(), "X", "X", UnidadDeMedida.NIU,
                AfectacionIgv.GRAVADO, BigDecimal.ONE, new BigDecimal("10"), new BigDecimal("50"), true))
                .isInstanceOf(ReglaDeNegocioViolada.class);
    }

    @Test
    @DisplayName("La nota de venta no está en el catálogo 01 y su serie empieza por N")
    void notaDeVentaFueraDelCatalogo() {
        assertThat(TipoDocumento.NOTA_VENTA.esFiscal()).isFalse();
        assertThat(TipoDocumento.BOLETA.esFiscal()).isTrue();
        assertThat(TipoDocumento.porCodigo("NV")).isEqualTo(TipoDocumento.NOTA_VENTA);
        assertThat(TipoDocumento.NOTA_VENTA.validarSerie("n001")).isEqualTo("N001");
        assertThatThrownBy(() -> TipoDocumento.NOTA_VENTA.validarSerie("B001"))
                .isInstanceOf(ReglaDeNegocioViolada.class);
    }
}
