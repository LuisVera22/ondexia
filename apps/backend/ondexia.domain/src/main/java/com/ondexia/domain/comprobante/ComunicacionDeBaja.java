package com.ondexia.domain.comprobante;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * La comunicación de baja: decirle a SUNAT que unas facturas no existen
 * (doc 13 §6).
 *
 * <h2>En qué se diferencia de una nota de crédito</h2>
 *
 * <p>La nota de crédito corrige una operación que ocurrió: se devolvió la
 * mercadería, se descontó, se anuló la venta. La comunicación de baja dice que
 * el comprobante <strong>no debió existir</strong> —se emitió por error, con el
 * cliente equivocado, duplicado— y por eso no lleva importes ni mueve
 * existencias: solo la lista de comprobantes y el motivo de cada uno.
 *
 * <h2>El envío es asíncrono, y eso cambia la máquina de estados</h2>
 *
 * <p>Un comprobante se envía y SUNAT contesta con el CDR en la misma llamada.
 * Una comunicación de baja no: SUNAT la recibe, devuelve un <em>ticket</em>, y
 * la respuesta se pide después. De ahí el estado {@code EN_PROCESO}, que un
 * comprobante nunca tiene:
 *
 * <pre>
 *   EN_COLA ──enviada──▶ EN_PROCESO(ticket) ──consulta──▶ ACEPTADO
 *      ▲                      │                       └─▶ RECHAZADO ─┐
 *      │                      └──sin respuesta──▶ ERROR_ENVIO ───────┤
 *      └────────────────── reintentar ◀───────────────────────────────┘
 * </pre>
 *
 * <h2>Todo del mismo día</h2>
 *
 * <p>Lo exige SUNAT: una comunicación cubre comprobantes emitidos en una sola
 * fecha, y su identificador —{@code RA-yyyyMMdd-N}— lleva esa fecha dentro. Dos
 * comunicaciones del mismo día se distinguen por el correlativo {@code N}.
 */
public class ComunicacionDeBaja {

    /**
     * Hasta el séptimo día calendario del mes siguiente al de emisión
     * (RS 097-2012/SUNAT). Pasado el plazo lo que corresponde es una nota de
     * crédito, y el mensaje lo dice en vez de dejar que SUNAT rechace el envío.
     */
    public static final int DIAS_DE_PLAZO = 7;

    /** Cuánto se espera al Emisor antes de admitir que la orden se perdió. */
    public static final Duration ESPERA_MAXIMA_EN_COLA = Duration.ofMinutes(10);

    private final UUID id;
    private final UUID empresaId;
    private final LocalDate fechaDeLosComprobantes;
    private final LocalDate fechaDeGeneracion;
    private final int numeroDelDia;
    private final List<Renglon> comprobantes;
    private final UUID solicitadaPor;
    private EstadoSunat estado;
    private int intentos;
    private Instant encoladaEn;
    private Instant respondidaEn;
    private String ticket;
    private String codigoSunat;
    private String descripcionSunat;
    private String claveXml;
    private String claveCdr;

    /**
     * @param documentoId el documento de venta que se da de baja
     * @param motivo por qué. Va en el XML y es lo que una persona lee después
     */
    public record Renglon(UUID documentoId, TipoDocumento tipo, String serie, long numero,
            String motivo) {

        public Renglon {
            Objects.requireNonNull(documentoId, "documentoId");
            Objects.requireNonNull(tipo, "tipo");
            if (motivo == null || motivo.isBlank()) {
                throw new ReglaDeNegocioViolada(
                        "motivo_requerido",
                        "Cada comprobante dado de baja necesita un motivo: es lo que SUNAT recibe "
                                + "y lo que se lee después.", "motivo");
            }
            motivo = motivo.trim();
        }

        public String numeroCompleto() {
            return serie + "-" + String.format("%08d", numero);
        }
    }

    private ComunicacionDeBaja(UUID id, UUID empresaId, LocalDate fechaDeLosComprobantes,
            LocalDate fechaDeGeneracion, int numeroDelDia, List<Renglon> comprobantes,
            UUID solicitadaPor, EstadoSunat estado, int intentos, Instant encoladaEn,
            Instant respondidaEn, String ticket, String codigoSunat, String descripcionSunat,
            String claveXml, String claveCdr) {
        this.id = id;
        this.empresaId = empresaId;
        this.fechaDeLosComprobantes = fechaDeLosComprobantes;
        this.fechaDeGeneracion = fechaDeGeneracion;
        this.numeroDelDia = numeroDelDia;
        this.comprobantes = List.copyOf(comprobantes);
        this.solicitadaPor = solicitadaPor;
        this.estado = estado;
        this.intentos = intentos;
        this.encoladaEn = encoladaEn;
        this.respondidaEn = respondidaEn;
        this.ticket = ticket;
        this.codigoSunat = codigoSunat;
        this.descripcionSunat = descripcionSunat;
        this.claveXml = claveXml;
        this.claveCdr = claveCdr;
    }

    /**
     * Arma la comunicación y comprueba lo que SUNAT exige del conjunto.
     *
     * @param numeroDelDia el correlativo dentro de {@code fechaDeGeneracion}
     * @param hoy la fecha con la que se mide el plazo
     */
    public static ComunicacionDeBaja crear(UUID id, UUID empresaId, List<Renglon> comprobantes,
            int numeroDelDia, LocalDate fechaDeLosComprobantes, LocalDate hoy, UUID solicitadaPor,
            Instant ahora) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(empresaId, "empresaId");
        if (comprobantes == null || comprobantes.isEmpty()) {
            throw new ReglaDeNegocioViolada(
                    "sin_comprobantes",
                    "Una comunicación de baja necesita al menos un comprobante.", "comprobantes");
        }
        for (Renglon renglon : comprobantes) {
            exigirDadoDeBajaPorEstaVia(renglon.tipo());
        }
        exigirDentroDelPlazo(fechaDeLosComprobantes, hoy);

        return new ComunicacionDeBaja(id, empresaId, fechaDeLosComprobantes, hoy, numeroDelDia,
                comprobantes, solicitadaPor, EstadoSunat.EN_COLA, 1, ahora, null, null, null, null,
                null, null);
    }

    /** Reconstrucción desde la persistencia: no valida. */
    public static ComunicacionDeBaja reconstruir(UUID id, UUID empresaId,
            LocalDate fechaDeLosComprobantes, LocalDate fechaDeGeneracion, int numeroDelDia,
            List<Renglon> comprobantes, UUID solicitadaPor, EstadoSunat estado, int intentos,
            Instant encoladaEn, Instant respondidaEn, String ticket, String codigoSunat,
            String descripcionSunat, String claveXml, String claveCdr) {
        return new ComunicacionDeBaja(id, empresaId, fechaDeLosComprobantes, fechaDeGeneracion,
                numeroDelDia, comprobantes, solicitadaPor, estado, intentos, encoladaEn,
                respondidaEn, ticket, codigoSunat, descripcionSunat, claveXml, claveCdr);
    }

    /**
     * Solo facturas y las notas asociadas a una factura.
     *
     * <p>Una boleta no se da de baja por esta vía: se anula con una nota de
     * crédito. Y una nota de venta no se declara, así que no hay nada que
     * comunicar (doc 13 §6).
     */
    private static void exigirDadoDeBajaPorEstaVia(TipoDocumento tipo) {
        if (tipo != TipoDocumento.FACTURA && tipo != TipoDocumento.NOTA_CREDITO
                && tipo != TipoDocumento.NOTA_DEBITO) {
            throw new ReglaDeNegocioViolada(
                    "tipo_no_dado_de_baja",
                    "La comunicación de baja es para facturas y sus notas. "
                            + tipo.nombre() + " se anula con una nota de crédito.", "comprobantes");
        }
    }

    /**
     * El plazo de SUNAT, comprobado aquí para que el mensaje diga qué hacer.
     * Dejarlo pasar produciría un rechazo con un código que hay que ir a buscar.
     */
    private static void exigirDentroDelPlazo(LocalDate fechaDeLosComprobantes, LocalDate hoy) {
        LocalDate limite = fechaDeLosComprobantes.plusMonths(1)
                .withDayOfMonth(DIAS_DE_PLAZO);
        if (hoy.isAfter(limite)) {
            throw new ReglaDeNegocioViolada(
                    "fuera_de_plazo",
                    "La comunicación de baja se admite hasta el " + DIAS_DE_PLAZO
                            + ".º día del mes siguiente al de emisión, y ese plazo venció el "
                            + limite + ". Anula con una nota de crédito.", "comprobantes");
        }
    }

    /** SUNAT recibió el archivo y dio un ticket; la respuesta llega al consultarlo. */
    public void anotarTicket(String ticket, String claveXml, Instant ahora) {
        if (estado != EstadoSunat.EN_COLA) {
            throw new IllegalStateException(
                    "La comunicación " + identificador() + " no estaba en cola.");
        }
        if (ticket == null || ticket.isBlank()) {
            throw new IllegalArgumentException("SUNAT no devolvió ticket.");
        }
        this.estado = EstadoSunat.EN_PROCESO;
        this.ticket = ticket.trim();
        this.claveXml = claveXml;
        this.respondidaEn = ahora;
    }

    /**
     * Lo que dijo la consulta del ticket, o el fallo del envío.
     *
     * @return {@code false} si el resultado ya no corresponde y se ignoró
     */
    public boolean resolver(EstadoSunat resultado, String codigo, String descripcion,
            String claveCdr, Instant ahora) {
        if (!estado.estaEnCurso()) {
            return false;
        }
        if (resultado == EstadoSunat.EN_COLA || resultado == EstadoSunat.EN_PROCESO) {
            throw new IllegalArgumentException(
                    "Una respuesta no puede dejar la comunicación en " + resultado);
        }
        this.estado = resultado;
        this.codigoSunat = codigo;
        this.descripcionSunat = descripcion;
        this.respondidaEn = ahora;
        if (claveCdr != null) {
            this.claveCdr = claveCdr;
        }
        return true;
    }

    /**
     * Vuelve a la cola. Desde un rechazo o un fallo de envío siempre; desde la
     * cola, solo si el Emisor lleva más de {@link #ESPERA_MAXIMA_EN_COLA} sin
     * responder. Desde {@code EN_PROCESO} no: ahí lo que toca es consultar el
     * ticket, no mandar otro archivo con el mismo número.
     */
    public void reintentar(Instant ahora) {
        if (estado == EstadoSunat.EN_PROCESO) {
            throw new ReglaDeNegocioViolada(
                    "consulta_pendiente",
                    "SUNAT ya recibió la comunicación " + identificador() + " y dio el ticket "
                            + ticket + ". Lo que falta es consultarlo, no volver a enviarla.");
        }
        if (estado == EstadoSunat.EN_COLA) {
            if (encoladaEn != null
                    && Duration.between(encoladaEn, ahora).compareTo(ESPERA_MAXIMA_EN_COLA) < 0) {
                throw new ReglaDeNegocioViolada(
                        "envio_en_curso",
                        "La comunicación está en cola desde hace menos de "
                                + ESPERA_MAXIMA_EN_COLA.toMinutes() + " minutos.");
            }
        } else if (!estado.admiteReintento()) {
            throw new ReglaDeNegocioViolada(
                    "reintento_no_admitido",
                    "Una comunicación " + estado.name().toLowerCase() + " no se vuelve a enviar.");
        }
        this.estado = EstadoSunat.EN_COLA;
        this.intentos++;
        this.encoladaEn = ahora;
        this.respondidaEn = null;
        this.ticket = null;
        this.claveCdr = null;
    }

    /** Cuánto lleva esperando respuesta, para que el planificador no consulte de más. */
    public boolean llevaEsperando(Duration minimo, Instant ahora) {
        Instant desde = respondidaEn == null ? encoladaEn : respondidaEn;
        return desde != null && Duration.between(desde, ahora).compareTo(minimo) >= 0;
    }

    public boolean fueAceptada() {
        return estado == EstadoSunat.ACEPTADO;
    }

    /** {@code RA-20260909-1}, que es como SUNAT la identifica. */
    public String identificador() {
        return "RA-" + fechaDeGeneracion.format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + numeroDelDia;
    }

    /** Los días que quedan de plazo a partir de una fecha; negativo si ya venció. */
    public long diasDePlazoDesde(LocalDate hoy) {
        return ChronoUnit.DAYS.between(hoy,
                fechaDeLosComprobantes.plusMonths(1).withDayOfMonth(DIAS_DE_PLAZO));
    }

    public UUID id() {
        return id;
    }

    public UUID empresaId() {
        return empresaId;
    }

    public LocalDate fechaDeLosComprobantes() {
        return fechaDeLosComprobantes;
    }

    public LocalDate fechaDeGeneracion() {
        return fechaDeGeneracion;
    }

    public int numeroDelDia() {
        return numeroDelDia;
    }

    public List<Renglon> comprobantes() {
        return comprobantes;
    }

    public UUID solicitadaPor() {
        return solicitadaPor;
    }

    public EstadoSunat estado() {
        return estado;
    }

    public int intentos() {
        return intentos;
    }

    public Instant encoladaEn() {
        return encoladaEn;
    }

    public Instant respondidaEn() {
        return respondidaEn;
    }

    public String ticket() {
        return ticket;
    }

    public String codigoSunat() {
        return codigoSunat;
    }

    public String descripcionSunat() {
        return descripcionSunat;
    }

    public String claveXml() {
        return claveXml;
    }

    public String claveCdr() {
        return claveCdr;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof ComunicacionDeBaja otra && id.equals(otra.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
