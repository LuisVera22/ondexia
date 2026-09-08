package com.ondexia.infrastructure.entrada.web.ventas;

import com.ondexia.application.ventas.CanjeDeNotaDeVenta;
import com.ondexia.application.ventas.DocumentosDeVenta;
import com.ondexia.application.ventas.NotasDeCredito;
import com.ondexia.domain.comprobante.SerieCorrelativo;
import com.ondexia.domain.comprobante.TipoDocumento;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.ventas.DocumentoVenta;
import com.ondexia.domain.ventas.FormaDePago;
import com.ondexia.domain.ventas.TipoNotaCredito;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anular y corregir: la nota de crédito, y el canje de una nota de venta
 * (doc 13 §5).
 *
 * <h2>Anular y emitir una nota de crédito son dos puertas, y es a propósito</h2>
 *
 * <p>El catálogo de permisos separa {@code emitir} de {@code anular} desde la
 * V2, y dice por qué: anular un comprobante ya emitido tiene efecto tributario
 * y quien atiende el mostrador casi nunca debe poder hacerlo. Una sola ruta con
 * el motivo dentro del cuerpo obligaría a comprobar el permiso a mano según ese
 * motivo — con lo fácil que es olvidarlo al añadir el siguiente caso—. Con dos
 * rutas, la anotación lo dice y el filtro de seguridad lo aplica sin que nadie
 * tenga que acordarse:
 *
 * <ul>
 *   <li>{@code POST /comprobantes/{id}/anulacion} — deja sin efecto el
 *       comprobante entero. Exige {@code ventas.nota_credito:anular}.</li>
 *   <li>{@code POST /notas-de-credito} — devoluciones parciales, descuentos y
 *       correcciones. Exige {@code ventas.nota_credito:emitir}, y rechaza los
 *       motivos que anulan.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ventas")
@Tag(name = "Notas de crédito", description = "Anular o corregir un comprobante, y canjear una nota de venta")
public class NotaDeCreditoController {

    private final NotasDeCredito notas;
    private final CanjeDeNotaDeVenta canje;
    private final DocumentosDeVenta documentos;

    public NotaDeCreditoController(NotasDeCredito notas, CanjeDeNotaDeVenta canje,
            DocumentosDeVenta documentos) {
        this.notas = notas;
        this.canje = canje;
        this.documentos = documentos;
    }

    @Operation(
            summary = "Anula un comprobante con una nota de crédito",
            description = """
                    Copia todas las líneas del comprobante y emite la nota que lo deja sin \
                    efecto. El comprobante pasa a ANULADO cuando SUNAT acepta la nota, no antes. \
                    Repone las existencias que la venta descargó.""")
    @RequierePermiso(modulo = "ventas.nota_credito", accion = "anular")
    @PostMapping("/comprobantes/{documentoId}/anulacion")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoVentaController.RespuestaDocumento anular(@PathVariable UUID documentoId,
            @Valid @RequestBody PeticionAnulacion peticion) {
        var motivo = peticion.motivo() == null ? TipoNotaCredito.ANULACION_DE_LA_OPERACION
                : peticion.motivo();
        if (!motivo.anulaElDocumento()) {
            throw new ReglaDeNegocioViolada(
                    "motivo_no_anula",
                    "«" + motivo.nombre() + "» no deja sin efecto el comprobante. Para eso está "
                            + "la nota de crédito por devolución parcial o descuento.", "motivo");
        }
        return DocumentoVentaController.RespuestaDocumento.desde(notas.emitir(
                new NotasDeCredito.Peticion(documentoId, motivo, peticion.cajaId(),
                        peticion.serieId(), List.of(), pagos(peticion.pagos()),
                        peticion.observaciones())));
    }

    @Operation(
            summary = "Emite una nota de crédito que no anula el comprobante",
            description = """
                    Devolución por ítem, descuentos y correcciones. Las líneas indican qué \
                    posiciones del comprobante se acreditan y cuántas unidades de cada una; sin \
                    líneas se acreditan todas, que solo tiene sentido con un motivo de anulación \
                    y por eso aquí se rechaza.""")
    @RequierePermiso(modulo = "ventas.nota_credito", accion = "emitir")
    @PostMapping("/notas-de-credito")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoVentaController.RespuestaDocumento emitir(
            @Valid @RequestBody PeticionNotaDeCredito peticion) {
        if (peticion.motivo().anulaElDocumento()) {
            throw new ReglaDeNegocioViolada(
                    "motivo_anula",
                    "«" + peticion.motivo().nombre() + "» deja sin efecto el comprobante entero. "
                            + "Eso se hace desde «Anular», que exige otro permiso.", "motivo");
        }
        return DocumentoVentaController.RespuestaDocumento.desde(notas.emitir(
                new NotasDeCredito.Peticion(peticion.documentoId(), peticion.motivo(),
                        peticion.cajaId(), peticion.serieId(),
                        peticion.lineas() == null ? List.of() : peticion.lineas().stream()
                                .map(l -> new NotasDeCredito.LineaAcreditada(l.orden(), l.cantidad()))
                                .toList(),
                        pagos(peticion.pagos()), peticion.observaciones())));
    }

    @Operation(summary = "Las últimas notas de crédito")
    @RequierePermiso(modulo = "ventas.nota_credito", accion = "consultar")
    @GetMapping("/notas-de-credito")
    public List<DocumentoVentaController.RespuestaResumen> recientes() {
        return notas.recientes().stream()
                .map(d -> DocumentoVentaController.RespuestaResumen.desde(d, null)).toList();
    }

    @Operation(summary = "Una nota de crédito, con sus líneas")
    @RequierePermiso(modulo = "ventas.nota_credito", accion = "consultar")
    @GetMapping("/notas-de-credito/{id}")
    public DocumentoVentaController.RespuestaDocumento nota(@PathVariable UUID id) {
        var documento = documentos.obtener(id);
        if (!documento.esNotaDeCredito()) {
            throw RecursoNoEncontrado.con("documento_no_encontrado", "La nota de crédito no existe.");
        }
        return DocumentoVentaController.RespuestaDocumento.desde(documento);
    }

    @Operation(summary = "Los motivos del catálogo 09 que la pantalla puede ofrecer")
    @RequierePermiso(modulo = "ventas.nota_credito", accion = "consultar")
    @GetMapping("/notas-de-credito/motivos")
    public List<RespuestaMotivo> motivos() {
        return Arrays.stream(TipoNotaCredito.values())
                .map(m -> new RespuestaMotivo(m.name(), m.codigo(), m.nombre(),
                        m.anulaElDocumento(), m.reponeExistencias()))
                .toList();
    }

    @Operation(
            summary = "Lo que salió de este documento",
            description = "Sus notas de crédito, o el comprobante que lo canjeó.")
    @RequierePermiso(modulo = "ventas.comprobante", accion = "consultar")
    @GetMapping("/documentos/{documentoId}/relacionados")
    public List<DocumentoVentaController.RespuestaResumen> relacionados(@PathVariable UUID documentoId) {
        return notas.relacionadasCon(documentoId).stream()
                .map(d -> DocumentoVentaController.RespuestaResumen.desde(d, null)).toList();
    }

    @Operation(
            summary = "Las series de nota de crédito que sirven para este comprobante",
            description = "Las del mismo establecimiento y con la misma letra que su serie.")
    @RequierePermiso(modulo = "ventas.nota_credito", accion = "consultar")
    @GetMapping("/comprobantes/{documentoId}/series-nota-credito")
    public List<DocumentoVentaController.RespuestaSerie> seriesDeNota(@PathVariable UUID documentoId) {
        return notas.seriesPara(documentos.obtener(documentoId)).stream()
                .map(DocumentoVentaController.RespuestaSerie::desde).toList();
    }

    // ── Canje ───────────────────────────────────────────────────────────────

    @Operation(
            summary = "Canjea una nota de venta por boleta o factura",
            description = """
                    Copia las líneas y deja la referencia en los dos sentidos. La nota de venta \
                    pasa a CANJEADO y conserva sus pagos: el dinero entró con ella y el arqueo \
                    de su sesión ya lo contó, así que el comprobante no lleva pagos propios ni \
                    vuelve a descargar existencias.""")
    @RequierePermiso(modulo = "ventas.nota_venta", accion = "canjear")
    @PostMapping("/notas-de-venta/{id}/canje")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoVentaController.RespuestaDocumento canjear(@PathVariable UUID id,
            @Valid @RequestBody PeticionCanje peticion) {
        return DocumentoVentaController.RespuestaDocumento.desde(canje.canjear(
                new CanjeDeNotaDeVenta.Peticion(id, peticion.tipo(), peticion.serieId(),
                        peticion.clienteId(), peticion.observaciones())));
    }

    @Operation(summary = "Las series disponibles para canjear esta nota de venta")
    @RequierePermiso(modulo = "ventas.nota_venta", accion = "canjear")
    @GetMapping("/notas-de-venta/{id}/series-canje")
    public List<DocumentoVentaController.RespuestaSerie> seriesDeCanje(@PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestParam TipoDocumento tipo) {
        DocumentoVenta nota = documentos.obtener(id);
        return canje.seriesDisponibles(tipo, nota.sucursalId()).stream()
                .map(DocumentoVentaController.RespuestaSerie::desde).toList();
    }

    private static List<DocumentosDeVenta.PagoPedido> pagos(List<PagoDevuelto> pedidos) {
        return pedidos == null ? List.of() : pedidos.stream()
                .map(p -> new DocumentosDeVenta.PagoPedido(p.forma(), p.monto(), p.referencia(), null))
                .toList();
    }

    // ── Cuerpos ─────────────────────────────────────────────────────────────

    /** Cómo se devolvió el dinero. La lista vacía significa que no se devolvió nada ahora. */
    public record PagoDevuelto(
            @NotNull(message = "Indica la forma de devolución.") FormaDePago forma,
            @NotNull(message = "Indica el monto.")
            @DecimalMin(value = "0", inclusive = false, message = "El monto tiene que ser mayor que cero.")
            @Digits(integer = 12, fraction = 2, message = "El monto va en céntimos.")
            BigDecimal monto,
            @Size(max = 100) String referencia) {
    }

    public record LineaAcreditada(
            int orden,
            @DecimalMin(value = "0", inclusive = false, message = "La cantidad tiene que ser mayor que cero.")
            @Digits(integer = 12, fraction = 6, message = "Hasta seis decimales.")
            BigDecimal cantidad) {
    }

    /** @param motivo nulo se toma como anulación de la operación, que es el caso normal */
    public record PeticionAnulacion(
            TipoNotaCredito motivo,
            @NotNull(message = "Indica la caja.") UUID cajaId,
            UUID serieId,
            @Valid List<PagoDevuelto> pagos,
            @Size(max = 500) String observaciones) {
    }

    public record PeticionNotaDeCredito(
            @NotNull(message = "Indica el comprobante.") UUID documentoId,
            @NotNull(message = "Indica el motivo.") TipoNotaCredito motivo,
            @NotNull(message = "Indica la caja.") UUID cajaId,
            UUID serieId,
            @Valid List<LineaAcreditada> lineas,
            @Valid List<PagoDevuelto> pagos,
            @Size(max = 500) String observaciones) {
    }

    public record PeticionCanje(
            @NotNull(message = "Indica si es boleta o factura.") TipoDocumento tipo,
            UUID serieId,
            UUID clienteId,
            @Size(max = 500) String observaciones) {
    }

    public record RespuestaMotivo(String codigo, String codigoSunat, String nombre,
            boolean anula, boolean repone) {
    }
}
