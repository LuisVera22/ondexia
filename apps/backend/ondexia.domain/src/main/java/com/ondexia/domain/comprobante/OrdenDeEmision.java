package com.ondexia.domain.comprobante;

import com.ondexia.domain.identidad.ModoSunat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Lo que la API deja en el bus para que el Emisor lo convierta en XML, lo
 * firme y lo envíe (doc 14 §2).
 *
 * <h2>Va todo, y va copiado</h2>
 *
 * <p>El Emisor no tiene base de datos —está fuera de la VPC a propósito—, así que
 * la orden lleva cada dato que el XML necesita: emisor, adquirente, líneas y
 * totales tal como los calculó el dominio. Y los lleva <strong>ya calculados</strong>:
 * el Emisor no vuelve a sumar nada. Si sumara, dos implementaciones de la misma
 * aritmética acabarían discrepando en un céntimo, y ese céntimo iría en el XML y
 * no en lo que el cliente pagó.
 *
 * <p>Los códigos son los de los catálogos de SUNAT ({@code 01}, {@code 03};
 * {@code 10}/{@code 20}/{@code 30}; {@code 1}/{@code 6}), no los nombres de las
 * constantes de Java: es lo que viaja en el XML.
 *
 * <p>Lo que <em>no</em> va: la clave SOL ni la contraseña del certificado. El
 * Emisor las lee por su cuenta con la convención de nombres de
 * {@code certificados/<ruc>.pfx} y {@code credenciales/<ruc>.json} (doc 14 §4).
 *
 * @param id para una emisión, el identificador del comprobante electrónico;
 *           para una verificación, uno nuevo
 */
public record OrdenDeEmision(
        UUID id,
        Operacion operacion,
        UUID empresaId,
        ModoSunat modo,
        Emisor emisor,
        Documento documento,
        Baja baja,
        Consulta consulta,
        Instant creadaEn) {

    /** Emitir un comprobante: la operación de siempre. */
    public static OrdenDeEmision paraEmitir(UUID id, UUID empresaId, ModoSunat modo, Emisor emisor,
            Documento documento, Instant creadaEn) {
        return new OrdenDeEmision(id, Operacion.EMITIR, empresaId, modo, emisor, documento, null,
                null, creadaEn);
    }

    public static OrdenDeEmision paraBaja(UUID id, UUID empresaId, ModoSunat modo, Emisor emisor,
            Baja baja, Instant creadaEn) {
        return new OrdenDeEmision(id, Operacion.ENVIAR_BAJA, empresaId, modo, emisor, null, baja,
                null, creadaEn);
    }

    public static OrdenDeEmision paraConsultarTicket(UUID id, UUID empresaId, ModoSunat modo,
            Emisor emisor, String ticket, Instant creadaEn) {
        return new OrdenDeEmision(id, Operacion.CONSULTAR_TICKET, empresaId, modo, emisor, null,
                null, new Consulta(ticket), creadaEn);
    }

    public static OrdenDeEmision paraVerificarCredenciales(UUID id, UUID empresaId, ModoSunat modo,
            Emisor emisor, Instant creadaEn) {
        return new OrdenDeEmision(id, Operacion.VERIFICAR_CREDENCIALES, empresaId, modo, emisor,
                null, null, null, creadaEn);
    }

    public enum Operacion {
        /** Construir, firmar y enviar un comprobante. Síncrono: vuelve con el CDR. */
        EMITIR,
        /**
         * La comunicación de baja. <strong>Asíncrona</strong>: SUNAT recibe el
         * archivo y devuelve un ticket, y la respuesta se pide después con
         * {@link #CONSULTAR_TICKET}.
         */
        ENVIAR_BAJA,
        /** Preguntar por un ticket que SUNAT dio antes. */
        CONSULTAR_TICKET,
        /** Abrir el certificado con su contraseña y decir qué hay dentro. Sin SUNAT. */
        VERIFICAR_CREDENCIALES
    }

    /**
     * @param codigoEstablecimiento el código del establecimiento anexo ante
     *                              SUNAT: {@code 0000} para la matriz
     */
    public record Emisor(
            String ruc,
            String razonSocial,
            String nombreComercial,
            String direccion,
            String ubigeo,
            String codigoEstablecimiento,
            String usuarioSol) {
    }

    /**
     * El adquirente. {@code null} en la orden: consumidor final sin documento
     * (boleta hasta S/ 700), que el Emisor codifica como tipo {@code 0}.
     */
    public record Adquirente(
            String tipoDocumento,
            String numeroDocumento,
            String nombre,
            String direccion) {
    }

    /** @param afectacion código del catálogo 07: 10 gravado, 20 exonerado, 30 inafecto */
    public record Linea(
            int orden,
            String descripcion,
            String unidad,
            BigDecimal cantidad,
            BigDecimal precioUnitario,
            BigDecimal valorUnitario,
            BigDecimal descuento,
            String afectacion,
            BigDecimal valorVenta,
            BigDecimal igv,
            BigDecimal total) {
    }

    /**
     * El documento al que una nota de crédito se refiere. SUNAT lo exige dentro
     * del XML de la nota: sin él no sabe qué está corrigiendo.
     */
    public record Referencia(String tipo, String serie, long numero) {

        /** {@code B001-12}, que es como va en el XML: sin ceros a la izquierda. */
        public String numeroCompleto() {
            return serie + "-" + numero;
        }
    }

    /**
     * @param motivoNota código del catálogo 09; solo en una nota de crédito
     * @param referencia el documento que la nota modifica; solo en una nota de crédito
     */
    /** El ticket que SUNAT dio al recibir un envío asíncrono. */
    public record Consulta(String ticket) {
    }

    public record Documento(
            String tipo,
            String serie,
            long numero,
            LocalDate fechaEmision,
            LocalTime horaEmision,
            String moneda,
            Adquirente adquirente,
            List<Linea> lineas,
            BigDecimal totalGravado,
            BigDecimal totalExonerado,
            BigDecimal totalInafecto,
            BigDecimal totalDescuento,
            BigDecimal totalIgv,
            BigDecimal total,
            String observaciones,
            String motivoNota,
            Referencia referencia) {

        public Documento {
            lineas = lineas == null ? List.of() : List.copyOf(lineas);
        }

        /** {@code 20100000009-03-B001-00000012}: el nombre que SUNAT espera para el archivo. */
        public String nombreDeArchivo(String ruc) {
            return ruc + "-" + tipo + "-" + serie + "-" + String.format("%08d", numero);
        }

        /** Si el XML que hay que construir es el de una nota de crédito (07). */
        public boolean esNotaDeCredito() {
            return "07".equals(tipo);
        }
    }

    public boolean esEmision() {
        return operacion == Operacion.EMITIR;
    }

    /**
     * La comunicación de baja (doc 13 §6): qué comprobantes se dan de baja y por
     * qué.
     *
     * @param numeroDelDia el correlativo de la comunicación dentro del día. El
     *                     identificador ante SUNAT es {@code RA-yyyyMMdd-N}, así
     *                     que dos comunicaciones del mismo día con el mismo
     *                     número serían el mismo documento
     * @param fechaDeLosComprobantes el día en que se emitió lo que se da de baja.
     *                     Todos tienen que ser del mismo, lo exige SUNAT
     */
    public record Baja(
            int numeroDelDia,
            LocalDate fechaDeLosComprobantes,
            LocalDate fechaDeGeneracion,
            List<ComprobanteDadoDeBaja> comprobantes) {

        public Baja {
            comprobantes = comprobantes == null ? List.of() : List.copyOf(comprobantes);
        }

        /** {@code RA-20260909-1}: el nombre con el que SUNAT lo identifica. */
        public String identificador() {
            return "RA-" + fechaDeGeneracion.format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + numeroDelDia;
        }

        public String nombreDeArchivo(String ruc) {
            return ruc + "-" + identificador();
        }
    }

    public record ComprobanteDadoDeBaja(String tipo, String serie, long numero, String motivo) {
    }
}
