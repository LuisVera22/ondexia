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
 * La nota de crédito y el canje (doc 13 §5), con lo que cada uno prohíbe. Es la
 * prueba que acompaña a las dos fábricas de {@link DocumentoVenta}.
 */
class NotaDeCreditoTest {

    private static final Instant AHORA = Instant.parse("2026-09-09T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 9, 9);
    private static final UUID EMPRESA = UUID.randomUUID();
    private static final UUID SUCURSAL = UUID.randomUUID();
    private static final UUID SESION = UUID.randomUUID();
    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID PRODUCTO = UUID.randomUUID();

    /** Dos bolsas a 32.50: 65.00, IGV 9.92 (doc 13 §4.3). */
    private static LineaDeVenta dosBolsas() {
        return LineaDeVenta.calcular(1, PRODUCTO, "CEM-001", "Cemento", UnidadDeMedida.BG,
                AfectacionIgv.GRAVADO, new BigDecimal("2"), new BigDecimal("32.50"), null, true);
    }

    private static LineaDeVenta unaBolsa() {
        return LineaDeVenta.calcular(1, PRODUCTO, "CEM-001", "Cemento", UnidadDeMedida.BG,
                AfectacionIgv.GRAVADO, new BigDecimal("1"), new BigDecimal("32.50"), null, true);
    }

    private static DocumentoVenta boleta() {
        return DocumentoVenta.emitir(UUID.randomUUID(), EMPRESA, SUCURSAL, SESION,
                TipoDocumento.BOLETA, "B001", 12, null, HOY, AHORA, USUARIO, List.of(dosBolsas()),
                List.of(new Pago(FormaDePago.EFECTIVO, new BigDecimal("65.00"), null)), null);
    }

    private static DocumentoVenta notaDeVenta() {
        return DocumentoVenta.emitir(UUID.randomUUID(), EMPRESA, SUCURSAL, SESION,
                TipoDocumento.NOTA_VENTA, "N001", 5, null, HOY, AHORA, USUARIO,
                List.of(dosBolsas()),
                List.of(new Pago(FormaDePago.EFECTIVO, new BigDecimal("65.00"), null)), null);
    }

    /** Una boleta que SUNAT ya aceptó: es la única sobre la que se puede acreditar. */
    private static DocumentoVenta boletaAceptada() {
        var boleta = boleta();
        boleta.aceptarPorSunat();
        return boleta;
    }

    private static DocumentoVenta notaSobre(DocumentoVenta original, TipoNotaCredito motivo,
            List<LineaDeVenta> lineas, List<Pago> pagos) {
        return DocumentoVenta.notaDeCredito(UUID.randomUUID(), original, motivo, "BC01", 1, lineas,
                pagos, HOY, AHORA, USUARIO, SESION, null);
    }

    @Test
    @DisplayName("Anular copia el comprobante entero y guarda el motivo y la referencia")
    void anulacionTotal() {
        var original = boletaAceptada();

        var nota = notaSobre(original, TipoNotaCredito.ANULACION_DE_LA_OPERACION,
                original.lineas(), List.of());

        assertThat(nota.tipo()).isEqualTo(TipoDocumento.NOTA_CREDITO);
        assertThat(nota.esNotaDeCredito()).isTrue();
        assertThat(nota.esFiscal()).isTrue();
        assertThat(nota.estado()).isEqualTo(EstadoDocumento.PENDIENTE);
        assertThat(nota.total()).isEqualByComparingTo(original.total());
        assertThat(nota.totalIgv()).isEqualByComparingTo("9.92");
        assertThat(nota.motivoNota()).isEqualTo(TipoNotaCredito.ANULACION_DE_LA_OPERACION);
        assertThat(nota.documentoOrigenId()).isEqualTo(original.id());
        assertThat(nota.origen().numeroCompleto()).isEqualTo("B001-00000012");
        assertThat(nota.origen().tipo()).isEqualTo(TipoDocumento.BOLETA);
        // El comprobante no se anula al emitir la nota: se anula cuando SUNAT
        // la acepta, y de eso se encarga quien aplica el resultado.
        assertThat(original.estado()).isEqualTo(EstadoDocumento.EMITIDO);
    }

    @Test
    @DisplayName("Media anulación no existe: con un motivo que anula, la nota suma el total")
    void anulacionParcialSeRechaza() {
        var original = boletaAceptada();

        assertThatThrownBy(() -> notaSobre(original, TipoNotaCredito.ANULACION_DE_LA_OPERACION,
                List.of(unaBolsa()), List.of()))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("anulacion_parcial");
    }

    @Test
    @DisplayName("Una devolución por ítem sí acredita una parte")
    void devolucionParcial() {
        var original = boletaAceptada();

        var nota = notaSobre(original, TipoNotaCredito.DEVOLUCION_POR_ITEM, List.of(unaBolsa()),
                List.of(new Pago(FormaDePago.EFECTIVO, new BigDecimal("32.50"), null)));

        assertThat(nota.total()).isEqualByComparingTo("32.50");
        assertThat(nota.motivoNota().anulaElDocumento()).isFalse();
        assertThat(nota.motivoNota().reponeExistencias()).isTrue();
    }

    @Test
    @DisplayName("No se acredita más de lo que se cobró")
    void notaMayorQueElComprobante() {
        var original = boletaAceptada();
        var dobles = LineaDeVenta.calcular(1, PRODUCTO, "CEM-001", "Cemento", UnidadDeMedida.BG,
                AfectacionIgv.GRAVADO, new BigDecimal("4"), new BigDecimal("32.50"), null, true);

        assertThatThrownBy(() -> notaSobre(original, TipoNotaCredito.DEVOLUCION_POR_ITEM,
                List.of(dobles), List.of()))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("nota_mayor_que_el_documento");
    }

    @Test
    @DisplayName("Sobre un comprobante que SUNAT no aceptó todavía, no se emite")
    void sobreComprobantePendiente() {
        var pendiente = boleta();
        assertThat(pendiente.estado()).isEqualTo(EstadoDocumento.PENDIENTE);

        assertThatThrownBy(() -> notaSobre(pendiente, TipoNotaCredito.ANULACION_DE_LA_OPERACION,
                pendiente.lineas(), List.of()))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("documento_no_aceptado");
    }

    @Test
    @DisplayName("Una nota de venta no se anula con nota de crédito: no se declaró")
    void sobreNotaDeVenta() {
        var nota = notaDeVenta();

        assertThatThrownBy(() -> notaSobre(nota, TipoNotaCredito.ANULACION_DE_LA_OPERACION,
                nota.lineas(), List.of()))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("nota_de_credito_sobre_no_fiscal");
    }

    @Test
    @DisplayName("La nota hereda la letra de la serie del documento que modifica")
    void serieConLetraAjena() {
        var original = boletaAceptada();

        assertThatThrownBy(() -> DocumentoVenta.notaDeCredito(UUID.randomUUID(), original,
                TipoNotaCredito.ANULACION_DE_LA_OPERACION, "FC01", 1, original.lineas(), List.of(),
                HOY, AHORA, USUARIO, SESION, null))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("serie_no_corresponde");
    }

    @Test
    @DisplayName("La devolución va entera o no va: no se admite a medias sin decirlo")
    void devolucionQueNoCuadra() {
        var original = boletaAceptada();

        assertThatThrownBy(() -> notaSobre(original, TipoNotaCredito.ANULACION_DE_LA_OPERACION,
                original.lineas(),
                List.of(new Pago(FormaDePago.EFECTIVO, new BigDecimal("20.00"), null))))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("devolucion_no_cuadra");

        // Sin pagos es válido: hay motivos que no mueven dinero.
        var sinDevolucion = notaSobre(original, TipoNotaCredito.CORRECCION_POR_ERROR_EN_DESCRIPCION,
                original.lineas(), List.of());
        assertThat(sinDevolucion.pagos()).isEmpty();
    }

    @Test
    @DisplayName("Anular solo desde emitido, y una sola vez")
    void anularElDocumento() {
        var original = boletaAceptada();

        original.anularPorNotaDeCredito();
        assertThat(original.estado()).isEqualTo(EstadoDocumento.ANULADO);

        assertThatThrownBy(original::anularPorNotaDeCredito)
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("documento_no_anulable");
    }

    // ── Canje ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("El canje copia las líneas, referencia la nota de venta y no lleva pagos")
    void canjeCompleto() {
        var nota = notaDeVenta();

        var boleta = DocumentoVenta.canjear(UUID.randomUUID(), nota, TipoDocumento.BOLETA,
                "B001", 30, null, HOY, AHORA, USUARIO, null);

        assertThat(boleta.tipo()).isEqualTo(TipoDocumento.BOLETA);
        assertThat(boleta.estado()).isEqualTo(EstadoDocumento.PENDIENTE);
        assertThat(boleta.total()).isEqualByComparingTo(nota.total());
        assertThat(boleta.lineas()).hasSize(1);
        assertThat(boleta.documentoOrigenId()).isEqualTo(nota.id());
        assertThat(boleta.origen().numeroCompleto()).isEqualTo("N001-00000005");
        // El dinero entró con la nota de venta y su sesión ya lo contó.
        assertThat(boleta.pagos()).isEmpty();
        assertThat(boleta.sesionCajaId()).isEqualTo(nota.sesionCajaId());

        nota.marcarCanjeada();
        assertThat(nota.estado()).isEqualTo(EstadoDocumento.CANJEADO);
        assertThat(nota.pagos()).as("la nota conserva sus pagos").hasSize(1);
    }

    @Test
    @DisplayName("Una nota de venta ya canjeada no se canjea otra vez")
    void canjeRepetido() {
        var nota = notaDeVenta();
        nota.marcarCanjeada();

        assertThatThrownBy(() -> DocumentoVenta.canjear(UUID.randomUUID(), nota,
                TipoDocumento.BOLETA, "B001", 31, null, HOY, AHORA, USUARIO, null))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("nota_de_venta_no_canjeable");

        assertThatThrownBy(nota::marcarCanjeada)
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("nota_de_venta_no_canjeable");
    }

    @Test
    @DisplayName("Solo se canjea una nota de venta, y solo por boleta o factura")
    void canjeDeLoQueNoCorresponde() {
        var boleta = boletaAceptada();
        assertThatThrownBy(() -> DocumentoVenta.canjear(UUID.randomUUID(), boleta,
                TipoDocumento.FACTURA, "F001", 1, null, HOY, AHORA, USUARIO, null))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("canje_solo_de_nota_de_venta");

        var nota = notaDeVenta();
        assertThatThrownBy(() -> DocumentoVenta.canjear(UUID.randomUUID(), nota,
                TipoDocumento.NOTA_VENTA, "N002", 1, null, HOY, AHORA, USUARIO, null))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("tipo_de_canje_invalido");
    }

    @Test
    @DisplayName("El canje a factura exige RUC, como cualquier factura")
    void canjeAFacturaSinRuc() {
        var nota = notaDeVenta();

        assertThatThrownBy(() -> DocumentoVenta.canjear(UUID.randomUUID(), nota,
                TipoDocumento.FACTURA, "F001", 1, null, HOY, AHORA, USUARIO, null))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("factura_sin_ruc");
    }
}
