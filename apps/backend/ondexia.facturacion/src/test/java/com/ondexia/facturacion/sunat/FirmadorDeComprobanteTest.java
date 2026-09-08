package com.ondexia.facturacion.sunat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.facturacion.Ordenes;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.UUID;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * La firma con el certificado de prueba ({@code certificado-prueba.pfx},
 * contraseña {@code prueba}, autofirmado con openssl) es una firma XML-DSig que
 * el propio JDK valida. Es la prueba que la regla 1 de CLAUDE.md pide para el
 * comentario de {@link FirmadorDeComprobante}.
 */
class FirmadorDeComprobanteTest {

    static byte[] pfxDePrueba() throws Exception {
        try (var entrada = FirmadorDeComprobanteTest.class.getResourceAsStream("/certificado-prueba.pfx")) {
            return entrada.readAllBytes();
        }
    }

    @Test
    @DisplayName("El certificado de prueba abre con su contraseña y se puede describir")
    void abrirYDescribir() throws Exception {
        var certificado = FirmadorDeComprobante.abrir(pfxDePrueba(), "prueba");
        var datos = FirmadorDeComprobante.describir(certificado);
        assertThat(datos.sujeto()).contains("CN=CERTIFICADO DE PRUEBA ONDEXIA").contains("OU=20100000009");
        assertThat(datos.venceEn()).isAfter(LocalDate.of(2030, 1, 1));
    }

    @Test
    @DisplayName("Con la contraseña equivocada el fallo lo dice, sin volcar nada más")
    void contrasenaIncorrecta() throws Exception {
        byte[] pfx = pfxDePrueba();
        assertThatThrownBy(() -> FirmadorDeComprobante.abrir(pfx, "otra"))
                .isInstanceOf(FirmadorDeComprobante.CertificadoNoAbre.class)
                .hasMessageContaining("contraseña");
        assertThatThrownBy(() -> FirmadorDeComprobante.abrir("no es un pfx".getBytes(), "prueba"))
                .isInstanceOf(FirmadorDeComprobante.CertificadoNoAbre.class);
    }

    @Test
    @DisplayName("El XML firmado lleva una firma válida dentro de UBLExtensions y un DigestValue")
    void firmaValida() throws Exception {
        String xml = new ConstructorDeComprobante().construir(Ordenes.boleta(UUID.randomUUID()));
        var certificado = FirmadorDeComprobante.abrir(pfxDePrueba(), "prueba");

        var firmado = new FirmadorDeComprobante().firmar(xml, certificado);

        assertThat(firmado.resumen()).isNotBlank();
        var fabrica = DocumentBuilderFactory.newInstance();
        fabrica.setNamespaceAware(true);
        Document doc = fabrica.newDocumentBuilder().parse(new ByteArrayInputStream(firmado.xml()));
        NodeList firmas = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        assertThat(firmas.getLength()).isEqualTo(1);
        assertThat(firmas.item(0).getParentNode().getLocalName()).isEqualTo("ExtensionContent");
        assertThat(firmas.item(0).getAttributes().getNamedItem("Id").getNodeValue())
                .isEqualTo(FirmadorDeComprobante.ID_FIRMA);

        var contexto = new DOMValidateContext(certificado.getX509Certificate().getPublicKey(), firmas.item(0));
        // SUNAT exige RSA-SHA1, que el JDK ya considera inseguro y rechaza al
        // validar con la comprobación estricta. Aquí se valida lo que SUNAT va
        // a validar, así que se relaja solo en la prueba.
        contexto.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
        XMLSignature firma = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(contexto);
        assertThat(firma.validate(contexto)).as("la firma valida con la clave pública del certificado").isTrue();
        assertThat(firma.getSignedInfo().getReferences().get(0).getDigestValue())
                .isEqualTo(java.util.Base64.getDecoder().decode(firmado.resumen()));
    }
}
