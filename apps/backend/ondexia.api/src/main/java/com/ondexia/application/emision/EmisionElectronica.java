package com.ondexia.application.emision;

import com.ondexia.domain.comprobante.BusDeEmision;
import com.ondexia.domain.comprobante.ClavesDelBus;
import com.ondexia.domain.comprobante.ComprobanteElectronico;
import com.ondexia.domain.comprobante.ComprobanteElectronicoRepositorio;
import com.ondexia.domain.comprobante.EstadoSunat;
import com.ondexia.domain.comprobante.OrdenDeEmision;
import com.ondexia.domain.comprobante.ResultadoDeEmision;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.identidad.EmpresaRepositorio;
import com.ondexia.domain.identidad.Sucursal;
import com.ondexia.domain.identidad.SucursalRepositorio;
import com.ondexia.domain.ventas.DocumentoVenta;
import com.ondexia.domain.ventas.DocumentoVentaRepositorio;
import com.ondexia.domain.ventas.EstadoDocumento;
import com.ondexia.domain.ventas.LineaDeVenta;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * La emisión electrónica desde el lado de la API (doc 14 §2 y §3): encolar una
 * boleta o factura, enterarse de lo que SUNAT dijo y volver a intentar.
 *
 * <h2>La orden sale DESPUÉS de confirmar</h2>
 *
 * <p>Si la orden se publicara dentro de la transacción de la venta y la
 * transacción se deshiciera después —un pago que no cuadra, la base que
 * falla—, el Emisor ya la tendría: firmaría y enviaría a SUNAT un comprobante
 * cuyo número volvió al correlativo y que la siguiente venta va a reutilizar.
 * SUNAT rechazaría la segunda como duplicada, con la primera ya aceptada para
 * una venta que no existió. Por eso la publicación se engancha al
 * {@code afterCommit}: si la transacción no confirma, la orden no sale.
 *
 * <p>El precio de eso: si la publicación falla después de confirmar, el
 * comprobante queda {@code EN_COLA} sin orden. No se pierde: pasada la espera
 * máxima, {@link #reintentar} lo vuelve a publicar.
 *
 * <h2>El resultado se lee cuando alguien pregunta</h2>
 *
 * <p>{@link #estadoDe} mira el bus si el comprobante sigue en cola y aplica lo
 * que encuentre. Es lo que la pantalla llama mientras espera. Ver
 * {@link BusDeEmision} para por qué no hay un evento de vuelta.
 */
@Service
public class EmisionElectronica {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Suficiente para que el navegador empiece la descarga; corta si la URL se comparte. */
    private static final Duration VALIDEZ_DESCARGA = Duration.ofMinutes(5);

    private final ComprobanteElectronicoRepositorio comprobantes;
    private final DocumentoVentaRepositorio documentos;
    private final EmpresaRepositorio empresas;
    private final SucursalRepositorio sucursales;
    private final BusDeEmision bus;
    private final Clock reloj;

    public EmisionElectronica(ComprobanteElectronicoRepositorio comprobantes,
            DocumentoVentaRepositorio documentos, EmpresaRepositorio empresas,
            SucursalRepositorio sucursales, BusDeEmision bus, Clock reloj) {
        this.comprobantes = comprobantes;
        this.documentos = documentos;
        this.empresas = empresas;
        this.sucursales = sucursales;
        this.bus = bus;
        this.reloj = reloj;
    }

    /**
     * Que la empresa pueda emitir, antes de consumir un correlativo. Se llama
     * desde el punto de venta al elegir boleta o factura.
     */
    public void exigirConfigurada(Empresa empresa) {
        if (!empresa.puedeEmitirElectronicamente()) {
            throw new ReglaDeNegocioViolada(
                    "emision_no_configurada",
                    "La empresa no tiene configurada la emisión electrónica: carga el certificado "
                            + "digital y la clave SOL en Configuración › Emisión electrónica. "
                            + "Mientras tanto se pueden emitir notas de venta.");
        }
    }

    /**
     * Crea el comprobante en cola y deja la orden lista para salir al confirmar.
     * Solo desde la transacción que emite el documento: sin ella no habría
     * confirmación a la que engancharse.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ComprobanteElectronico encolar(DocumentoVenta documento, Empresa empresa) {
        exigirConfigurada(empresa);
        var comprobante = ComprobanteElectronico.encolar(UUID.randomUUID(), empresa.id(),
                documento.id(), documento.tipo(), documento.serie(), documento.numero(),
                reloj.instant());
        var guardado = comprobantes.guardar(comprobante);
        publicarTrasConfirmar(bus, construirOrden(guardado, documento, empresa));
        return guardado;
    }

    /**
     * El comprobante de un documento, al día: si sigue en cola y el Emisor ya
     * dejó resultado, se aplica aquí mismo.
     */
    @Transactional
    public ComprobanteElectronico estadoDe(UUID documentoId) {
        var comprobante = exigir(documentoId);
        if (comprobante.estado() != EstadoSunat.EN_COLA) {
            return comprobante;
        }
        return bus.resultadoDe(comprobante.empresaId(), comprobante.id())
                .map(resultado -> aplicar(comprobante, resultado))
                .orElse(comprobante);
    }

    /** Aplica un resultado del Emisor. Público para el ensayo local (doc 14 §6). */
    @Transactional
    public ComprobanteElectronico aplicarResultado(ResultadoDeEmision resultado) {
        var comprobante = comprobantes.buscarPorId(resultado.ordenId())
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "comprobante_no_encontrado", "No hay comprobante para esa orden."));
        return aplicar(comprobante, resultado);
    }

    private ComprobanteElectronico aplicar(ComprobanteElectronico comprobante,
            ResultadoDeEmision resultado) {
        if (!comprobante.aplicar(resultado)) {
            return comprobante;
        }
        if (comprobante.estaAceptado()) {
            var documento = documentos.buscarPorId(comprobante.documentoId())
                    .orElseThrow(() -> new IllegalStateException(
                            "El comprobante " + comprobante.id() + " apunta a un documento que no existe."));
            documento.aceptarPorSunat();
            documentos.guardar(documento);
            anularOriginalSiCorresponde(documento);
        }
        return comprobantes.guardar(comprobante);
    }

    /**
     * Una nota de crédito de anulación aceptada deja sin efecto el comprobante
     * que corrige (doc 13 §5.2).
     *
     * <p>Aquí y no al registrarla: si SUNAT rechazara la nota, la anulación no
     * habría ocurrido y el comprobante seguiría vigente. Ese orden es lo que
     * hace que el estado que se ve en pantalla sea el que SUNAT reconoce.
     */
    private void anularOriginalSiCorresponde(DocumentoVenta nota) {
        if (!nota.esNotaDeCredito() || !nota.motivoNota().anulaElDocumento()
                || nota.documentoOrigenId() == null) {
            return;
        }
        documentos.buscarPorId(nota.documentoOrigenId()).ifPresent(original -> {
            if (original.estado() == EstadoDocumento.EMITIDO) {
                original.anularPorNotaDeCredito();
                documentos.guardar(original);
            }
        });
    }

    /** Vuelve a encolar. Las reglas de cuándo se puede están en el agregado. */
    @Transactional
    public ComprobanteElectronico reintentar(UUID documentoId) {
        var comprobante = exigir(documentoId);
        var documento = documentos.buscarPorId(documentoId)
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "documento_no_encontrado", "El documento no existe."));
        var empresa = empresas.buscarPorId(comprobante.empresaId())
                .orElseThrow(() -> new IllegalStateException("La empresa del comprobante no existe."));
        exigirConfigurada(empresa);
        comprobante.reintentar(reloj.instant());
        var guardado = comprobantes.guardar(comprobante);
        publicarTrasConfirmar(bus, construirOrden(guardado, documento, empresa));
        return guardado;
    }

    public Map<UUID, EstadoSunat> estadosDe(Collection<UUID> documentoIds) {
        return comprobantes.estadosDe(documentoIds);
    }

    /** URL temporal del XML firmado, o error si todavía no hay. */
    public String urlDelXml(UUID documentoId) {
        var comprobante = exigir(documentoId);
        if (comprobante.claveXml() == null) {
            throw RecursoNoEncontrado.con("xml_no_disponible", "El XML todavía no se generó.");
        }
        return bus.urlDeDescarga(comprobante.claveXml(), VALIDEZ_DESCARGA);
    }

    public String urlDelCdr(UUID documentoId) {
        var comprobante = exigir(documentoId);
        if (comprobante.claveCdr() == null) {
            throw RecursoNoEncontrado.con("cdr_no_disponible", "SUNAT todavía no devolvió el CDR.");
        }
        return bus.urlDeDescarga(comprobante.claveCdr(), VALIDEZ_DESCARGA);
    }

    private ComprobanteElectronico exigir(UUID documentoId) {
        return comprobantes.buscarPorDocumento(documentoId)
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "comprobante_no_encontrado",
                        "El documento no tiene comprobante electrónico: no es boleta ni factura."));
    }

    /**
     * Engancha la publicación al final feliz de la transacción en curso. Sin
     * transacción —no debería pasar— publica en el acto, que es lo que hacía
     * antes de existir esta clase y es mejor que perder la orden en silencio.
     */
    static void publicarTrasConfirmar(BusDeEmision bus, OrdenDeEmision orden) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            bus.publicar(orden);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                bus.publicar(orden);
            }
        });
    }

    /** Todo lo que el XML necesita, copiado y ya calculado: el Emisor no suma nada. */
    OrdenDeEmision construirOrden(ComprobanteElectronico comprobante, DocumentoVenta d, Empresa e) {
        Sucursal sucursal = sucursales.buscarPorId(d.sucursalId())
                .orElseThrow(() -> new IllegalStateException(
                        "El documento " + d.numeroCompleto() + " apunta a un establecimiento que no existe."));
        var emisor = new OrdenDeEmision.Emisor(e.ruc().valor(), e.razonSocial(), e.nombreComercial(),
                sucursal.direccion() == null ? e.domicilioFiscal() : sucursal.direccion(),
                sucursal.ubigeo() != null ? sucursal.ubigeo().valor()
                        : e.ubigeo() == null ? null : e.ubigeo().valor(),
                sucursal.codigo(), e.usuarioSol());
        var cliente = d.cliente();
        var adquirente = cliente == null ? null : new OrdenDeEmision.Adquirente(
                cliente.tipoDocumento().codigo(), cliente.numeroDocumento(), cliente.nombre(),
                cliente.direccion());
        var lineas = d.lineas().stream().map(EmisionElectronica::aLinea).toList();
        Instant emitidoEn = d.emitidoEn();
        var documento = new OrdenDeEmision.Documento(d.tipo().codigo(), d.serie(), d.numero(),
                d.fechaEmision(), LocalTime.ofInstant(emitidoEn, LIMA).withNano(0), "PEN",
                adquirente, lineas, d.totalGravado(), d.totalExonerado(), d.totalInafecto(),
                d.totalDescuento(), d.totalIgv(), d.total(), d.observaciones(),
                d.motivoNota() == null ? null : d.motivoNota().codigo(),
                // La referencia solo viaja en la nota de crédito. En un canje
                // existe igual en la base —de dónde salió el comprobante— pero
                // el XML de una boleta no la lleva: para SUNAT es una boleta
                // corriente, y la nota de venta no es un documento que conozca.
                d.esNotaDeCredito() && d.origen() != null
                        ? new OrdenDeEmision.Referencia(d.origen().tipo().codigo(),
                                d.origen().serie(), d.origen().numero())
                        : null);
        return new OrdenDeEmision(comprobante.id(), OrdenDeEmision.Operacion.EMITIR, e.id(),
                e.modoSunat(), emisor, documento, reloj.instant());
    }

    private static OrdenDeEmision.Linea aLinea(LineaDeVenta l) {
        return new OrdenDeEmision.Linea(l.orden(), l.descripcion(), l.unidad().codigo(),
                l.cantidad(), l.precioUnitario(), l.valorUnitario(), l.descuento(),
                l.afectacion().codigo(), l.valorVenta(), l.igv(), l.total());
    }

    /** Hoy en Lima: es la fecha contra la que se comprueba la vigencia del certificado. */
    LocalDate hoy() {
        return LocalDate.ofInstant(reloj.instant(), LIMA);
    }

    /** Dónde queda el XML de un comprobante, por si la pantalla quiere saberlo. */
    static String claveXml(String ruc, OrdenDeEmision.Documento documento) {
        return ClavesDelBus.xml(ruc, documento.nombreDeArchivo(ruc));
    }
}
