package com.ondexia.facturacion;

import com.ondexia.domain.comprobante.OrdenDeEmision;
import com.ondexia.domain.identidad.ModoSunat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Órdenes de ejemplo para las pruebas del Emisor: las cifras de doc 13 §4.3. */
public final class Ordenes {

    public static final String RUC = "20100000009";
    public static final UUID EMPRESA = UUID.fromString("00000000-0000-4000-8000-000000000010");

    private Ordenes() {
    }

    public static OrdenDeEmision.Emisor emisor() {
        return new OrdenDeEmision.Emisor(RUC, "COMERCIAL DEMO S.A.C.", "Demo",
                "Av. Siempre Viva 742, Lima", "150101", "0000", "MODDATOS");
    }

    /** Dos bolsas de cemento a 32.50 y una instalación a 80: 145.00, IGV 22.12. */
    public static OrdenDeEmision boleta(UUID id) {
        var lineas = List.of(
                new OrdenDeEmision.Linea(1, "Cemento Portland Tipo I 42.5 kg", "BG",
                        new BigDecimal("2"), new BigDecimal("32.500000"), new BigDecimal("27.542373"),
                        new BigDecimal("0.00"), "10", new BigDecimal("55.08"), new BigDecimal("9.92"),
                        new BigDecimal("65.00")),
                new OrdenDeEmision.Linea(2, "Instalación", "ZZ", new BigDecimal("1"),
                        new BigDecimal("80.000000"), new BigDecimal("67.796610"),
                        new BigDecimal("0.00"), "10", new BigDecimal("67.80"), new BigDecimal("12.20"),
                        new BigDecimal("80.00")));
        var documento = new OrdenDeEmision.Documento("03", "B001", 12, LocalDate.of(2026, 9, 8),
                LocalTime.of(10, 15, 0), "PEN",
                new OrdenDeEmision.Adquirente("1", "70123456", "Juan Perez Gomez", "Calle 1, Lima"),
                lineas, new BigDecimal("122.88"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("22.12"), new BigDecimal("145.00"), "Entrega mañana");
        return new OrdenDeEmision(id, OrdenDeEmision.Operacion.EMITIR, EMPRESA, ModoSunat.BETA,
                emisor(), documento, Instant.parse("2026-09-08T15:15:00Z"));
    }

    /** Una factura con una línea exonerada, a consumidor con RUC. */
    public static OrdenDeEmision facturaExonerada(UUID id) {
        var lineas = List.of(new OrdenDeEmision.Linea(1, "Libro", "NIU", new BigDecimal("1"),
                new BigDecimal("50.000000"), new BigDecimal("50.000000"), new BigDecimal("0.00"), "20",
                new BigDecimal("50.00"), new BigDecimal("0.00"), new BigDecimal("50.00")));
        var documento = new OrdenDeEmision.Documento("01", "F001", 7, LocalDate.of(2026, 9, 8),
                LocalTime.of(11, 0), "PEN",
                new OrdenDeEmision.Adquirente("6", "20131312955", "SUNAT", null),
                lineas, BigDecimal.ZERO, new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("50.00"), null);
        return new OrdenDeEmision(id, OrdenDeEmision.Operacion.EMITIR, EMPRESA, ModoSunat.BETA,
                emisor(), documento, Instant.parse("2026-09-08T16:00:00Z"));
    }

    /** Boleta a consumidor final sin documento. */
    public static OrdenDeEmision boletaSinCliente(UUID id) {
        var base = boleta(id).documento();
        var documento = new OrdenDeEmision.Documento(base.tipo(), base.serie(), base.numero(),
                base.fechaEmision(), base.horaEmision(), base.moneda(), null, base.lineas(),
                base.totalGravado(), base.totalExonerado(), base.totalInafecto(),
                base.totalDescuento(), base.totalIgv(), base.total(), null);
        return new OrdenDeEmision(id, OrdenDeEmision.Operacion.EMITIR, EMPRESA, ModoSunat.BETA,
                emisor(), documento, Instant.parse("2026-09-08T15:15:00Z"));
    }

    public static OrdenDeEmision verificacion(UUID id) {
        return new OrdenDeEmision(id, OrdenDeEmision.Operacion.VERIFICAR_CREDENCIALES, EMPRESA,
                ModoSunat.BETA, emisor(), null, Instant.parse("2026-09-08T15:00:00Z"));
    }
}
