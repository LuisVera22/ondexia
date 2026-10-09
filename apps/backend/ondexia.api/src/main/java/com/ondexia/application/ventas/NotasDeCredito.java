package com.ondexia.application.ventas;

import com.ondexia.application.comprobante.AsignadorDeCorrelativo;
import com.ondexia.application.configuracion.ConsultarEmpresa;
import com.ondexia.application.emision.EmisionElectronica;
import com.ondexia.domain.almacen.Almacen;
import com.ondexia.domain.almacen.AlmacenRepositorio;
import com.ondexia.domain.almacen.ExistenciasRepositorio;
import com.ondexia.domain.almacen.MovimientoStock;
import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comprobante.SerieCorrelativo;
import com.ondexia.domain.comprobante.SerieCorrelativoRepositorio;
import com.ondexia.domain.comprobante.TipoDocumento;
import com.ondexia.domain.comun.ContextoOperacion;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.ventas.DocumentoVenta;
import com.ondexia.domain.ventas.DocumentoVentaRepositorio;
import com.ondexia.domain.ventas.EstadoDocumento;
import com.ondexia.domain.ventas.LineaDeVenta;
import com.ondexia.domain.ventas.Pago;
import com.ondexia.domain.ventas.TipoNotaCredito;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anular o corregir un comprobante con una nota de crédito (doc 13 §5.2).
 *
 * <p>Aparte de {@link DocumentosDeVenta} porque no es una venta: no cobra, no
 * elige productos y no descarga existencias — las repone. Lo único que comparte
 * es el correlativo y el camino hacia SUNAT, y los dos son colaboradores.
 *
 * <h2>Las cantidades se toman del comprobante, nunca de la petición</h2>
 *
 * <p>La pantalla manda qué líneas del original se acreditan y cuántas unidades
 * de cada una. La descripción, el precio y la afectación salen del documento
 * guardado. Un precio distinto en la nota y en el comprobante es una diferencia
 * que SUNAT ve, y dejar que el navegador lo proponga sería ponerla al alcance de
 * cualquiera.
 */
@Service
public class NotasDeCredito {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final DocumentoVentaRepositorio documentos;
    private final SesionesDeCaja sesiones;
    private final SerieCorrelativoRepositorio series;
    private final AsignadorDeCorrelativo correlativos;
    private final ExistenciasRepositorio existencias;
    private final AlmacenRepositorio almacenes;
    private final ConsultarEmpresa empresa;
    private final EmisionElectronica emision;
    private final RegistroDeAuditoria auditoria;
    private final ProveedorDeContexto contexto;
    private final Clock reloj;

    public NotasDeCredito(DocumentoVentaRepositorio documentos, SesionesDeCaja sesiones,
            SerieCorrelativoRepositorio series, AsignadorDeCorrelativo correlativos,
            ExistenciasRepositorio existencias, AlmacenRepositorio almacenes,
            ConsultarEmpresa empresa, EmisionElectronica emision, RegistroDeAuditoria auditoria,
            ProveedorDeContexto contexto, Clock reloj) {
        this.documentos = documentos;
        this.sesiones = sesiones;
        this.series = series;
        this.correlativos = correlativos;
        this.existencias = existencias;
        this.almacenes = almacenes;
        this.empresa = empresa;
        this.emision = emision;
        this.auditoria = auditoria;
        this.contexto = contexto;
        this.reloj = reloj;
    }

    /**
     * @param orden    el de la línea <strong>del comprobante original</strong>
     * @param cantidad cuántas unidades se acreditan; nula, todas las de esa línea
     */
    public record LineaAcreditada(int orden, BigDecimal cantidad) {
    }

    /**
     * @param lineas vacío significa «todas, enteras». Es lo normal al anular
     * @param pagos  cómo se devolvió el dinero. Vacío: no se devolvió nada ahora
     */
    public record Peticion(UUID documentoId, TipoNotaCredito motivo, UUID cajaId, UUID serieId,
            List<LineaAcreditada> lineas, List<DocumentosDeVenta.PagoPedido> pagos,
            String observaciones) {
    }

    @Transactional
    public DocumentoVenta emitir(Peticion peticion) {
        Objects.requireNonNull(peticion.motivo(), "motivo");
        ContextoOperacion actual = contexto.obligatorio();
        Empresa laEmpresa = empresa.ejecutar();
        emision.exigirConfigurada(laEmpresa);

        DocumentoVenta original = documentos.buscarPorId(peticion.documentoId())
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "documento_no_encontrado", "El comprobante no existe."));
        exigirSinNotaEnCurso(original);

        var sesion = sesiones.abiertaDe(peticion.cajaId())
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "caja_cerrada",
                        "La caja está cerrada. Ábrela antes de emitir la nota de crédito.",
                        "cajaId"));
        // El establecimiento es el del comprobante original y no el de la caja:
        // la nota pertenece al mismo local que emitió lo que corrige, aunque
        // hoy se atienda desde otra caja.
        SerieCorrelativo serie = elegirSerie(original, peticion.serieId());

        var lineas = construirLineas(original, peticion.lineas());
        var pagos = (peticion.pagos() == null ? List.<DocumentosDeVenta.PagoPedido>of() : peticion.pagos())
                .stream().map(p -> new Pago(p.forma(), p.monto(), p.referencia())).toList();

        long numero = correlativos.siguienteNumero(serie.id());
        Instant ahora = reloj.instant();
        var nota = DocumentoVenta.notaDeCredito(UUID.randomUUID(), original, peticion.motivo(),
                serie.serie(), numero, lineas, pagos, LocalDate.ofInstant(ahora, LIMA), ahora,
                actual.usuarioId(), sesion.id(), peticion.observaciones());

        reponerExistencias(nota, original);
        var guardada = documentos.guardar(nota);
        emision.encolar(guardada, laEmpresa);
        auditoria.registrarCreacion("documento_venta", guardada.id(), Instantanea.de(guardada));
        return guardada;
    }

    /** Las notas de crédito de un comprobante, para mostrarlas en su ficha. */
    public List<DocumentoVenta> relacionadasCon(UUID documentoId) {
        return documentos.listarPorOrigen(documentoId);
    }

    public List<DocumentoVenta> recientes() {
        return documentos.listarRecientes(TipoDocumento.NOTA_CREDITO, 200);
    }

    /**
     * Ni dos anulaciones del mismo comprobante, ni una segunda nota mientras la
     * primera está en camino.
     *
     * <p>Lo primero es evidente. Lo segundo lo es menos y también hace falta:
     * mientras la nota de anulación está pendiente de SUNAT, el comprobante
     * sigue {@code EMITIDO}, así que sin esta comprobación nada impediría
     * emitir otra —y acabar con dos notas por el mismo importe si las dos se
     * aceptan—.
     */
    private void exigirSinNotaEnCurso(DocumentoVenta original) {
        if (original.estado() == EstadoDocumento.ANULADO) {
            throw new ReglaDeNegocioViolada(
                    "documento_ya_anulado",
                    "El comprobante " + original.numeroCompleto() + " ya está anulado.");
        }
        boolean hayAnulacionEnCurso = documentos.listarPorOrigen(original.id()).stream()
                .filter(DocumentoVenta::esNotaDeCredito)
                .filter(nota -> nota.estado() == EstadoDocumento.PENDIENTE)
                .anyMatch(nota -> nota.motivoNota().anulaElDocumento());
        if (hayAnulacionEnCurso) {
            throw new ReglaDeNegocioViolada(
                    "anulacion_en_curso",
                    "Ya hay una nota de crédito de anulación de " + original.numeroCompleto()
                            + " esperando respuesta de SUNAT. Espera a que se resuelva.");
        }
    }

    /** Series de nota de crédito del establecimiento del original, con su misma letra. */
    private SerieCorrelativo elegirSerie(DocumentoVenta original, UUID serieId) {
        var candidatas = seriesPara(original);
        if (serieId != null) {
            return candidatas.stream().filter(s -> s.id().equals(serieId)).findFirst()
                    .orElseThrow(() -> new ReglaDeNegocioViolada(
                            "serie_invalida",
                            "La serie indicada no sirve para una nota de crédito de "
                                    + original.tipo().nombre().toLowerCase()
                                    + " en este establecimiento.", "serieId"));
        }
        if (candidatas.isEmpty()) {
            throw new ReglaDeNegocioViolada(
                    "sin_serie",
                    "No hay una serie activa de nota de crédito que empiece por "
                            + original.serie().charAt(0) + " en este establecimiento. Créala en "
                            + "Configuración › Series y correlativos.", "serieId");
        }
        if (candidatas.size() > 1) {
            throw new ReglaDeNegocioViolada(
                    "varias_series", "Hay varias series de nota de crédito: elige una.", "serieId");
        }
        return candidatas.get(0);
    }

    /**
     * La nota hereda la letra del documento que modifica: F para una factura,
     * B para una boleta. Es lo que hace que SUNAT encuentre el original.
     */
    public List<SerieCorrelativo> seriesPara(DocumentoVenta original) {
        return series.listar().stream()
                .filter(s -> s.estaActiva() && s.tipoDocumento() == TipoDocumento.NOTA_CREDITO)
                .filter(s -> original.sucursalId().equals(s.sucursalId()))
                .filter(s -> s.serie().charAt(0) == original.serie().charAt(0))
                .toList();
    }

    /**
     * Copia las líneas del original, con la cantidad que se acredita de cada
     * una. Sin líneas indicadas, todas enteras: es el caso de la anulación, y
     * pedir que la pantalla las envíe una por una solo daría ocasión de que
     * faltara alguna.
     */
    private List<LineaDeVenta> construirLineas(DocumentoVenta original,
            List<LineaAcreditada> pedidas) {
        Map<Integer, LineaDeVenta> porOrden = original.lineas().stream()
                .collect(Collectors.toMap(LineaDeVenta::orden, Function.identity()));

        List<LineaAcreditada> aAcreditar = pedidas == null || pedidas.isEmpty()
                ? original.lineas().stream()
                        .map(l -> new LineaAcreditada(l.orden(), l.cantidad())).toList()
                : pedidas;

        var lineas = new ArrayList<LineaDeVenta>();
        int orden = 1;
        for (LineaAcreditada pedida : aAcreditar) {
            LineaDeVenta linea = porOrden.get(pedida.orden());
            if (linea == null) {
                throw new ReglaDeNegocioViolada(
                        "linea_inexistente",
                        "El comprobante " + original.numeroCompleto() + " no tiene una línea "
                                + pedida.orden() + ".", "lineas");
            }
            BigDecimal cantidad = pedida.cantidad() == null ? linea.cantidad() : pedida.cantidad();
            if (cantidad.compareTo(linea.cantidad()) > 0) {
                throw new ReglaDeNegocioViolada(
                        "cantidad_mayor_que_la_vendida",
                        "De «" + linea.descripcion() + "» se vendieron "
                                + linea.cantidad().stripTrailingZeros().toPlainString()
                                + " y se quieren acreditar "
                                + cantidad.stripTrailingZeros().toPlainString() + ".", "lineas");
            }
            // El descuento se acredita en la misma proporción que la cantidad:
            // devolver la mitad de lo vendido devuelve la mitad de lo pagado,
            // y el descuento era parte de ese precio.
            BigDecimal descuento = linea.descuento().signum() == 0 ? BigDecimal.ZERO
                    : linea.descuento().multiply(cantidad)
                            .divide(linea.cantidad(), 2, java.math.RoundingMode.HALF_UP);
            lineas.add(LineaDeVenta.calcular(orden++, linea.productoId(), linea.codigo(),
                    linea.descripcion(), linea.unidad(), linea.afectacion(), cantidad,
                    linea.precioUnitario(), descuento, linea.descargaExistencias()));
        }
        return lineas;
    }

    /**
     * La mercadería vuelve al almacén del local, con el mismo criterio que la
     * venta la sacó. Solo si el motivo lo dice: un descuento posterior no
     * devuelve nada al estante, y sumarlo sería inventar existencias.
     */
    private void reponerExistencias(DocumentoVenta nota, DocumentoVenta original) {
        if (!nota.motivoNota().reponeExistencias()) {
            return;
        }
        var conStock = nota.lineas().stream().filter(LineaDeVenta::descargaExistencias).toList();
        if (conStock.isEmpty()) {
            return;
        }
        Almacen almacen = almacenes.principalDe(original.sucursalId())
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "sin_almacen_en_el_local",
                        "El establecimiento del comprobante no tiene un almacén activo al que "
                                + "devolver la mercadería."));
        for (LineaDeVenta linea : conStock) {
            existencias.mover(new MovimientoStock(UUID.randomUUID(), almacen.id(),
                    linea.productoId(), linea.cantidad(), MovimientoStock.Tipo.DEVOLUCION,
                    nota.tipo().codigo(), nota.id(), nota.numeroCompleto(), nota.emitidoPor(),
                    nota.emitidoEn()));
        }
    }

    private record Instantanea(String tipo, String numero, String motivo, String origen,
            BigDecimal total, String estado) {

        static Instantanea de(DocumentoVenta d) {
            return new Instantanea(d.tipo().codigo(), d.numeroCompleto(),
                    d.motivoNota() == null ? null : d.motivoNota().codigo(),
                    d.origen() == null ? null : d.origen().numeroCompleto(), d.total(),
                    d.estado().name());
        }
    }
}
