package com.ondexia.application.ventas;

import com.ondexia.application.almacen.Existencias;
import com.ondexia.application.almacen.Productos;
import com.ondexia.application.comprobante.AsignadorDeCorrelativo;
import com.ondexia.application.comprobante.TiposDeComprobante;
import com.ondexia.application.configuracion.ConsultarEmpresa;
import com.ondexia.application.emision.EmisionElectronica;
import com.ondexia.domain.almacen.Almacen;
import com.ondexia.domain.almacen.AlmacenRepositorio;
import com.ondexia.domain.almacen.Existencia;
import com.ondexia.domain.almacen.ExistenciasRepositorio;
import com.ondexia.domain.almacen.MovimientoStock;
import com.ondexia.domain.almacen.Producto;
import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comprobante.SerieCorrelativo;
import com.ondexia.domain.comprobante.SerieCorrelativoRepositorio;
import com.ondexia.domain.comprobante.TipoDocumento;
import com.ondexia.domain.comun.ContextoOperacion;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.ventas.Cliente;
import com.ondexia.domain.ventas.ClienteRepositorio;
import com.ondexia.domain.ventas.DocumentoVenta;
import com.ondexia.domain.ventas.DocumentoVentaRepositorio;
import com.ondexia.domain.ventas.FormaDePago;
import com.ondexia.domain.ventas.LineaDeVenta;
import com.ondexia.domain.ventas.Pago;
import com.ondexia.domain.ventas.SesionCaja;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El punto de venta (doc 12 §8, iteración 4): emite notas de venta, boletas y
 * facturas dentro de una sesión de caja, con correlativo, líneas, IGV, pago
 * mixto y descarga de existencias en una sola transacción.
 *
 * <p>Los precios los pone el servidor: el que rige en el local o el de lista.
 * La pantalla manda producto y cantidad, y un descuento por línea si lo hay; un
 * precio tecleado en el navegador sería un precio que nadie controla.
 *
 * <p>Boleta y factura nacen {@code PENDIENTE} y salen hacia SUNAT en cuanto la
 * transacción confirma ({@link EmisionElectronica}); pasan a {@code EMITIDO}
 * cuando SUNAT las acepta. La nota de venta nace {@code EMITIDO} y no se envía.
 */
@Service
public class DocumentosDeVenta {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final int MAXIMO_LISTADO = 200;

    private final DocumentoVentaRepositorio documentos;
    private final SesionesDeCaja sesiones;
    private final Productos productos;
    private final ExistenciasRepositorio existencias;
    private final AlmacenRepositorio almacenes;
    private final ClienteRepositorio clientes;
    private final SerieCorrelativoRepositorio series;
    private final AsignadorDeCorrelativo correlativos;
    private final TiposDeComprobante tipos;
    private final ConsultarEmpresa empresa;
    private final RegistroDeAuditoria auditoria;
    private final ProveedorDeContexto contexto;
    private final EmisionElectronica emision;
    private final Clock reloj;

    public DocumentosDeVenta(DocumentoVentaRepositorio documentos, SesionesDeCaja sesiones,
            Productos productos, ExistenciasRepositorio existencias, AlmacenRepositorio almacenes,
            ClienteRepositorio clientes, SerieCorrelativoRepositorio series,
            AsignadorDeCorrelativo correlativos, TiposDeComprobante tipos,
            ConsultarEmpresa empresa, RegistroDeAuditoria auditoria, ProveedorDeContexto contexto,
            EmisionElectronica emision, Clock reloj) {
        this.documentos = documentos;
        this.sesiones = sesiones;
        this.productos = productos;
        this.existencias = existencias;
        this.almacenes = almacenes;
        this.clientes = clientes;
        this.series = series;
        this.correlativos = correlativos;
        this.tipos = tipos;
        this.empresa = empresa;
        this.auditoria = auditoria;
        this.contexto = contexto;
        this.emision = emision;
        this.reloj = reloj;
    }

    public record LineaPedida(UUID productoId, BigDecimal cantidad, BigDecimal descuento) {
    }

    public record PagoPedido(FormaDePago forma, BigDecimal monto, String referencia) {
    }

    /**
     * @param serieId opcional: sin él, la única serie activa del tipo en el
     *                establecimiento. Con varias hay que elegir
     * @param clienteId opcional: el adquirente sin documento no tiene fila
     */
    public record Peticion(TipoDocumento tipo, UUID cajaId, UUID serieId, UUID clienteId,
            List<LineaPedida> lineas, List<PagoPedido> pagos, String observaciones) {
    }

    /** Lo emitido, más lo que conviene decir sin impedir: existencias que quedaron en negativo. */
    public record Emision(DocumentoVenta documento, List<String> avisos) {
    }

    @Transactional
    public Emision emitir(Peticion peticion) {
        Objects.requireNonNull(peticion.tipo(), "tipo");
        ContextoOperacion actual = contexto.obligatorio();
        Empresa laEmpresa = empresa.ejecutar();

        // La sesión de caja manda el establecimiento: una venta ocurre donde
        // está la caja abierta, no donde diga la pantalla.
        SesionCaja sesion = sesiones.abiertaDe(peticion.cajaId())
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "caja_cerrada", "La caja está cerrada. Ábrela antes de vender.", "cajaId"));
        UUID sucursalId = sesiones.cajaDe(sesion).sucursalId();

        exigirTipoEmitible(peticion.tipo(), laEmpresa);
        SerieCorrelativo serie = elegirSerie(peticion.tipo(), sucursalId, peticion.serieId());

        Cliente cliente = peticion.clienteId() == null ? null : clientes.buscarPorId(peticion.clienteId())
                .filter(Cliente::estaActivo)
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "cliente_invalido", "El cliente no existe o está inactivo.", "clienteId"));

        var lineas = construirLineas(peticion.lineas(), sucursalId);
        var pagos = (peticion.pagos() == null ? List.<PagoPedido>of() : peticion.pagos()).stream()
                .map(p -> new Pago(p.forma(), p.monto(), p.referencia()))
                .toList();

        // El correlativo se reserva bajo bloqueo y dentro de esta misma
        // transacción: si algo falla después, el número vuelve (F-02 del DTE).
        long numero = correlativos.siguienteNumero(serie.id());
        Instant ahora = reloj.instant();
        var documento = DocumentoVenta.emitir(UUID.randomUUID(), laEmpresa.id(), sucursalId,
                sesion.id(), peticion.tipo(), serie.serie(), numero, cliente,
                LocalDate.ofInstant(ahora, LIMA), ahora, actual.usuarioId(), lineas, pagos,
                peticion.observaciones());

        var avisos = descargarExistencias(documento, sucursalId, laEmpresa);
        var guardado = documentos.guardar(documento);
        if (guardado.esFiscal()) {
            // La orden sale al confirmar esta misma transacción, no antes.
            emision.encolar(guardado, laEmpresa);
        }
        auditoria.registrarCreacion("documento_venta", guardado.id(), Instantanea.de(guardado));
        return new Emision(guardado, avisos);
    }

    public DocumentoVenta obtener(UUID id) {
        return documentos.buscarPorId(id)
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "documento_no_encontrado", "El documento no existe."));
    }

    public List<DocumentoVenta> recientes(TipoDocumento tipo) {
        return documentos.listarRecientes(tipo, MAXIMO_LISTADO);
    }

    /** Las series activas del tipo en el establecimiento, para que la pantalla ofrezca elegir. */
    public List<SerieCorrelativo> seriesDisponibles(TipoDocumento tipo, UUID sucursalId) {
        return series.listar().stream()
                .filter(s -> s.estaActiva() && s.tipoDocumento() == tipo
                        && s.sucursalId().equals(sucursalId))
                .toList();
    }

    private void exigirTipoEmitible(TipoDocumento tipo, Empresa laEmpresa) {
        if (tipo == TipoDocumento.NOTA_VENTA) {
            return; // Siempre: no es comprobante y no se habilita.
        }
        if (tipo == TipoDocumento.FACTURA && !laEmpresa.emiteFacturas()) {
            throw new ReglaDeNegocioViolada(
                    "nuevo_rus_no_emite_facturas",
                    "Una empresa en el Nuevo RUS no emite facturas. Emite una boleta.");
        }
        if (!tipos.emite(tipo)) {
            throw new ReglaDeNegocioViolada(
                    "tipo_no_habilitado",
                    "La empresa tiene desactivada la emisión de " + tipo.nombre().toLowerCase()
                            + ". Habilítala en Configuración › Comprobantes.");
        }
        // Antes de consumir un correlativo: sin certificado ni clave SOL la
        // boleta no puede salir, y un número gastado en una venta que no se
        // registra es un hueco que justificar.
        emision.exigirConfigurada(laEmpresa);
    }

    private SerieCorrelativo elegirSerie(TipoDocumento tipo, UUID sucursalId, UUID serieId) {
        var candidatas = seriesDisponibles(tipo, sucursalId);
        if (serieId != null) {
            return candidatas.stream().filter(s -> s.id().equals(serieId)).findFirst()
                    .orElseThrow(() -> new ReglaDeNegocioViolada(
                            "serie_invalida",
                            "La serie indicada no es de " + tipo.nombre().toLowerCase()
                                    + " en este establecimiento, o está desactivada.", "serieId"));
        }
        if (candidatas.isEmpty()) {
            throw new ReglaDeNegocioViolada(
                    "sin_serie",
                    "Este establecimiento no tiene una serie activa de " + tipo.nombre().toLowerCase()
                            + ". Créala en Configuración › Series y correlativos.", "serieId");
        }
        if (candidatas.size() > 1) {
            throw new ReglaDeNegocioViolada(
                    "varias_series", "Hay varias series de " + tipo.nombre().toLowerCase()
                            + " en este establecimiento: elige una.", "serieId");
        }
        return candidatas.get(0);
    }

    private List<LineaDeVenta> construirLineas(List<LineaPedida> pedidas, UUID sucursalId) {
        if (pedidas == null || pedidas.isEmpty()) {
            throw new ReglaDeNegocioViolada(
                    "sin_lineas", "Un documento de venta necesita al menos una línea.", "lineas");
        }
        var lineas = new ArrayList<LineaDeVenta>();
        int orden = 1;
        for (LineaPedida pedida : pedidas) {
            Productos.ProductoDisponible disponible = productos.disponibleEn(pedida.productoId(), sucursalId)
                    .orElseThrow(() -> new ReglaDeNegocioViolada(
                            "producto_no_disponible",
                            "Uno de los productos no se vende en este establecimiento o está inactivo.",
                            "lineas"));
            Producto producto = disponible.producto();
            lineas.add(LineaDeVenta.calcular(orden++, producto.id(), producto.codigo(),
                    producto.nombre(), producto.unidad(), producto.afectacion(), pedida.cantidad(),
                    disponible.precio(), pedida.descuento(), producto.controlaStock()));
        }
        return lineas;
    }

    /**
     * Descarga del almacén del local, línea por línea. Con existencias
     * insuficientes se avisa; se rechaza solo si la empresa lo prohibió.
     */
    private List<String> descargarExistencias(DocumentoVenta documento, UUID sucursalId,
            Empresa laEmpresa) {
        var conDescarga = documento.lineas().stream().filter(LineaDeVenta::descargaExistencias).toList();
        if (conDescarga.isEmpty()) {
            return List.of();
        }
        Almacen almacen = almacenDelLocal(sucursalId);
        var avisos = new ArrayList<String>();
        for (LineaDeVenta linea : conDescarga) {
            BigDecimal habia = existencias.buscar(almacen.id(), linea.productoId())
                    .map(Existencia::cantidad).orElse(BigDecimal.ZERO);
            if (habia.compareTo(linea.cantidad()) < 0) {
                if (!laEmpresa.permiteVentaSinStock()) {
                    throw new ReglaDeNegocioViolada(
                            "existencias_insuficientes",
                            "De «" + linea.descripcion() + "» hay " + habia.stripTrailingZeros().toPlainString()
                                    + " en " + almacen.nombre() + " y se piden "
                                    + linea.cantidad().stripTrailingZeros().toPlainString()
                                    + ". La empresa no permite vender sin existencias.", "lineas");
                }
                avisos.add("«" + linea.descripcion() + "» queda en "
                        + habia.subtract(linea.cantidad()).stripTrailingZeros().toPlainString()
                        + " en " + almacen.nombre() + ": conviene contar.");
            }
            existencias.mover(new MovimientoStock(UUID.randomUUID(), almacen.id(), linea.productoId(),
                    linea.cantidad().negate(), MovimientoStock.Tipo.VENTA, documento.tipo().codigo(),
                    documento.id(), documento.numeroCompleto(), documento.emitidoPor(),
                    documento.emitidoEn()));
        }
        return avisos;
    }

    /** El almacén del establecimiento: el activo más antiguo, que es el que nació con él. */
    private Almacen almacenDelLocal(UUID sucursalId) {
        return almacenes.principalDe(sucursalId)
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "sin_almacen_en_el_local",
                        "Este establecimiento no tiene un almacén activo del que descargar. "
                                + "Créalo en Almacén › Almacenes."));
    }

    private record Instantanea(String tipo, String numero, UUID sucursalId, UUID sesionCajaId,
            UUID clienteId, BigDecimal total, String estado) {

        static Instantanea de(DocumentoVenta d) {
            return new Instantanea(d.tipo().codigo(), d.numeroCompleto(), d.sucursalId(),
                    d.sesionCajaId(), d.cliente() == null ? null : d.cliente().id(), d.total(),
                    d.estado().name());
        }
    }
}
