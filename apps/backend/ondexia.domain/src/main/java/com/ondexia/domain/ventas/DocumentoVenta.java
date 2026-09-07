package com.ondexia.domain.ventas;

import com.ondexia.domain.almacen.AfectacionIgv;
import com.ondexia.domain.comprobante.TipoDocumento;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Una venta: nota de venta, boleta o factura (doc 12 §3.2 y §3.3).
 *
 * <p>Nace completa e inmutable: líneas, totales, pagos, cliente y sesión de
 * caja se fijan al emitir, y después solo cambia el estado. Lo que decide qué
 * documento puede ser cada venta está aquí y en ningún otro sitio:
 *
 * <ul>
 *   <li>Una factura exige un cliente con RUC.</li>
 *   <li>Una boleta admite cliente con documento o ninguno, salvo que el total
 *       supere {@link #TOPE_BOLETA_SIN_DOCUMENTO}: entonces hay que identificar
 *       al adquirente (Reglamento de Comprobantes de Pago, art. 8, 3.10).</li>
 *   <li>Una nota de venta no es comprobante de pago: cliente libre, y no se
 *       declara a SUNAT.</li>
 *   <li>Los pagos suman exactamente el total. Con varios, es pago mixto.</li>
 * </ul>
 */
public class DocumentoVenta {

    public static final BigDecimal TASA_IGV = new BigDecimal("0.18");
    public static final BigDecimal FACTOR_IGV = new BigDecimal("1.18");
    /** S/ 700: por encima, la boleta identifica al adquirente. */
    public static final BigDecimal TOPE_BOLETA_SIN_DOCUMENTO = new BigDecimal("700");

    private final UUID id;
    private final UUID empresaId;
    private final UUID sucursalId;
    private final UUID sesionCajaId;
    private final TipoDocumento tipo;
    private final String serie;
    private final long numero;
    private final Cliente cliente;
    private final LocalDate fechaEmision;
    private final Instant emitidoEn;
    private final UUID emitidoPor;
    private final List<LineaDeVenta> lineas;
    private final List<Pago> pagos;
    private final String observaciones;
    private final UUID documentoOrigenId;
    private EstadoDocumento estado;

    private final BigDecimal totalGravado;
    private final BigDecimal totalExonerado;
    private final BigDecimal totalInafecto;
    private final BigDecimal totalDescuento;
    private final BigDecimal totalIgv;
    private final BigDecimal total;

    private DocumentoVenta(UUID id, UUID empresaId, UUID sucursalId, UUID sesionCajaId,
            TipoDocumento tipo, String serie, long numero, Cliente cliente,
            LocalDate fechaEmision, Instant emitidoEn, UUID emitidoPor, List<LineaDeVenta> lineas,
            List<Pago> pagos, String observaciones, UUID documentoOrigenId,
            EstadoDocumento estado) {
        this.id = id;
        this.empresaId = empresaId;
        this.sucursalId = sucursalId;
        this.sesionCajaId = sesionCajaId;
        this.tipo = tipo;
        this.serie = serie;
        this.numero = numero;
        this.cliente = cliente;
        this.fechaEmision = fechaEmision;
        this.emitidoEn = emitidoEn;
        this.emitidoPor = emitidoPor;
        this.lineas = List.copyOf(lineas);
        this.pagos = List.copyOf(pagos);
        this.observaciones = observaciones;
        this.documentoOrigenId = documentoOrigenId;
        this.estado = estado;

        this.totalGravado = suma(AfectacionIgv.GRAVADO);
        this.totalExonerado = suma(AfectacionIgv.EXONERADO);
        this.totalInafecto = suma(AfectacionIgv.INAFECTO);
        this.totalDescuento = this.lineas.stream().map(LineaDeVenta::descuento)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        this.totalIgv = this.lineas.stream().map(LineaDeVenta::igv)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        this.total = this.lineas.stream().map(LineaDeVenta::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Emite el documento: aplica las reglas del tipo, cuadra los pagos y fija el
     * estado inicial.
     *
     * @param cliente {@code null} para el adquirente sin documento
     */
    public static DocumentoVenta emitir(UUID id, UUID empresaId, UUID sucursalId, UUID sesionCajaId,
            TipoDocumento tipo, String serie, long numero, Cliente cliente, LocalDate fechaEmision,
            Instant emitidoEn, UUID emitidoPor, List<LineaDeVenta> lineas, List<Pago> pagos,
            String observaciones) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(empresaId, "empresaId");
        Objects.requireNonNull(sucursalId, "sucursalId");
        Objects.requireNonNull(sesionCajaId, "sesionCajaId");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(emitidoPor, "emitidoPor");
        if (tipo != TipoDocumento.NOTA_VENTA && tipo != TipoDocumento.BOLETA
                && tipo != TipoDocumento.FACTURA) {
            throw new ReglaDeNegocioViolada(
                    "tipo_no_vendible",
                    "Desde el punto de venta se emiten notas de venta, boletas y facturas.");
        }
        if (lineas == null || lineas.isEmpty()) {
            throw new ReglaDeNegocioViolada(
                    "sin_lineas", "Un documento de venta necesita al menos una línea.", "lineas");
        }

        var documento = new DocumentoVenta(id, empresaId, sucursalId, sesionCajaId, tipo,
                serie, numero, cliente, fechaEmision, emitidoEn, emitidoPor, lineas,
                pagos == null ? List.of() : pagos, limpiar(observaciones), null,
                tipo.esFiscal() ? EstadoDocumento.PENDIENTE : EstadoDocumento.EMITIDO);
        documento.exigirAdquirente();
        documento.exigirPagosCuadrados();
        return documento;
    }

    /** Reconstrucción desde la persistencia: no valida. */
    public static DocumentoVenta reconstruir(UUID id, UUID empresaId, UUID sucursalId,
            UUID sesionCajaId, TipoDocumento tipo, String serie, long numero, Cliente cliente,
            LocalDate fechaEmision, Instant emitidoEn, UUID emitidoPor, List<LineaDeVenta> lineas,
            List<Pago> pagos, String observaciones, UUID documentoOrigenId,
            EstadoDocumento estado) {
        return new DocumentoVenta(id, empresaId, sucursalId, sesionCajaId, tipo, serie, numero,
                cliente, fechaEmision, emitidoEn, emitidoPor, lineas, pagos, observaciones,
                documentoOrigenId, estado);
    }

    private void exigirAdquirente() {
        switch (tipo) {
            case FACTURA -> {
                if (cliente == null || !cliente.admiteFactura()) {
                    throw new ReglaDeNegocioViolada(
                            "factura_sin_ruc",
                            "Una factura exige un cliente con RUC. Con DNI o sin documento "
                                    + "corresponde una boleta.", "clienteId");
                }
            }
            case BOLETA -> {
                if (cliente == null && total.compareTo(TOPE_BOLETA_SIN_DOCUMENTO) > 0) {
                    throw new ReglaDeNegocioViolada(
                            "boleta_exige_adquirente",
                            "Una boleta de más de S/ 700 tiene que identificar al adquirente "
                                    + "(esta suma S/ " + total.toPlainString() + "). Elige o "
                                    + "registra al cliente.", "clienteId");
                }
            }
            default -> {
                // La nota de venta no es comprobante de pago: cliente libre.
            }
        }
    }

    private void exigirPagosCuadrados() {
        if (pagos.isEmpty()) {
            throw new ReglaDeNegocioViolada(
                    "sin_pagos", "Indica cómo se cobró: al menos una forma de pago.", "pagos");
        }
        BigDecimal cobrado = pagos.stream().map(Pago::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        if (cobrado.compareTo(total) != 0) {
            throw new ReglaDeNegocioViolada(
                    "pagos_no_cuadran",
                    "Los pagos suman S/ " + cobrado.toPlainString() + " y el documento S/ "
                            + total.toPlainString() + ". Tienen que coincidir.", "pagos");
        }
    }

    private BigDecimal suma(AfectacionIgv afectacion) {
        return lineas.stream()
                .filter(linea -> linea.afectacion() == afectacion)
                .map(LineaDeVenta::valorVenta)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    public String numeroCompleto() {
        return serie + "-" + String.format("%08d", numero);
    }

    public boolean esFiscal() {
        return tipo.esFiscal();
    }

    public UUID id() {
        return id;
    }

    public UUID empresaId() {
        return empresaId;
    }

    public UUID sucursalId() {
        return sucursalId;
    }

    public UUID sesionCajaId() {
        return sesionCajaId;
    }

    public TipoDocumento tipo() {
        return tipo;
    }

    public String serie() {
        return serie;
    }

    public long numero() {
        return numero;
    }

    /** {@code null}: adquirente sin documento. */
    public Cliente cliente() {
        return cliente;
    }

    public LocalDate fechaEmision() {
        return fechaEmision;
    }

    public Instant emitidoEn() {
        return emitidoEn;
    }

    public UUID emitidoPor() {
        return emitidoPor;
    }

    public List<LineaDeVenta> lineas() {
        return lineas;
    }

    public List<Pago> pagos() {
        return pagos;
    }

    public String observaciones() {
        return observaciones;
    }

    public UUID documentoOrigenId() {
        return documentoOrigenId;
    }

    public EstadoDocumento estado() {
        return estado;
    }

    public BigDecimal totalGravado() {
        return totalGravado;
    }

    public BigDecimal totalExonerado() {
        return totalExonerado;
    }

    public BigDecimal totalInafecto() {
        return totalInafecto;
    }

    public BigDecimal totalDescuento() {
        return totalDescuento;
    }

    public BigDecimal totalIgv() {
        return totalIgv;
    }

    public BigDecimal total() {
        return total;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof DocumentoVenta documento && id.equals(documento.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
