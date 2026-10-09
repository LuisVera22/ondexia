package com.ondexia.application.ventas;

import com.ondexia.application.comprobante.AsignadorDeCorrelativo;
import com.ondexia.application.comprobante.TiposDeComprobante;
import com.ondexia.application.configuracion.ConsultarEmpresa;
import com.ondexia.application.emision.EmisionElectronica;
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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Convertir una nota de venta en boleta o factura (doc 13 §5.1).
 *
 * <p>Es lo que hace que la nota de venta sea útil en un mostrador real: se cobra
 * rápido y se documenta después, cuando el cliente pide su comprobante. Copia
 * las líneas del original y deja la referencia en los dos sentidos; la nota de
 * venta pasa a {@code CANJEADO} y no se borra nunca.
 *
 * <h2>Lo que no se repite</h2>
 *
 * <p>Ni el cobro ni la salida de mercadería: los dos ocurrieron con la nota de
 * venta. El comprobante hereda sus pagos para lo que se imprime, no para el
 * arqueo —{@link DocumentoVenta#canjear} explica por qué—, y no toca
 * existencias.
 *
 * <h2>Se marca canjeada al emitir, no al aceptar SUNAT</h2>
 *
 * <p>Al revés que la anulación por nota de crédito. El motivo es lo que cada
 * estado protege: {@code CANJEADO} impide canjear dos veces la misma nota, y
 * eso tiene que valer desde el momento en que existe el comprobante, aunque
 * SUNAT todavía no haya respondido. Si lo rechaza, lo que se corrige y reenvía
 * es ese comprobante; no se canjea otra vez.
 */
@Service
public class CanjeDeNotaDeVenta {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final DocumentoVentaRepositorio documentos;
    private final ClienteRepositorio clientes;
    private final SerieCorrelativoRepositorio series;
    private final AsignadorDeCorrelativo correlativos;
    private final TiposDeComprobante tipos;
    private final ConsultarEmpresa empresa;
    private final EmisionElectronica emision;
    private final RegistroDeAuditoria auditoria;
    private final ProveedorDeContexto contexto;
    private final Clock reloj;

    public CanjeDeNotaDeVenta(DocumentoVentaRepositorio documentos, ClienteRepositorio clientes,
            SerieCorrelativoRepositorio series, AsignadorDeCorrelativo correlativos,
            TiposDeComprobante tipos, ConsultarEmpresa empresa, EmisionElectronica emision,
            RegistroDeAuditoria auditoria, ProveedorDeContexto contexto, Clock reloj) {
        this.documentos = documentos;
        this.clientes = clientes;
        this.series = series;
        this.correlativos = correlativos;
        this.tipos = tipos;
        this.empresa = empresa;
        this.emision = emision;
        this.auditoria = auditoria;
        this.contexto = contexto;
        this.reloj = reloj;
    }

    /**
     * @param clienteId el adquirente del comprobante. Nulo conserva el de la
     *                  nota de venta, que puede no tener: una factura entonces
     *                  se rechaza por falta de RUC, que es lo correcto
     */
    public record Peticion(UUID notaVentaId, TipoDocumento tipo, UUID serieId, UUID clienteId,
            String observaciones) {
    }

    @Transactional
    public DocumentoVenta canjear(Peticion peticion) {
        Objects.requireNonNull(peticion.tipo(), "tipo");
        ContextoOperacion actual = contexto.obligatorio();
        Empresa laEmpresa = empresa.ejecutar();
        emision.exigirConfigurada(laEmpresa);

        DocumentoVenta nota = documentos.buscarPorId(peticion.notaVentaId())
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "documento_no_encontrado", "La nota de venta no existe."));
        exigirTipoEmitible(peticion.tipo(), laEmpresa);

        Cliente cliente = peticion.clienteId() == null
                ? nota.cliente()
                : clientes.buscarPorId(peticion.clienteId())
                        .filter(Cliente::estaActivo)
                        .orElseThrow(() -> new ReglaDeNegocioViolada(
                                "cliente_invalido", "El cliente no existe o está inactivo.",
                                "clienteId"));

        SerieCorrelativo serie = elegirSerie(peticion.tipo(), nota.sucursalId(), peticion.serieId());
        long numero = correlativos.siguienteNumero(serie.id());
        Instant ahora = reloj.instant();

        var comprobante = DocumentoVenta.canjear(UUID.randomUUID(), nota, peticion.tipo(),
                serie.serie(), numero, cliente, LocalDate.ofInstant(ahora, LIMA), ahora,
                actual.usuarioId(), peticion.observaciones());

        nota.marcarCanjeada();
        documentos.guardar(nota);
        var guardado = documentos.guardar(comprobante);
        emision.encolar(guardado, laEmpresa);
        auditoria.registrar("documento_venta", nota.id(), "CANJEAR",
                Instantanea.de(nota), Instantanea.de(guardado));
        return guardado;
    }

    /** Las series del tipo elegido en el establecimiento de la nota de venta. */
    public List<SerieCorrelativo> seriesDisponibles(TipoDocumento tipo, UUID sucursalId) {
        return series.listar().stream()
                .filter(s -> s.estaActiva() && s.tipoDocumento() == tipo
                        && sucursalId.equals(s.sucursalId()))
                .toList();
    }

    private void exigirTipoEmitible(TipoDocumento tipo, Empresa laEmpresa) {
        if (tipo != TipoDocumento.BOLETA && tipo != TipoDocumento.FACTURA) {
            throw new ReglaDeNegocioViolada(
                    "tipo_de_canje_invalido",
                    "Una nota de venta se canjea por boleta o por factura.");
        }
        if (tipo == TipoDocumento.FACTURA && !laEmpresa.emiteFacturas()) {
            throw new ReglaDeNegocioViolada(
                    "nuevo_rus_no_emite_facturas",
                    "Una empresa en el Nuevo RUS no emite facturas. Canjea por boleta.");
        }
        if (!tipos.emite(tipo)) {
            throw new ReglaDeNegocioViolada(
                    "tipo_no_habilitado",
                    "La empresa tiene desactivada la emisión de " + tipo.nombre().toLowerCase()
                            + ". Habilítala en Configuración › Comprobantes.");
        }
    }

    private SerieCorrelativo elegirSerie(TipoDocumento tipo, UUID sucursalId, UUID serieId) {
        var candidatas = seriesDisponibles(tipo, sucursalId);
        if (serieId != null) {
            return candidatas.stream().filter(s -> s.id().equals(serieId)).findFirst()
                    .orElseThrow(() -> new ReglaDeNegocioViolada(
                            "serie_invalida",
                            "La serie indicada no es de " + tipo.nombre().toLowerCase()
                                    + " en el establecimiento de la nota de venta.", "serieId"));
        }
        if (candidatas.isEmpty()) {
            throw new ReglaDeNegocioViolada(
                    "sin_serie",
                    "El establecimiento de la nota de venta no tiene una serie activa de "
                            + tipo.nombre().toLowerCase() + ".", "serieId");
        }
        if (candidatas.size() > 1) {
            throw new ReglaDeNegocioViolada(
                    "varias_series", "Hay varias series de " + tipo.nombre().toLowerCase()
                            + " en ese establecimiento: elige una.", "serieId");
        }
        return candidatas.get(0);
    }

    private record Instantanea(String tipo, String numero, String estado, String origen,
            BigDecimal total) {

        static Instantanea de(DocumentoVenta d) {
            return new Instantanea(d.tipo().codigo(), d.numeroCompleto(), d.estado().name(),
                    d.origen() == null ? null : d.origen().numeroCompleto(), d.total());
        }
    }
}
