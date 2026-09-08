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
 *
 * <p>Además de la venta directa hay dos formas de nacer, cada una con su
 * fábrica y sus reglas: el {@link #canjear canje} de una nota de venta y la
 * {@link #notaDeCredito nota de crédito} que corrige o anula un comprobante
 * (doc 13 §5).
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
    /** Solo en una nota de crédito: por qué se emite (catálogo 09). */
    private final TipoNotaCredito motivoNota;
    /** El documento al que este se refiere: el corregido, o la nota de venta canjeada. */
    private final ReferenciaDocumento origen;
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
            TipoNotaCredito motivoNota, ReferenciaDocumento origen, EstadoDocumento estado) {
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
        this.motivoNota = motivoNota;
        this.origen = origen;
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
                pagos == null ? List.of() : pagos, limpiar(observaciones), null, null, null,
                tipo.esFiscal() ? EstadoDocumento.PENDIENTE : EstadoDocumento.EMITIDO);
        documento.exigirAdquirente();
        documento.exigirPagosCuadrados();
        return documento;
    }

    /**
     * Convierte una nota de venta en boleta o factura (doc 13 §5.1).
     *
     * <p>Copia las líneas tal cual: mismos productos, mismas cantidades, mismos
     * precios. Volver a teclearlas sería invitar a que el comprobante y la nota
     * de venta digan cosas distintas de la misma operación.
     *
     * <h2>El comprobante del canje no lleva pagos, y es deliberado</h2>
     *
     * <p>El dinero entró una sola vez, con la nota de venta, en la sesión de
     * caja donde ocurrió. Copiar los pagos aquí los contaría dos veces en el
     * arqueo, y peor aún si el canje ocurre días después: cambiaría el
     * calculado de una sesión ya cerrada. La nota de venta conserva sus pagos y
     * pasa a {@code CANJEADO}; este documento hereda su forma de cobro para lo
     * que se imprime, no para lo que se cuenta.
     *
     * <p>Tampoco descarga existencias: ya salieron con la nota de venta.
     */
    public static DocumentoVenta canjear(UUID id, DocumentoVenta original, TipoDocumento tipo,
            String serie, long numero, Cliente cliente, LocalDate fechaEmision, Instant emitidoEn,
            UUID emitidoPor, String observaciones) {
        Objects.requireNonNull(original, "original");
        if (original.tipo() != TipoDocumento.NOTA_VENTA) {
            throw new ReglaDeNegocioViolada(
                    "canje_solo_de_nota_de_venta",
                    "Solo se canjea una nota de venta. " + original.numeroCompleto() + " es "
                            + original.tipo().nombre().toLowerCase() + ".");
        }
        if (original.estado() != EstadoDocumento.EMITIDO) {
            throw new ReglaDeNegocioViolada(
                    "nota_de_venta_no_canjeable",
                    "La nota de venta " + original.numeroCompleto() + " está "
                            + original.estado().name().toLowerCase() + " y no se puede canjear.");
        }
        if (tipo != TipoDocumento.BOLETA && tipo != TipoDocumento.FACTURA) {
            throw new ReglaDeNegocioViolada(
                    "tipo_de_canje_invalido",
                    "Una nota de venta se canjea por boleta o por factura.");
        }

        var documento = new DocumentoVenta(id, original.empresaId(), original.sucursalId(),
                original.sesionCajaId(), tipo, serie, numero, cliente, fechaEmision, emitidoEn,
                emitidoPor, original.lineas(), List.of(),
                limpiar(observaciones == null ? original.observaciones() : observaciones),
                original.id(), null, ReferenciaDocumento.de(original),
                EstadoDocumento.PENDIENTE);
        documento.exigirAdquirente();
        return documento;
    }

    /**
     * La nota de crédito que corrige o anula un comprobante ya aceptado
     * (doc 13 §5.2).
     *
     * <h2>Sobre un comprobante que SUNAT ya aceptó, y no antes</h2>
     *
     * <p>Una nota de crédito se refiere a un documento que existe para SUNAT.
     * Mientras la boleta está pendiente o rechazada no hay nada que corregir
     * allí: lo que hay que arreglar es su propio envío. Emitir la nota antes
     * produciría un rechazo por «documento que modifica no existe», con un
     * correlativo de nota ya gastado.
     *
     * @param lineas lo que se acredita. Con un motivo que anula tienen que ser
     *               todas las del original: media anulación no existe
     * @param pagos  cómo se devolvió el dinero. Vacío es válido —una corrección
     *               de descripción no mueve dinero, y un abono a cuenta tampoco—
     *               pero si hay pagos tienen que sumar el total de la nota
     */
    public static DocumentoVenta notaDeCredito(UUID id, DocumentoVenta original,
            TipoNotaCredito motivo, String serie, long numero, List<LineaDeVenta> lineas,
            List<Pago> pagos, LocalDate fechaEmision, Instant emitidoEn, UUID emitidoPor,
            UUID sesionCajaId, String observaciones) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(motivo, "motivo");
        if (!original.esFiscal()) {
            throw new ReglaDeNegocioViolada(
                    "nota_de_credito_sobre_no_fiscal",
                    "Una nota de venta no se anula con nota de crédito: no se declaró a SUNAT.");
        }
        if (original.estado() != EstadoDocumento.EMITIDO) {
            throw new ReglaDeNegocioViolada(
                    "documento_no_aceptado",
                    "El comprobante " + original.numeroCompleto() + " está "
                            + original.estado().name().toLowerCase() + ". La nota de crédito se "
                            + "emite sobre un comprobante que SUNAT ya aceptó.");
        }
        if (lineas == null || lineas.isEmpty()) {
            throw new ReglaDeNegocioViolada(
                    "sin_lineas", "Una nota de crédito necesita al menos una línea.", "lineas");
        }
        if (serie != null && !serie.isBlank() && serie.charAt(0) != original.serie().charAt(0)) {
            // La nota hereda la letra del documento que modifica: una nota con
            // serie F sobre una boleta apuntaria a un documento que no existe
            // en el libro de boletas.
            throw new ReglaDeNegocioViolada(
                    "serie_no_corresponde",
                    "La nota de crédito de " + original.tipo().nombre().toLowerCase()
                            + " usa una serie que empieza por " + original.serie().charAt(0) + ".",
                    "serieId");
        }

        var nota = new DocumentoVenta(id, original.empresaId(), original.sucursalId(), sesionCajaId,
                TipoDocumento.NOTA_CREDITO, serie, numero, original.cliente(), fechaEmision,
                emitidoEn, emitidoPor, lineas, pagos == null ? List.of() : pagos,
                limpiar(observaciones), original.id(), motivo, ReferenciaDocumento.de(original),
                EstadoDocumento.PENDIENTE);

        if (motivo.anulaElDocumento() && nota.total().compareTo(original.total()) != 0) {
            throw new ReglaDeNegocioViolada(
                    "anulacion_parcial",
                    "«" + motivo.nombre() + "» deja sin efecto el comprobante entero, así que la "
                            + "nota tiene que sumar S/ " + original.total().toPlainString()
                            + " y suma S/ " + nota.total().toPlainString()
                            + ". Para devolver una parte, usa «Devolución por ítem».", "lineas");
        }
        if (nota.total().compareTo(original.total()) > 0) {
            throw new ReglaDeNegocioViolada(
                    "nota_mayor_que_el_documento",
                    "La nota de crédito suma S/ " + nota.total().toPlainString()
                            + " y el comprobante S/ " + original.total().toPlainString()
                            + ". No se puede acreditar más de lo que se cobró.", "lineas");
        }
        nota.exigirPagosDeDevolucion();
        return nota;
    }

    /** Reconstrucción desde la persistencia: no valida. */
    public static DocumentoVenta reconstruir(UUID id, UUID empresaId, UUID sucursalId,
            UUID sesionCajaId, TipoDocumento tipo, String serie, long numero, Cliente cliente,
            LocalDate fechaEmision, Instant emitidoEn, UUID emitidoPor, List<LineaDeVenta> lineas,
            List<Pago> pagos, String observaciones, UUID documentoOrigenId,
            TipoNotaCredito motivoNota, ReferenciaDocumento origen, EstadoDocumento estado) {
        return new DocumentoVenta(id, empresaId, sucursalId, sesionCajaId, tipo, serie, numero,
                cliente, fechaEmision, emitidoEn, emitidoPor, lineas, pagos, observaciones,
                documentoOrigenId, motivoNota, origen, estado);
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

    /**
     * En una nota de crédito los pagos son la devolución, y son opcionales: una
     * corrección de descripción no mueve dinero y un abono a cuenta tampoco. Lo
     * que no se admite es una devolución a medias sin decirlo, así que si hay
     * pagos tienen que sumar el total de la nota.
     */
    private void exigirPagosDeDevolucion() {
        if (pagos.isEmpty()) {
            return;
        }
        BigDecimal devuelto = pagos.stream().map(Pago::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        if (devuelto.compareTo(total) != 0) {
            throw new ReglaDeNegocioViolada(
                    "devolucion_no_cuadra",
                    "La devolución suma S/ " + devuelto.toPlainString() + " y la nota S/ "
                            + total.toPlainString() + ". Tienen que coincidir, o no indiques "
                            + "ninguna forma de devolución.", "pagos");
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

    /**
     * SUNAT aceptó el comprobante: deja de estar pendiente. Solo desde
     * {@code PENDIENTE}; una nota de venta nunca pasa por aquí.
     */
    public void aceptarPorSunat() {
        if (estado != EstadoDocumento.PENDIENTE) {
            throw new ReglaDeNegocioViolada(
                    "documento_no_pendiente",
                    "El documento " + numeroCompleto() + " está " + estado.name().toLowerCase()
                            + " y no puede pasar a emitido.");
        }
        this.estado = EstadoDocumento.EMITIDO;
    }

    /**
     * Lo deja sin efecto. Solo desde {@code EMITIDO}: un comprobante que SUNAT
     * no aceptó no se anula, se corrige su envío.
     *
     * <p>Lo llama {@code EmisionElectronica} cuando SUNAT acepta una nota de
     * crédito con motivo de anulación, y no cuando la nota se registra: si SUNAT
     * la rechazara, la anulación no habría ocurrido y este documento seguiría
     * vigente.
     */
    public void anularPorNotaDeCredito() {
        if (estado != EstadoDocumento.EMITIDO) {
            throw new ReglaDeNegocioViolada(
                    "documento_no_anulable",
                    "El documento " + numeroCompleto() + " está " + estado.name().toLowerCase()
                            + " y no se puede anular.");
        }
        this.estado = EstadoDocumento.ANULADO;
    }

    /**
     * La nota de venta ya se convirtió en comprobante. Nunca se borra: sigue
     * nombrando la operación y conserva sus pagos, que son los que el arqueo de
     * su sesión contó.
     */
    public void marcarCanjeada() {
        if (tipo != TipoDocumento.NOTA_VENTA) {
            throw new ReglaDeNegocioViolada(
                    "canje_solo_de_nota_de_venta", "Solo una nota de venta se marca como canjeada.");
        }
        if (estado != EstadoDocumento.EMITIDO) {
            throw new ReglaDeNegocioViolada(
                    "nota_de_venta_no_canjeable",
                    "La nota de venta " + numeroCompleto() + " está " + estado.name().toLowerCase()
                            + " y no se puede canjear.");
        }
        this.estado = EstadoDocumento.CANJEADO;
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

    /** @return {@code null} salvo en una nota de crédito */
    public TipoNotaCredito motivoNota() {
        return motivoNota;
    }

    /** @return el documento al que este se refiere, o {@code null} */
    public ReferenciaDocumento origen() {
        return origen;
    }

    public boolean esNotaDeCredito() {
        return tipo == TipoDocumento.NOTA_CREDITO;
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
