package com.ondexia.facturacion.sunat;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondexia.facturacion.Ordenes;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/**
 * El XML que sale lleva los datos de la orden y, sobre todo, SUS importes: los
 * del dominio (doc 13 §4.3), no los que xbuilder recalcula.
 */
class ConstructorDeComprobanteTest {

    private final ConstructorDeComprobante constructor = new ConstructorDeComprobante();

    private static Document analizar(String xml) throws Exception {
        var fabrica = DocumentBuilderFactory.newInstance();
        fabrica.setNamespaceAware(true);
        return fabrica.newDocumentBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.ISO_8859_1)));
    }

    /** Los prefijos de UBL, para que las rutas se lean como el XML. */
    private static final Map<String, String> PREFIJOS = Map.of(
            "inv", "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
            "cbc", "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
            "cac", "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
            "nota", "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2");

    private static String valor(Document doc, String ruta) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefijo) {
                return PREFIJOS.getOrDefault(prefijo, XMLConstants.NULL_NS_URI);
            }

            @Override
            public String getPrefix(String uri) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String uri) {
                return null;
            }
        });
        return (String) xpath.evaluate(ruta, doc, XPathConstants.STRING);
    }

    @Test
    @DisplayName("La boleta lleva emisor, adquirente, líneas y los totales de la orden")
    void boleta() throws Exception {
        String xml = constructor.construir(Ordenes.boleta(UUID.randomUUID()));
        Document doc = analizar(xml);

        assertThat(valor(doc, "/inv:Invoice/cbc:ID")).isEqualTo("B001-12");
        assertThat(valor(doc, "/inv:Invoice/cbc:IssueDate")).isEqualTo("2026-09-08");
        assertThat(valor(doc, "/inv:Invoice/cbc:IssueTime")).isEqualTo("10:15:00");
        assertThat(valor(doc, "/inv:Invoice/cbc:InvoiceTypeCode")).isEqualTo("03");
        assertThat(valor(doc, "/inv:Invoice/cbc:DocumentCurrencyCode")).isEqualTo("PEN");
        assertThat(valor(doc, "//cac:AccountingSupplierParty//cbc:ID[@schemeID='6']")).isEqualTo("20100000009");
        assertThat(valor(doc, "//cac:AccountingSupplierParty//cbc:RegistrationName")).isEqualTo("COMERCIAL DEMO S.A.C.");
        assertThat(valor(doc, "//cac:AccountingSupplierParty//cac:RegistrationAddress/cbc:AddressTypeCode")).isEqualTo("0000");
        assertThat(valor(doc, "//cac:AccountingCustomerParty//cbc:ID")).isEqualTo("70123456");
        assertThat(valor(doc, "//cac:AccountingCustomerParty//cbc:ID/@schemeID")).isEqualTo("1");
        assertThat(valor(doc, "//cac:AccountingCustomerParty//cbc:RegistrationName")).isEqualTo("Juan Perez Gomez");
        // La firma se enlaza al identificador que XMLSigner va a poner.
        assertThat(valor(doc, "//cac:Signature//cbc:URI")).isEqualTo("#PROJECT-OPENUBL-SIGN");

        // Los totales son los de la orden, a dos decimales.
        assertThat(valor(doc, "/inv:Invoice/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("22.12");
        assertThat(valor(doc, "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal/cbc:TaxableAmount")).isEqualTo("122.88");
        assertThat(valor(doc, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:LineExtensionAmount")).isEqualTo("122.88");
        assertThat(valor(doc, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:TaxInclusiveAmount")).isEqualTo("145.00");
        assertThat(valor(doc, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:PayableAmount")).isEqualTo("145.00");

        // Y las líneas también: la primera son dos bolsas a 32.50 con IGV.
        assertThat(valor(doc, "count(//cac:InvoiceLine)")).isEqualTo("2");
        assertThat(valor(doc, "//cac:InvoiceLine[1]/cbc:InvoicedQuantity")).isEqualTo("2");
        assertThat(valor(doc, "//cac:InvoiceLine[1]/cbc:InvoicedQuantity/@unitCode")).isEqualTo("BG");
        assertThat(valor(doc, "//cac:InvoiceLine[1]/cbc:LineExtensionAmount")).isEqualTo("55.08");
        assertThat(valor(doc, "//cac:InvoiceLine[1]/cac:PricingReference//cbc:PriceAmount")).isEqualTo("32.50");
        assertThat(valor(doc, "//cac:InvoiceLine[1]/cac:PricingReference//cbc:PriceTypeCode")).isEqualTo("01");
        assertThat(valor(doc, "//cac:InvoiceLine[1]/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("9.92");
        assertThat(valor(doc, "//cac:InvoiceLine[1]//cbc:TaxExemptionReasonCode")).isEqualTo("10");
        assertThat(valor(doc, "//cac:InvoiceLine[1]/cac:Item/cbc:Description")).isEqualTo("Cemento Portland Tipo I 42.5 kg");
        assertThat(valor(doc, "//cac:InvoiceLine[1]/cac:Price/cbc:PriceAmount")).isEqualTo("27.54");
        assertThat(valor(doc, "//cac:InvoiceLine[2]/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("12.20");

        // La forma de pago al contado, obligatoria desde 2021, y la observación.
        assertThat(valor(doc, "//cac:PaymentTerms/cbc:PaymentMeansID")).isEqualTo("Contado");
        assertThat(xml).contains("Entrega mañana");
    }

    @Test
    @DisplayName("Una factura exonerada declara la base exonerada y ningún IGV")
    void facturaExonerada() throws Exception {
        Document doc = analizar(constructor.construir(Ordenes.facturaExonerada(UUID.randomUUID())));

        assertThat(valor(doc, "/inv:Invoice/cbc:ID")).isEqualTo("F001-7");
        assertThat(valor(doc, "/inv:Invoice/cbc:InvoiceTypeCode")).isEqualTo("01");
        assertThat(valor(doc, "//cac:AccountingCustomerParty//cbc:ID/@schemeID")).isEqualTo("6");
        assertThat(valor(doc, "/inv:Invoice/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("0.00");
        assertThat(valor(doc, "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal/cbc:TaxableAmount")).isEqualTo("50.00");
        assertThat(valor(doc, "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal//cac:TaxScheme/cbc:ID")).isEqualTo("9997");
        assertThat(valor(doc, "//cac:InvoiceLine[1]//cbc:TaxExemptionReasonCode")).isEqualTo("20");
        assertThat(valor(doc, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:PayableAmount")).isEqualTo("50.00");
    }

    @Test
    @DisplayName("La nota de crédito dice qué comprobante modifica, con qué motivo y con qué sustento")
    void notaDeCredito() throws Exception {
        String xml = constructor.construir(Ordenes.notaDeCredito(UUID.randomUUID()));
        Document doc = analizar(xml);

        assertThat(valor(doc, "/nota:CreditNote/cbc:ID")).isEqualTo("BC01-3");
        // El motivo del catálogo 09 y el comprobante afectado, que es lo que
        // distingue una nota de crédito de todo lo demás.
        assertThat(valor(doc, "//cac:DiscrepancyResponse/cbc:ResponseCode")).isEqualTo("01");
        assertThat(valor(doc, "//cac:DiscrepancyResponse/cbc:ReferenceID")).isEqualTo("B001-12");
        assertThat(valor(doc, "//cac:DiscrepancyResponse/cbc:Description"))
                .isEqualTo("Cliente devolvió la mercadería");
        assertThat(valor(doc, "//cac:BillingReference//cbc:ID")).isEqualTo("B001-12");
        assertThat(valor(doc, "//cac:BillingReference//cbc:DocumentTypeCode")).isEqualTo("03");

        // Y los importes son los de la orden, igual que en la boleta.
        assertThat(valor(doc, "//cac:CreditNoteLine[1]/cbc:CreditedQuantity")).isEqualTo("2");
        assertThat(valor(doc, "/nota:CreditNote/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("22.12");
        assertThat(valor(doc, "/nota:CreditNote/cac:LegalMonetaryTotal/cbc:PayableAmount"))
                .isEqualTo("145.00");
        assertThat(valor(doc, "//cac:AccountingCustomerParty//cbc:ID")).isEqualTo("70123456");
    }

    @Test
    @DisplayName("Sin adquirente va el tipo 0 con «-» y consumidor final")
    void boletaSinCliente() throws Exception {
        Document doc = analizar(constructor.construir(Ordenes.boletaSinCliente(UUID.randomUUID())));

        assertThat(valor(doc, "//cac:AccountingCustomerParty//cbc:ID/@schemeID")).isEqualTo("0");
        assertThat(valor(doc, "//cac:AccountingCustomerParty//cbc:ID")).isEqualTo("-");
        assertThat(valor(doc, "//cac:AccountingCustomerParty//cbc:RegistrationName")).isEqualTo("CLIENTES VARIOS");
    }
}
