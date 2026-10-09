package com.ondexia.domain.comprobante;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * La vida de una boleta o factura ante SUNAT (doc 14 §3).
 *
 * <p>Un documento de venta fiscal tiene exactamente uno. Nace {@code EN_COLA}
 * cuando el documento se emite, y de ahí solo sale por lo que el Emisor traiga
 * de vuelta o por una orden expresa de reintentar. Nunca se borra: si SUNAT lo
 * rechazó, el rechazo queda y se ve.
 *
 * <h2>La máquina de estados, con lo que cada transición prohíbe</h2>
 *
 * <pre>
 *   EN_COLA ──resultado──▶ ACEPTADO          (final; iteración 6: ANULADO)
 *      ▲    ──resultado──▶ RECHAZADO ──┐
 *      │    ──resultado──▶ ERROR_ENVIO ─┤
 *      └───────── reintentar ◀──────────┘
 * </pre>
 *
 * <p>Un {@code ACEPTADO} no se toca: SUNAT ya tiene el comprobante y volver a
 * enviarlo produce un rechazo por duplicado o, peor, un CDR distinto para el
 * mismo número. Un resultado que llegue tarde para un comprobante que ya se
 * reintentó se ignora: lo dice {@link #aplicar} devolviendo {@code false}.
 */
public class ComprobanteElectronico {

    /**
     * Cuánto se espera al Emisor antes de admitir que la orden se perdió y dejar
     * volver a encolar. Una emisión normal tarda segundos; diez minutos cubren
     * un arranque en frío lento y un reintento de S3.
     */
    public static final Duration ESPERA_MAXIMA_EN_COLA = Duration.ofMinutes(10);

    private final UUID id;
    private final UUID empresaId;
    private final UUID documentoId;
    private final TipoDocumento tipo;
    private final String serie;
    private final long numero;
    private EstadoSunat estado;
    private int intentos;
    private Instant encoladoEn;
    private Instant respondidoEn;
    private String codigoSunat;
    private String descripcionSunat;
    private List<String> observaciones;
    private String claveXml;
    private String claveCdr;
    private String resumenFirma;

    private ComprobanteElectronico(UUID id, UUID empresaId, UUID documentoId, TipoDocumento tipo,
            String serie, long numero, EstadoSunat estado, int intentos, Instant encoladoEn,
            Instant respondidoEn, String codigoSunat, String descripcionSunat,
            List<String> observaciones, String claveXml, String claveCdr, String resumenFirma) {
        this.id = id;
        this.empresaId = empresaId;
        this.documentoId = documentoId;
        this.tipo = tipo;
        this.serie = serie;
        this.numero = numero;
        this.estado = estado;
        this.intentos = intentos;
        this.encoladoEn = encoladoEn;
        this.respondidoEn = respondidoEn;
        this.codigoSunat = codigoSunat;
        this.descripcionSunat = descripcionSunat;
        this.observaciones = observaciones == null ? List.of() : List.copyOf(observaciones);
        this.claveXml = claveXml;
        this.claveCdr = claveCdr;
        this.resumenFirma = resumenFirma;
    }

    /** Nace en cola, con el primer intento contado. */
    public static ComprobanteElectronico encolar(UUID id, UUID empresaId, UUID documentoId,
            TipoDocumento tipo, String serie, long numero, Instant ahora) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(empresaId, "empresaId");
        Objects.requireNonNull(documentoId, "documentoId");
        Objects.requireNonNull(tipo, "tipo");
        if (!tipo.esFiscal()) {
            throw new ReglaDeNegocioViolada(
                    "documento_no_fiscal",
                    "Una " + tipo.nombre().toLowerCase() + " no se declara a SUNAT.");
        }
        return new ComprobanteElectronico(id, empresaId, documentoId, tipo, serie, numero,
                EstadoSunat.EN_COLA, 1, ahora, null, null, null, List.of(), null, null, null);
    }

    /** Reconstrucción desde la persistencia: no valida. */
    public static ComprobanteElectronico reconstruir(UUID id, UUID empresaId, UUID documentoId,
            TipoDocumento tipo, String serie, long numero, EstadoSunat estado, int intentos,
            Instant encoladoEn, Instant respondidoEn, String codigoSunat, String descripcionSunat,
            List<String> observaciones, String claveXml, String claveCdr, String resumenFirma) {
        return new ComprobanteElectronico(id, empresaId, documentoId, tipo, serie, numero, estado,
                intentos, encoladoEn, respondidoEn, codigoSunat, descripcionSunat, observaciones,
                claveXml, claveCdr, resumenFirma);
    }

    /**
     * Aplica lo que el Emisor devolvió.
     *
     * @return {@code false} si el resultado ya no corresponde —el comprobante
     *         no estaba en cola— y se ignoró. Un resultado tardío de un intento
     *         anterior no debe pisar lo que pasó después
     */
    public boolean aplicar(ResultadoDeEmision resultado) {
        Objects.requireNonNull(resultado, "resultado");
        if (!resultado.ordenId().equals(id)) {
            throw new IllegalArgumentException(
                    "El resultado " + resultado.ordenId() + " no es del comprobante " + id + ".");
        }
        if (estado != EstadoSunat.EN_COLA) {
            return false;
        }
        switch (resultado.estado()) {
            case ACEPTADO, RECHAZADO, ERROR_ENVIO -> this.estado = resultado.estado();
            default -> throw new IllegalArgumentException(
                    "Un resultado de emisión no puede dejar el comprobante en " + resultado.estado());
        }
        this.respondidoEn = resultado.procesadoEn();
        this.codigoSunat = resultado.codigo();
        this.descripcionSunat = resultado.descripcion();
        this.observaciones = resultado.observaciones();
        // El XML se conserva aunque SUNAT rechace: es lo que hay que mirar para
        // entender el rechazo. El CDR solo llega con respuesta de SUNAT.
        if (resultado.claveXml() != null) {
            this.claveXml = resultado.claveXml();
        }
        this.claveCdr = resultado.claveCdr();
        if (resultado.resumenFirma() != null) {
            this.resumenFirma = resultado.resumenFirma();
        }
        return true;
    }

    /**
     * Vuelve a la cola para un nuevo envío.
     *
     * <p>Desde {@code RECHAZADO} o {@code ERROR_ENVIO} siempre. Desde
     * {@code EN_COLA} solo si el Emisor lleva más de {@link #ESPERA_MAXIMA_EN_COLA}
     * sin responder: la orden se perdió, o nunca llegó a publicarse. Antes de
     * ese plazo sería mandar dos órdenes iguales.
     */
    public void reintentar(Instant ahora) {
        if (estado == EstadoSunat.EN_COLA) {
            if (encoladoEn != null && Duration.between(encoladoEn, ahora).compareTo(ESPERA_MAXIMA_EN_COLA) < 0) {
                throw new ReglaDeNegocioViolada(
                        "emision_en_curso",
                        "El comprobante está en cola desde hace menos de "
                                + ESPERA_MAXIMA_EN_COLA.toMinutes()
                                + " minutos. Espera la respuesta antes de reintentar.");
            }
        } else if (!estado.admiteReintento()) {
            throw new ReglaDeNegocioViolada(
                    "reintento_no_admitido",
                    "Un comprobante " + estado.name().toLowerCase() + " no se vuelve a enviar.");
        }
        this.estado = EstadoSunat.EN_COLA;
        this.intentos++;
        this.encoladoEn = ahora;
        this.respondidoEn = null;
        this.claveCdr = null;
    }

    public boolean estaAceptado() {
        return estado == EstadoSunat.ACEPTADO;
    }

    public UUID id() {
        return id;
    }

    public UUID empresaId() {
        return empresaId;
    }

    public UUID documentoId() {
        return documentoId;
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

    public EstadoSunat estado() {
        return estado;
    }

    public int intentos() {
        return intentos;
    }

    public Instant encoladoEn() {
        return encoladoEn;
    }

    public Instant respondidoEn() {
        return respondidoEn;
    }

    public String codigoSunat() {
        return codigoSunat;
    }

    public String descripcionSunat() {
        return descripcionSunat;
    }

    public List<String> observaciones() {
        return observaciones;
    }

    public String claveXml() {
        return claveXml;
    }

    public String claveCdr() {
        return claveCdr;
    }

    public String resumenFirma() {
        return resumenFirma;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof ComprobanteElectronico c && id.equals(c.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
