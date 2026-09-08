package com.ondexia.infrastructure.entrada.web.ventas;

import com.ondexia.application.emision.EmisionElectronica;
import com.ondexia.application.ventas.DocumentosDeVenta;
import com.ondexia.domain.comprobante.EstadoSunat;
import com.ondexia.domain.comprobante.SerieCorrelativo;
import com.ondexia.domain.comprobante.TipoDocumento;
import com.ondexia.domain.ventas.DocumentoVenta;
import com.ondexia.domain.ventas.FormaDePago;
import com.ondexia.domain.ventas.LineaDeVenta;
import com.ondexia.domain.ventas.Pago;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * El punto de venta y lo que emite.
 *
 * <p>Dos puertas para el mismo caso de uso, porque los permisos son distintos:
 * la nota de venta es {@code ventas.nota_venta} y los comprobantes son
 * {@code ventas.comprobante}. Un cajero puede tener la primera sin la segunda.
 */
@RestController
@RequestMapping("/api/v1/ventas")
@Tag(name = "Ventas", description = "Punto de venta: notas de venta, boletas y facturas")
public class DocumentoVentaController {

    private final DocumentosDeVenta documentos;
    private final EmisionElectronica emision;

    public DocumentoVentaController(DocumentosDeVenta documentos, EmisionElectronica emision) {
        this.documentos = documentos;
        this.emision = emision;
    }

    // ── Notas de venta ──────────────────────────────────────────────────────

    @Operation(summary = "Emite una nota de venta", description = "Documento interno: no se declara a SUNAT.")
    @RequierePermiso(modulo = "ventas.nota_venta", accion = "registrar")
    @PostMapping("/notas-de-venta")
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaEmision emitirNotaDeVenta(@Valid @RequestBody PeticionVenta peticion) {
        return RespuestaEmision.desde(documentos.emitir(peticion.como(TipoDocumento.NOTA_VENTA)));
    }

    @Operation(summary = "Las últimas notas de venta")
    @RequierePermiso(modulo = "ventas.nota_venta", accion = "consultar")
    @GetMapping("/notas-de-venta")
    public List<RespuestaResumen> notasDeVenta() {
        return documentos.recientes(TipoDocumento.NOTA_VENTA).stream()
                .map(d -> RespuestaResumen.desde(d, null)).toList();
    }

    @Operation(summary = "Una nota de venta, con sus líneas y pagos")
    @RequierePermiso(modulo = "ventas.nota_venta", accion = "consultar")
    @GetMapping("/notas-de-venta/{id}")
    public RespuestaDocumento notaDeVenta(@PathVariable UUID id) {
        return RespuestaDocumento.desde(exigirTipo(documentos.obtener(id), TipoDocumento.NOTA_VENTA));
    }

    // ── Boletas y facturas ──────────────────────────────────────────────────

    @Operation(
            summary = "Registra una boleta o una factura",
            description = """
                    Queda PENDIENTE y sale hacia SUNAT al confirmar. `tipo`: BOLETA o FACTURA. \
                    Responde 400 con `emision_no_configurada` si la empresa no cargó certificado \
                    y clave SOL. El estado ante SUNAT se consulta en /comprobantes/{id}/sunat.""")
    @RequierePermiso(modulo = "ventas.comprobante", accion = "emitir")
    @PostMapping("/comprobantes")
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaEmision emitirComprobante(@Valid @RequestBody PeticionComprobante peticion) {
        return RespuestaEmision.desde(documentos.emitir(peticion.venta().como(peticion.tipo())));
    }

    @Operation(summary = "Los últimos comprobantes; con `tipo`, solo de ese tipo")
    @RequierePermiso(modulo = "ventas.comprobante", accion = "consultar")
    @GetMapping("/comprobantes")
    public List<RespuestaResumen> comprobantes(@RequestParam(required = false) TipoDocumento tipo) {
        var lista = tipo == null
                ? documentos.recientes(null).stream().filter(DocumentoVenta::esFiscal).toList()
                : documentos.recientes(tipo);
        var estados = emision.estadosDe(lista.stream().map(DocumentoVenta::id).toList());
        return lista.stream().map(d -> RespuestaResumen.desde(d, estados.get(d.id()))).toList();
    }

    @Operation(summary = "Un comprobante, con sus líneas y pagos")
    @RequierePermiso(modulo = "ventas.comprobante", accion = "consultar")
    @GetMapping("/comprobantes/{id}")
    public RespuestaDocumento comprobante(@PathVariable UUID id) {
        var documento = documentos.obtener(id);
        if (!documento.esFiscal()) {
            throw com.ondexia.domain.comun.error.RecursoNoEncontrado.con(
                    "documento_no_encontrado", "El documento no existe.");
        }
        return RespuestaDocumento.desde(documento);
    }

    @Operation(summary = "Las series activas de un tipo en un establecimiento, para elegir al emitir")
    @RequierePermiso(modulo = "ventas.nota_venta", accion = "consultar")
    @GetMapping("/series")
    public List<RespuestaSerie> series(@RequestParam TipoDocumento tipo, @RequestParam UUID sucursalId) {
        return documentos.seriesDisponibles(tipo, sucursalId).stream().map(RespuestaSerie::desde).toList();
    }

    private static DocumentoVenta exigirTipo(DocumentoVenta documento, TipoDocumento tipo) {
        if (documento.tipo() != tipo) {
            throw com.ondexia.domain.comun.error.RecursoNoEncontrado.con(
                    "documento_no_encontrado", "El documento no existe.");
        }
        return documento;
    }

    // ── Cuerpos ─────────────────────────────────────────────────────────────

    public record LineaPedida(
            @NotNull(message = "Indica el producto.") UUID productoId,
            @NotNull(message = "Indica la cantidad.")
            @DecimalMin(value = "0", inclusive = false, message = "La cantidad tiene que ser mayor que cero.")
            @Digits(integer = 12, fraction = 6, message = "Hasta seis decimales.")
            BigDecimal cantidad,
            @DecimalMin(value = "0", message = "El descuento no puede ser negativo.")
            @Digits(integer = 12, fraction = 2, message = "El descuento va en céntimos.")
            BigDecimal descuento) {
    }

    public record PagoPedido(
            @NotNull(message = "Indica la forma de pago.") FormaDePago forma,
            @NotNull(message = "Indica el monto.")
            @DecimalMin(value = "0", inclusive = false, message = "El monto tiene que ser mayor que cero.")
            @Digits(integer = 12, fraction = 2, message = "El monto va en céntimos.")
            BigDecimal monto,
            @Size(max = 100) String referencia,
            /*
             * Lo que el cliente entregó, cuando fue más que el monto. Opcional:
             * sin él se entiende que pagó justo. El vuelto no se pide ni se
             * devuelve porque es una resta, y un dato derivado que viaja es un
             * dato que algún día no coincide.
             */
            @DecimalMin(value = "0", inclusive = false,
                    message = "Lo entregado tiene que ser mayor que cero.")
            @Digits(integer = 12, fraction = 2, message = "Lo entregado va en céntimos.")
            BigDecimal entregado) {
    }

    public record PeticionVenta(
            @NotNull(message = "Indica la caja.") UUID cajaId,
            UUID serieId,
            UUID clienteId,
            @NotEmpty(message = "Agrega al menos una línea.") @Valid List<LineaPedida> lineas,
            @NotEmpty(message = "Indica cómo se cobró.") @Valid List<PagoPedido> pagos,
            @Size(max = 500) String observaciones) {

        DocumentosDeVenta.Peticion como(TipoDocumento tipo) {
            return new DocumentosDeVenta.Peticion(tipo, cajaId, serieId, clienteId,
                    lineas.stream().map(l -> new DocumentosDeVenta.LineaPedida(
                            l.productoId(), l.cantidad(), l.descuento())).toList(),
                    pagos.stream().map(p -> new DocumentosDeVenta.PagoPedido(
                            p.forma(), p.monto(), p.referencia(), p.entregado())).toList(),
                    observaciones);
        }
    }

    public record PeticionComprobante(
            @NotNull(message = "Indica si es boleta o factura.") TipoDocumento tipo,
            @NotNull @Valid PeticionVenta venta) {
    }

    public record RespuestaLinea(int orden, UUID productoId, String codigo, String descripcion,
            String unidad, BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal valorUnitario,
            BigDecimal descuento, String afectacion, BigDecimal valorVenta, BigDecimal igv,
            BigDecimal total) {

        static RespuestaLinea desde(LineaDeVenta l) {
            return new RespuestaLinea(l.orden(), l.productoId(), l.codigo(), l.descripcion(),
                    l.unidad().codigo(), l.cantidad(), l.precioUnitario(), l.valorUnitario(),
                    l.descuento(), l.afectacion().name(), l.valorVenta(), l.igv(), l.total());
        }
    }

    /**
     * @param entregado lo que el cliente puso sobre el mostrador; {@code null}
     *                  si pagó justo
     * @param vuelto    entregado menos monto. Va calculado y no guardado: se
     *                  manda porque el comprobante impreso lo enseña, y así el
     *                  cliente que lo pinte no repite la resta
     */
    public record RespuestaPago(String forma, BigDecimal monto, String referencia,
            BigDecimal entregado, BigDecimal vuelto) {

        static RespuestaPago desde(Pago p) {
            return new RespuestaPago(p.forma().name(), p.monto(), p.referencia(),
                    p.entregado(), p.vuelto());
        }
    }

    public record RespuestaCliente(UUID id, String tipoDocumento, String numeroDocumento,
            String nombre, String direccion) {
    }

    /**
     * El documento al que este se refiere: el que una nota de crédito modifica,
     * o la nota de venta de la que salió por canje.
     */
    public record RespuestaOrigen(UUID id, String tipo, String numeroCompleto) {
    }

    /**
     * @param motivo el del catálogo 09, solo en una nota de crédito
     * @param origen el documento al que este se refiere, o nulo
     */
    public record RespuestaDocumento(UUID id, String tipo, String tipoNombre, boolean fiscal,
            String serie, long numero, String numeroCompleto, String estado, UUID sucursalId,
            UUID sesionCajaId, RespuestaCliente cliente, LocalDate fechaEmision, Instant emitidoEn,
            UUID emitidoPor, String moneda, BigDecimal totalGravado, BigDecimal totalExonerado,
            BigDecimal totalInafecto, BigDecimal totalDescuento, BigDecimal totalIgv,
            BigDecimal total, String observaciones, UUID documentoOrigenId, String motivo,
            String motivoNombre, RespuestaOrigen origen, List<RespuestaLinea> lineas,
            List<RespuestaPago> pagos) {

        static RespuestaDocumento desde(DocumentoVenta d) {
            var c = d.cliente();
            var motivo = d.motivoNota();
            var origen = d.origen();
            return new RespuestaDocumento(d.id(), d.tipo().codigo(), d.tipo().nombre(), d.esFiscal(),
                    d.serie(), d.numero(), d.numeroCompleto(), d.estado().name(), d.sucursalId(),
                    d.sesionCajaId(),
                    c == null ? null : new RespuestaCliente(c.id(), c.tipoDocumento().name(),
                            c.numeroDocumento(), c.nombre(), c.direccion()),
                    d.fechaEmision(), d.emitidoEn(), d.emitidoPor(), "PEN", d.totalGravado(),
                    d.totalExonerado(), d.totalInafecto(), d.totalDescuento(), d.totalIgv(),
                    d.total(), d.observaciones(), d.documentoOrigenId(),
                    motivo == null ? null : motivo.name(),
                    motivo == null ? null : motivo.nombre(),
                    origen == null ? null : new RespuestaOrigen(d.documentoOrigenId(),
                            origen.tipo().codigo(), origen.numeroCompleto()),
                    d.lineas().stream().map(RespuestaLinea::desde).toList(),
                    d.pagos().stream().map(RespuestaPago::desde).toList());
        }
    }

    /** @param estadoSunat nulo en una nota de venta: no se declara */
    public record RespuestaResumen(UUID id, String tipo, String tipoNombre, String numeroCompleto,
            String estado, String estadoSunat, LocalDate fechaEmision, Instant emitidoEn,
            String cliente, String clienteDocumento, BigDecimal total) {

        static RespuestaResumen desde(DocumentoVenta d, EstadoSunat sunat) {
            var c = d.cliente();
            return new RespuestaResumen(d.id(), d.tipo().codigo(), d.tipo().nombre(),
                    d.numeroCompleto(), d.estado().name(), sunat == null ? null : sunat.name(),
                    d.fechaEmision(), d.emitidoEn(),
                    c == null ? null : c.nombre(), c == null ? null : c.numeroDocumento(), d.total());
        }
    }

    public record RespuestaEmision(RespuestaDocumento documento, List<String> avisos) {

        static RespuestaEmision desde(DocumentosDeVenta.Emision emision) {
            return new RespuestaEmision(RespuestaDocumento.desde(emision.documento()), emision.avisos());
        }
    }

    public record RespuestaSerie(UUID id, String serie, String siguienteNumero) {

        static RespuestaSerie desde(SerieCorrelativo s) {
            return new RespuestaSerie(s.id(), s.serie(), s.numeroCompleto(s.ultimoNumero() + 1));
        }
    }
}
