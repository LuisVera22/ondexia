package com.ondexia.application.emision;

import com.ondexia.application.configuracion.ConsultarEmpresa;
import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comprobante.BusDeEmision;
import com.ondexia.domain.comprobante.ComunicacionDeBaja;
import com.ondexia.domain.comprobante.ComunicacionDeBajaRepositorio;
import com.ondexia.domain.comprobante.EstadoSunat;
import com.ondexia.domain.comprobante.OrdenDeEmision;
import com.ondexia.domain.comprobante.ResultadoDeEmision;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.identidad.SucursalRepositorio;
import com.ondexia.domain.ventas.DocumentoVenta;
import com.ondexia.domain.ventas.DocumentoVentaRepositorio;
import com.ondexia.domain.ventas.EstadoDocumento;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dar de baja facturas ante SUNAT (doc 13 §6).
 *
 * <h2>El envío es asíncrono, y de eso sale casi todo lo demás</h2>
 *
 * <p>Una comunicación de baja no vuelve con el CDR: SUNAT la recibe y devuelve
 * un ticket. La respuesta se pide después, y quien la pide es
 * {@link #sincronizarPendientes()} —que llama el planificador cada pocos
 * minutos— o la propia pantalla al mirar una comunicación en curso.
 *
 * <p>Eso significa que hay <strong>dos</strong> viajes al Emisor por
 * comunicación: {@code ENVIAR_BAJA} y, más tarde, {@code CONSULTAR_TICKET}. Los
 * dos van por el mismo bus y con el mismo mecanismo de resultados.
 */
@Service
public class ComunicacionesDeBaja {

    private static final Logger LOG = LoggerFactory.getLogger(ComunicacionesDeBaja.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final int MAXIMO_LISTADO = 200;
    private static final Duration VALIDEZ_DESCARGA = Duration.ofMinutes(5);

    /**
     * Cuánto se espera antes de volver a preguntar por un ticket. SUNAT suele
     * tenerlo listo en menos de un minuto; preguntar cada pocos segundos solo
     * gastaría invocaciones del Emisor sin adelantar nada.
     */
    static final Duration ESPERA_ENTRE_CONSULTAS = Duration.ofSeconds(45);

    private final ComunicacionDeBajaRepositorio comunicaciones;
    private final DocumentoVentaRepositorio documentos;
    private final SucursalRepositorio sucursales;
    private final ConsultarEmpresa empresa;
    private final EmisionElectronica emision;
    private final BusDeEmision bus;
    private final RegistroDeAuditoria auditoria;
    private final ProveedorDeContexto contexto;
    private final Clock reloj;

    public ComunicacionesDeBaja(ComunicacionDeBajaRepositorio comunicaciones,
            DocumentoVentaRepositorio documentos, SucursalRepositorio sucursales,
            ConsultarEmpresa empresa, EmisionElectronica emision, BusDeEmision bus,
            RegistroDeAuditoria auditoria, ProveedorDeContexto contexto, Clock reloj) {
        this.comunicaciones = comunicaciones;
        this.documentos = documentos;
        this.sucursales = sucursales;
        this.empresa = empresa;
        this.emision = emision;
        this.bus = bus;
        this.auditoria = auditoria;
        this.contexto = contexto;
        this.reloj = reloj;
    }

    /** @param motivo por qué el comprobante no debió existir. Va en el XML */
    public record ComprobanteAAnular(UUID documentoId, String motivo) {
    }

    @Transactional
    public ComunicacionDeBaja crear(List<ComprobanteAAnular> pedidos) {
        var actual = contexto.obligatorio();
        Empresa laEmpresa = empresa.ejecutar();
        emision.exigirConfigurada(laEmpresa);
        if (pedidos == null || pedidos.isEmpty()) {
            throw new ReglaDeNegocioViolada(
                    "sin_comprobantes",
                    "Elige al menos una factura para dar de baja.", "comprobantes");
        }

        var renglones = new ArrayList<ComunicacionDeBaja.Renglon>();
        LocalDate fechaComun = null;
        for (ComprobanteAAnular pedido : pedidos) {
            DocumentoVenta documento = documentos.buscarPorId(pedido.documentoId())
                    .orElseThrow(() -> RecursoNoEncontrado.con(
                            "documento_no_encontrado", "El comprobante no existe."));
            exigirAnulable(documento);
            if (fechaComun == null) {
                fechaComun = documento.fechaEmision();
            } else if (!fechaComun.equals(documento.fechaEmision())) {
                // Lo exige SUNAT, y conviene decirlo aquí: el rechazo por esta
                // causa llega con un código que hay que ir a buscar.
                throw new ReglaDeNegocioViolada(
                        "fechas_distintas",
                        "Una comunicación de baja cubre comprobantes de un solo día. "
                                + documento.numeroCompleto() + " es del "
                                + documento.fechaEmision() + " y los demás del " + fechaComun + ".",
                        "comprobantes");
            }
            renglones.add(new ComunicacionDeBaja.Renglon(documento.id(), documento.tipo(),
                    documento.serie(), documento.numero(), pedido.motivo()));
        }

        LocalDate hoy = LocalDate.ofInstant(reloj.instant(), LIMA);
        var comunicacion = ComunicacionDeBaja.crear(UUID.randomUUID(), laEmpresa.id(), renglones,
                comunicaciones.siguienteNumeroDelDia(hoy), fechaComun, hoy, actual.usuarioId(),
                reloj.instant());

        var guardada = comunicaciones.guardar(comunicacion);
        EmisionElectronica.publicarTrasConfirmar(bus, ordenDeEnvio(guardada, laEmpresa));
        auditoria.registrarCreacion("comunicacion_baja", guardada.id(), Instantanea.de(guardada));
        return guardada;
    }

    /**
     * El comprobante tiene que existir para SUNAT y no estar ya de baja ni en
     * camino de estarlo.
     */
    private void exigirAnulable(DocumentoVenta documento) {
        if (documento.estado() != EstadoDocumento.EMITIDO) {
            throw new ReglaDeNegocioViolada(
                    "documento_no_anulable",
                    "El comprobante " + documento.numeroCompleto() + " está "
                            + documento.estado().name().toLowerCase()
                            + ". La baja se comunica sobre un comprobante que SUNAT aceptó.",
                    "comprobantes");
        }
        boolean yaEnCurso = comunicaciones.queIncluyen(documento.id()).stream()
                .anyMatch(c -> c.estado().estaEnCurso() || c.fueAceptada());
        if (yaEnCurso) {
            throw new ReglaDeNegocioViolada(
                    "baja_en_curso",
                    "El comprobante " + documento.numeroCompleto()
                            + " ya está en una comunicación de baja.", "comprobantes");
        }
    }

    /**
     * Mira si el Emisor respondió a lo que esté en curso, y pide el estado de
     * los tickets que llevan esperando lo suficiente.
     *
     * <p>Lo llama el planificador y también la pantalla al abrir la lista. Es
     * idempotente: si no hay nada que hacer, no hace nada.
     *
     * @return cuántas comunicaciones cambiaron de estado
     */
    @Transactional
    public int sincronizarPendientes() {
        Empresa laEmpresa = empresa.ejecutar();
        Instant ahora = reloj.instant();
        int cambiadas = 0;
        for (ComunicacionDeBaja comunicacion : comunicaciones.enCurso()) {
            var resultado = bus.resultadoDe(comunicacion.empresaId(), idDeLaConsulta(comunicacion));
            if (resultado.isEmpty() && comunicacion.estado() == EstadoSunat.EN_COLA) {
                resultado = bus.resultadoDe(comunicacion.empresaId(), comunicacion.id());
            }
            if (resultado.isPresent()) {
                if (aplicar(comunicacion, resultado.get())) {
                    cambiadas++;
                }
                continue;
            }
            // Sin respuesta todavía: si tiene ticket y ya esperó, se pregunta.
            if (comunicacion.estado() == EstadoSunat.EN_PROCESO
                    && comunicacion.llevaEsperando(ESPERA_ENTRE_CONSULTAS, ahora)) {
                EmisionElectronica.publicarTrasConfirmar(bus,
                        OrdenDeEmision.paraConsultarTicket(idDeLaConsulta(comunicacion),
                                comunicacion.empresaId(), laEmpresa.modoSunat(), emisor(laEmpresa),
                                comunicacion.ticket(), ahora));
            }
        }
        return cambiadas;
    }

    /** Aplica lo que el Emisor dejó. Público para el ensayo local (doc 14 §6). */
    @Transactional
    public ComunicacionDeBaja aplicarResultado(ResultadoDeEmision resultado) {
        var comunicacion = comunicaciones.enCurso().stream()
                .filter(c -> c.id().equals(resultado.ordenId())
                        || idDeLaConsulta(c).equals(resultado.ordenId()))
                .findFirst()
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "comunicacion_no_encontrada", "No hay comunicación en curso para esa orden."));
        aplicar(comunicacion, resultado);
        return comunicaciones.buscarPorId(comunicacion.id()).orElse(comunicacion);
    }

    /**
     * Un envío devuelve ticket y deja la comunicación en proceso; una consulta
     * devuelve el veredicto. Los dos llegan como {@link ResultadoDeEmision}, y
     * lo que los distingue es que el envío trae ticket.
     */
    private boolean aplicar(ComunicacionDeBaja comunicacion, ResultadoDeEmision resultado) {
        Instant ahora = resultado.procesadoEn() == null ? reloj.instant() : resultado.procesadoEn();
        if (resultado.estado() == EstadoSunat.EN_PROCESO) {
            if (comunicacion.estado() != EstadoSunat.EN_COLA) {
                return false;
            }
            comunicacion.anotarTicket(resultado.codigo(), resultado.claveXml(), ahora);
            comunicaciones.guardar(comunicacion);
            return true;
        }
        if (!comunicacion.resolver(resultado.estado(), resultado.codigo(), resultado.descripcion(),
                resultado.claveCdr(), ahora)) {
            return false;
        }
        if (comunicacion.fueAceptada()) {
            anularLosComprobantes(comunicacion);
        }
        comunicaciones.guardar(comunicacion);
        LOG.info("Comunicación de baja {} → {} {}", comunicacion.identificador(),
                comunicacion.estado(), comunicacion.codigoSunat());
        return true;
    }

    /**
     * SUNAT aceptó la baja: los comprobantes dejan de existir. Aquí y no al
     * crearla, por lo mismo que la nota de crédito: si SUNAT la rechaza, la baja
     * no ocurrió.
     */
    private void anularLosComprobantes(ComunicacionDeBaja comunicacion) {
        for (var renglon : comunicacion.comprobantes()) {
            documentos.buscarPorId(renglon.documentoId()).ifPresent(documento -> {
                if (documento.estado() == EstadoDocumento.EMITIDO) {
                    documento.anularPorNotaDeCredito();
                    documentos.guardar(documento);
                }
            });
        }
    }

    @Transactional
    public ComunicacionDeBaja reintentar(UUID id) {
        Empresa laEmpresa = empresa.ejecutar();
        var comunicacion = obtener(id);
        comunicacion.reintentar(reloj.instant());
        var guardada = comunicaciones.guardar(comunicacion);
        EmisionElectronica.publicarTrasConfirmar(bus, ordenDeEnvio(guardada, laEmpresa));
        return guardada;
    }

    public ComunicacionDeBaja obtener(UUID id) {
        return comunicaciones.buscarPorId(id)
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "comunicacion_no_encontrada", "La comunicación de baja no existe."));
    }

    public List<ComunicacionDeBaja> recientes() {
        return comunicaciones.listarRecientes(MAXIMO_LISTADO);
    }

    /** Las comunicaciones que incluyen ese documento, para su ficha. */
    public List<ComunicacionDeBaja> queIncluyen(UUID documentoId) {
        return comunicaciones.queIncluyen(documentoId);
    }

    public String urlDelXml(UUID id) {
        var comunicacion = obtener(id);
        if (comunicacion.claveXml() == null) {
            throw RecursoNoEncontrado.con("xml_no_disponible", "El XML todavía no se generó.");
        }
        return bus.urlDeDescarga(comunicacion.claveXml(), VALIDEZ_DESCARGA);
    }

    public String urlDelCdr(UUID id) {
        var comunicacion = obtener(id);
        if (comunicacion.claveCdr() == null) {
            throw RecursoNoEncontrado.con("cdr_no_disponible", "SUNAT todavía no devolvió el CDR.");
        }
        return bus.urlDeDescarga(comunicacion.claveCdr(), VALIDEZ_DESCARGA);
    }

    /**
     * La consulta del ticket va con un identificador propio y derivado del de la
     * comunicación, para que su resultado no pise el del envío: son dos
     * respuestas distintas en el mismo bus.
     */
    public static UUID idDeLaConsulta(ComunicacionDeBaja comunicacion) {
        return UUID.nameUUIDFromBytes(("ticket:" + comunicacion.id() + ":"
                + comunicacion.intentos()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private OrdenDeEmision ordenDeEnvio(ComunicacionDeBaja comunicacion, Empresa laEmpresa) {
        var baja = new OrdenDeEmision.Baja(comunicacion.numeroDelDia(),
                comunicacion.fechaDeLosComprobantes(), comunicacion.fechaDeGeneracion(),
                comunicacion.comprobantes().stream()
                        .map(r -> new OrdenDeEmision.ComprobanteDadoDeBaja(r.tipo().codigo(),
                                r.serie(), r.numero(), r.motivo()))
                        .toList());
        return OrdenDeEmision.paraBaja(comunicacion.id(), laEmpresa.id(), laEmpresa.modoSunat(),
                emisor(laEmpresa), baja, reloj.instant());
    }

    /**
     * El emisor de la comunicación es la empresa, no un establecimiento: una
     * baja no pertenece a un local. Se usa el código de la matriz.
     */
    private OrdenDeEmision.Emisor emisor(Empresa e) {
        String codigoMatriz = sucursales.listarDeEmpresa(e.id()).stream()
                .map(s -> s.codigo()).sorted().findFirst().orElse("0000");
        return new OrdenDeEmision.Emisor(e.ruc().valor(), e.razonSocial(), e.nombreComercial(),
                e.domicilioFiscal(), e.ubigeo() == null ? null : e.ubigeo().valor(), codigoMatriz,
                e.usuarioSol());
    }

    private record Instantanea(String identificador, String fechaComprobantes, int comprobantes,
            String estado) {

        static Instantanea de(ComunicacionDeBaja c) {
            return new Instantanea(c.identificador(), c.fechaDeLosComprobantes().toString(),
                    c.comprobantes().size(), c.estado().name());
        }
    }
}
