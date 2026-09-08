package com.ondexia.facturacion.sunat;

import com.ondexia.domain.comprobante.OrdenDeEmision;
import io.github.project.openubl.xbuilder.content.catalogs.Catalog6;
import io.github.project.openubl.xbuilder.content.catalogs.Catalog7;
import io.github.project.openubl.xbuilder.content.catalogs.CatalogContadoCredito;
import io.github.project.openubl.xbuilder.content.models.common.Cliente;
import io.github.project.openubl.xbuilder.content.models.common.Direccion;
import io.github.project.openubl.xbuilder.content.models.common.Firmante;
import io.github.project.openubl.xbuilder.content.models.common.Proveedor;
import io.github.project.openubl.xbuilder.content.models.standard.general.DocumentoVentaDetalle;
import io.github.project.openubl.xbuilder.content.models.standard.general.FormaDePago;
import io.github.project.openubl.xbuilder.content.models.standard.general.Invoice;
import io.github.project.openubl.xbuilder.enricher.ContentEnricher;
import io.github.project.openubl.xbuilder.enricher.config.Defaults;
import io.github.project.openubl.xbuilder.renderer.TemplateProducer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Map;

/**
 * De la orden al XML UBL 2.1 sin firmar (doc 14 §5).
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

    private final BigDecimal tasaIgv;

    public ConstructorDeComprobante(BigDecimal tasaIgv) {
        this.tasaIgv = tasaIgv;
    }

    public ConstructorDeComprobante() {
        this(new BigDecimal("0.18"));
    }

    /** El modelo ya enriquecido, con los importes de la orden. */
    public Invoice modelar(OrdenDeEmision orden) {
        OrdenDeEmision.Documento d = orden.documento();
        OrdenDeEmision.Emisor e = orden.emisor();

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

        var invoice = Invoice.builder()
                .tipoComprobante(d.tipo())
                .serie(d.serie())
                .numero((int) d.numero())
                .fechaEmision(d.fechaEmision())
                .horaEmision(d.horaEmision())
                .moneda(d.moneda())
                .observaciones(d.observaciones())
                .proveedor(Proveedor.builder()
                        .ruc(e.ruc())
                        .razonSocial(e.razonSocial())
                        .nombreComercial(e.nombreComercial())
                        .direccion(direccion(e))
                        .build())
                .firmante(Firmante.builder().ruc(e.ruc()).razonSocial(e.razonSocial()).build())
                .cliente(cliente(d.adquirente()))
                .formaDePago(FormaDePago.builder()
                        .tipo(CatalogContadoCredito.CONTADO.getCode())
                        .total(d.total())
                        .build())
                .detalles(detalles)
                .build();

        new ContentEnricher(Defaults.builder()
                .igvTasa(tasaIgv)
                .icbTasa(new BigDecimal("0.50"))
                .ivapTasa(new BigDecimal("0.04"))
                .build(), d::fechaEmision).enrich(invoice);

        imponerImportes(invoice, d);
        return invoice;
    }

    /** El XML listo para firmar. */
    public String construir(OrdenDeEmision orden) {
        return TemplateProducer.getInstance().getInvoice().data(modelar(orden)).render();
    }

    /**
     * Los importes de la orden mandan sobre los del enriquecedor.
     *
     * <p>Por línea: base imponible (valor de venta), IGV, total de impuestos,
     * precio sin IGV y precio de referencia con IGV. En el documento: los
     * subtotales por afectación, el IGV total, y el importe con y sin impuestos.
     * Todo a dos decimales, que es lo que la plantilla imprime.
     */
    static void imponerImportes(Invoice invoice, OrdenDeEmision.Documento d) {
        var detalles = invoice.getDetalles();
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

        var impuestos = invoice.getTotalImpuestos();
        impuestos.setTotal(dosDecimales(d.totalIgv()));
        impuestos.setGravadoBaseImponible(positivoONulo(d.totalGravado()));
        impuestos.setGravadoImporte(d.totalGravado().signum() > 0 ? dosDecimales(d.totalIgv()) : null);
        impuestos.setExoneradoBaseImponible(positivoONulo(d.totalExonerado()));
        impuestos.setExoneradoImporte(d.totalExonerado().signum() > 0 ? cero() : null);
        impuestos.setInafectoBaseImponible(positivoONulo(d.totalInafecto()));
        impuestos.setInafectoImporte(d.totalInafecto().signum() > 0 ? cero() : null);

        var importe = invoice.getTotalImporte();
        BigDecimal sinImpuestos = d.totalGravado().add(d.totalExonerado()).add(d.totalInafecto());
        importe.setImporteSinImpuestos(dosDecimales(sinImpuestos));
        importe.setImporteConImpuestos(dosDecimales(d.total()));
        importe.setImporte(dosDecimales(d.total()));
        if (d.totalDescuento() != null && d.totalDescuento().signum() > 0) {
            // Los descuentos ya están dentro de cada línea (el total de la
            // línea es cantidad por precio menos descuento), así que no se
            // declaran aparte: hacerlo los restaría dos veces.
            importe.setDescuentos(null);
        }
        invoice.getFormaDePago().setTotal(dosDecimales(d.total()));
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
    static String leyenda(Invoice invoice) {
        Map<String, String> leyendas = invoice.getLeyendas();
        return leyendas == null ? null : leyendas.get("1000");
    }
}
