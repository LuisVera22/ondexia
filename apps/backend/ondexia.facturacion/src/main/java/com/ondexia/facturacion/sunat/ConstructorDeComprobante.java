package com.ondexia.facturacion.sunat;

import com.ondexia.domain.comprobante.OrdenDeEmision;
import io.github.project.openubl.xbuilder.content.catalogs.Catalog6;
import io.github.project.openubl.xbuilder.content.catalogs.Catalog7;
import io.github.project.openubl.xbuilder.content.catalogs.CatalogContadoCredito;
import io.github.project.openubl.xbuilder.content.models.common.Cliente;
import io.github.project.openubl.xbuilder.content.models.common.Direccion;
import io.github.project.openubl.xbuilder.content.models.common.Firmante;
import io.github.project.openubl.xbuilder.content.models.common.Proveedor;
import io.github.project.openubl.xbuilder.content.models.standard.general.CreditNote;
import io.github.project.openubl.xbuilder.content.models.standard.general.DocumentoVentaDetalle;
import io.github.project.openubl.xbuilder.content.models.standard.general.FormaDePago;
import io.github.project.openubl.xbuilder.content.models.standard.general.Invoice;
import io.github.project.openubl.xbuilder.content.models.standard.general.SalesDocument;
import io.github.project.openubl.xbuilder.content.models.standard.general.TotalImporte;
import io.github.project.openubl.xbuilder.enricher.ContentEnricher;
import io.github.project.openubl.xbuilder.enricher.config.Defaults;
import io.github.project.openubl.xbuilder.renderer.TemplateProducer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * De la orden al XML UBL 2.1 sin firmar (doc 14 §5).
 *
 * <p>Dos documentos con casi todo en común: la boleta y la factura son un
 * {@code Invoice}; la nota de crédito, un {@code CreditNote} que además dice
 * qué comprobante modifica y por qué (catálogo 09). Lo que comparten —emisor,
 * adquirente, líneas, impuestos, totales— se construye una sola vez.
 *
 * <h2>xbuilder construye la estructura; los importes son los de la orden</h2>
 *
 * <p>{@link ContentEnricher} rellena lo que la plantilla necesita —tipos de
 * afectación, categorías de impuesto, leyendas, la forma de pago— y de paso
 * recalcula importes con su propia aritmética. Ese recálculo se pisa después
 * con los importes que llegan en la orden, línea a línea y en los totales: la
 * aritmética del comprobante es la del dominio (doc 13 §4.3, desde el total con
 * IGV), y un céntimo distinto entre el XML y lo que el cliente pagó no es un
 * detalle en un documento con valor tributario. Ver {@link #imponerImportes}.
 *
 * <p>Sin cliente en la orden —boleta hasta S/ 700 a consumidor final— va el
 * tipo {@code 0} del catálogo 06 con número {@code -} y nombre genérico, que
 * es lo que SUNAT acepta para «sin documento».
 */
public class ConstructorDeComprobante {

    /** El «-» que SUNAT espera como número cuando el adquirente no se identifica. */
    static final String SIN_DOCUMENTO = "-";
    static final String CONSUMIDOR_FINAL = "CLIENTES VARIOS";
    /** Catálogo 16: 01 = precio unitario con IGV incluido. */
    private static final String PRECIO_CON_IGV = "01";

    private static final String NS_CAC =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String NS_CBC =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

    private final BigDecimal tasaIgv;

    public ConstructorDeComprobante(BigDecimal tasaIgv) {
        this.tasaIgv = tasaIgv;
    }

    public ConstructorDeComprobante() {
        this(new BigDecimal("0.18"));
    }

    /** El modelo ya enriquecido, con los importes de la orden. */
    public SalesDocument modelar(OrdenDeEmision orden) {
        return orden.documento().esNotaDeCredito() ? modelarNota(orden) : modelarFactura(orden);
    }

    /** El XML listo para firmar. */
    public String construir(OrdenDeEmision orden) {
        SalesDocument modelo = modelar(orden);
        if (modelo instanceof CreditNote) {
            return corregirMotivoDeLaNota(
                    TemplateProducer.getInstance().getCreditNote().data(modelo).render(),
                    orden.documento().motivoNota());
        }
        return TemplateProducer.getInstance().getInvoice().data(modelo).render();
    }

    /**
     * Pone el motivo del catálogo 09 donde SUNAT lo espera.
     *
     * <h2>Un defecto de la plantilla de xbuilder 5.1.1, y por qué se corrige aquí</h2>
     *
     * <p>El XML de una nota de crédito lleva el mismo par de datos en dos
     * sitios, con <strong>catálogos distintos</strong>:
     *
     * <pre>
     *   cac:DiscrepancyResponse/cbc:ResponseCode   → catálogo 09: por qué se emite la nota
     *   cac:BillingReference//cbc:DocumentTypeCode → catálogo 01: qué es el documento afectado
     * </pre>
     *
     * <p>La plantilla {@code note/invoice-reference.xml} de xbuilder rellena los
     * dos con el mismo campo del modelo, {@code comprobanteAfectadoTipo}, y no
     * usa {@code tipoNota} en ninguna parte —se comprobó buscándolo en las
     * nueve plantillas—. Es decir: <em>ningún</em> valor del modelo puede dejar
     * los dos correctos a la vez, así que no es cuestión de rellenarlo mejor.
     * Con el campo puesto al tipo del afectado, el {@code ResponseCode} sale
     * {@code 03} en vez de {@code 01}, y una nota que dice «el motivo es una
     * boleta» no significa nada.
     *
     * <p>Se corrige sobre el XML ya generado y <strong>antes de firmar</strong>,
     * que es la única ventana en que tocarlo no invalida nada. Las alternativas
     * eran peores: una plantilla propia obliga a mantener el UBL de SUNAT
     * entero, que es justo lo que xbuilder ahorra (doc 12 §5.2), y sobrescribir
     * el recurso del jar depende del orden del classpath, que puede cambiar sin
     * que nadie lo note.
     *
     * <p>Lo vigila {@code ConstructorDeComprobanteTest.notaDeCredito}, que fija
     * los dos valores. Si una versión posterior de xbuilder lo arregla, esa
     * prueba seguirá pasando y este método podrá retirarse.
     */
    static String corregirMotivoDeLaNota(String xml, String motivo) {
        try {
            var fabrica = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            fabrica.setNamespaceAware(true);
            org.w3c.dom.Document documento = fabrica.newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(
                            xml.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));
            var respuestas = documento.getElementsByTagNameNS(NS_CAC, "DiscrepancyResponse");
            for (int i = 0; i < respuestas.getLength(); i++) {
                var codigos = ((org.w3c.dom.Element) respuestas.item(i))
                        .getElementsByTagNameNS(NS_CBC, "ResponseCode");
                if (codigos.getLength() > 0) {
                    codigos.item(0).setTextContent(motivo);
                }
            }
            var transformador = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            transformador.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "ISO-8859-1");
            var salida = new java.io.StringWriter();
            transformador.transform(new javax.xml.transform.dom.DOMSource(documento),
                    new javax.xml.transform.stream.StreamResult(salida));
            return salida.toString();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo fijar el motivo de la nota de crédito en el XML: " + e.getMessage(), e);
        }
    }

    private Invoice modelarFactura(OrdenDeEmision orden) {
        OrdenDeEmision.Documento d = orden.documento();
        var invoice = Invoice.builder()
                .tipoComprobante(d.tipo())
                .serie(d.serie())
                .numero((int) d.numero())
                .fechaEmision(d.fechaEmision())
                .horaEmision(d.horaEmision())
                .moneda(d.moneda())
                .observaciones(d.observaciones())
                .proveedor(proveedor(orden.emisor()))
                .firmante(firmante(orden.emisor()))
                .cliente(cliente(d.adquirente()))
                .formaDePago(FormaDePago.builder()
                        .tipo(CatalogContadoCredito.CONTADO.getCode())
                        .total(d.total())
                        .build())
                .detalles(detalles(d))
                .build();

        enriquecedor(d).enrich(invoice);
        imponerImportes(invoice, invoice.getTotalImporte(), d);
        invoice.getFormaDePago().setTotal(dosDecimales(d.total()));
        return invoice;
    }

    /**
     * La nota de crédito.
     *
     * <p>Tres datos la distinguen y los tres son obligatorios para SUNAT: el
     * motivo del catálogo 09, el comprobante que modifica —tipo y número, sin
     * ceros a la izquierda— y un texto de sustento. Sin ellos el rechazo es
     * inmediato, y sin el comprobante afectado ni siquiera se sabría qué se
     * está corrigiendo.
     *
     * <p>El sustento sale de las observaciones si las hay, y si no del nombre
     * del motivo. Dejarlo vacío no es opción: es lo que una persona lee cuando
     * revisa por qué se anuló una venta.
     */
    private CreditNote modelarNota(OrdenDeEmision orden) {
        OrdenDeEmision.Documento d = orden.documento();
        OrdenDeEmision.Referencia referencia = d.referencia();
        if (referencia == null || d.motivoNota() == null) {
            throw new IllegalArgumentException(
                    "Una nota de crédito necesita el motivo y el comprobante que modifica.");
        }
        var nota = CreditNote.builder()
                .serie(d.serie())
                .numero((int) d.numero())
                .fechaEmision(d.fechaEmision())
                .horaEmision(d.horaEmision())
                .moneda(d.moneda())
                .tipoNota(d.motivoNota())
                .comprobanteAfectadoTipo(referencia.tipo())
                .comprobanteAfectadoSerieNumero(referencia.numeroCompleto())
                .sustentoDescripcion(d.observaciones() == null || d.observaciones().isBlank()
                        ? "Nota de crédito de " + referencia.numeroCompleto()
                        : d.observaciones())
                .proveedor(proveedor(orden.emisor()))
                .firmante(firmante(orden.emisor()))
                .cliente(cliente(d.adquirente()))
                .detalles(detalles(d))
                .build();

        enriquecedor(d).enrich(nota);
        imponerImportes(nota, nota.getTotalImporte(), d);
        return nota;
    }

    private ContentEnricher enriquecedor(OrdenDeEmision.Documento d) {
        return new ContentEnricher(Defaults.builder()
                .igvTasa(tasaIgv)
                .icbTasa(new BigDecimal("0.50"))
                .ivapTasa(new BigDecimal("0.04"))
                .build(), d::fechaEmision);
    }

    private static List<DocumentoVentaDetalle> detalles(OrdenDeEmision.Documento d) {
        var detalles = new ArrayList<DocumentoVentaDetalle>();
        for (OrdenDeEmision.Linea l : d.lineas()) {
            detalles.add(DocumentoVentaDetalle.builder()
                    .descripcion(l.descripcion())
                    .unidadMedida(l.unidad())
                    .cantidad(l.cantidad())
                    .precio(l.valorUnitario())
                    .igvTipo(afectacion(l.afectacion()).getCode())
                    .build());
        }
        return detalles;
    }

    /**
     * Los importes de la orden mandan sobre los del enriquecedor.
     *
     * <p>Por línea: base imponible (valor de venta), IGV, total de impuestos,
     * precio sin IGV y precio de referencia con IGV. En el documento: los
     * subtotales por afectación, el IGV total, y el importe con y sin impuestos.
     * Todo a dos decimales, que es lo que la plantilla imprime.
     */
    static void imponerImportes(SalesDocument documento, TotalImporte importe,
            OrdenDeEmision.Documento d) {
        var detalles = documento.getDetalles();
        for (int i = 0; i < detalles.size(); i++) {
            var detalle = detalles.get(i);
            var linea = d.lineas().get(i);
            boolean gravada = "10".equals(linea.afectacion());
            detalle.setIgvBaseImponible(dosDecimales(linea.valorVenta()));
            detalle.setIgv(gravada ? dosDecimales(linea.igv()) : cero());
            detalle.setTotalImpuestos(gravada ? dosDecimales(linea.igv()) : cero());
            detalle.setPrecio(linea.valorUnitario());
            detalle.setPrecioReferencia(linea.precioUnitario());
            detalle.setPrecioReferenciaTipo(PRECIO_CON_IGV);
            detalle.setTasaIgv(gravada ? detalle.getTasaIgv() : cero());
        }

        var impuestos = documento.getTotalImpuestos();
        impuestos.setTotal(dosDecimales(d.totalIgv()));
        impuestos.setGravadoBaseImponible(positivoONulo(d.totalGravado()));
        impuestos.setGravadoImporte(d.totalGravado().signum() > 0 ? dosDecimales(d.totalIgv()) : null);
        impuestos.setExoneradoBaseImponible(positivoONulo(d.totalExonerado()));
        impuestos.setExoneradoImporte(d.totalExonerado().signum() > 0 ? cero() : null);
        impuestos.setInafectoBaseImponible(positivoONulo(d.totalInafecto()));
        impuestos.setInafectoImporte(d.totalInafecto().signum() > 0 ? cero() : null);

        BigDecimal sinImpuestos = d.totalGravado().add(d.totalExonerado()).add(d.totalInafecto());
        importe.setImporteSinImpuestos(dosDecimales(sinImpuestos));
        importe.setImporteConImpuestos(dosDecimales(d.total()));
        importe.setImporte(dosDecimales(d.total()));
        // Los descuentos ya están dentro de cada línea —el total de la línea es
        // cantidad por precio menos descuento—, así que no se declaran aparte:
        // hacerlo los restaría dos veces.
        if (importe instanceof io.github.project.openubl.xbuilder.content.models.standard.general.TotalImporteInvoice factura) {
            factura.setDescuentos(null);
        }
    }

    private static Proveedor proveedor(OrdenDeEmision.Emisor e) {
        return Proveedor.builder()
                .ruc(e.ruc())
                .razonSocial(e.razonSocial())
                .nombreComercial(e.nombreComercial())
                .direccion(direccion(e))
                .build();
    }

    private static Firmante firmante(OrdenDeEmision.Emisor e) {
        return Firmante.builder().ruc(e.ruc()).razonSocial(e.razonSocial()).build();
    }

    private static Cliente cliente(OrdenDeEmision.Adquirente a) {
        if (a == null) {
            return Cliente.builder()
                    .tipoDocumentoIdentidad(Catalog6.DOC_TRIB_NO_DOM_SIN_RUC.getCode())
                    .numeroDocumentoIdentidad(SIN_DOCUMENTO)
                    .nombre(CONSUMIDOR_FINAL)
                    .build();
        }
        var cliente = Cliente.builder()
                .tipoDocumentoIdentidad(a.tipoDocumento())
                .numeroDocumentoIdentidad(a.numeroDocumento())
                .nombre(a.nombre());
        if (a.direccion() != null && !a.direccion().isBlank()) {
            cliente.direccion(Direccion.builder().direccion(a.direccion()).build());
        }
        return cliente.build();
    }

    private static Direccion direccion(OrdenDeEmision.Emisor e) {
        var direccion = Direccion.builder()
                .direccion(e.direccion())
                .codigoLocal(e.codigoEstablecimiento() == null ? "0000" : e.codigoEstablecimiento());
        if (e.ubigeo() != null && e.ubigeo().length() == 6) {
            direccion.ubigeo(e.ubigeo());
        }
        return direccion.build();
    }

    /** Catálogo 07 onerosa por afectación: 10, 20 o 30 (doc 13 §3). */
    static Catalog7 afectacion(String codigo) {
        return switch (codigo) {
            case "10" -> Catalog7.GRAVADO_OPERACION_ONEROSA;
            case "20" -> Catalog7.EXONERADO_OPERACION_ONEROSA;
            case "30" -> Catalog7.INAFECTO_OPERACION_ONEROSA;
            default -> throw new IllegalArgumentException(
                    "Afectación al IGV fuera del catálogo 07 admitido: " + codigo);
        };
    }

    private static BigDecimal dosDecimales(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal positivoONulo(BigDecimal valor) {
        return valor == null || valor.signum() <= 0 ? null : dosDecimales(valor);
    }

    private static BigDecimal cero() {
        return BigDecimal.ZERO.setScale(2);
    }

    /** Para la representación impresa: la leyenda en letras la pone xbuilder. */
    static String leyenda(SalesDocument documento) {
        Map<String, String> leyendas = documento.getLeyendas();
        return leyendas == null ? null : leyendas.get("1000");
    }
}
