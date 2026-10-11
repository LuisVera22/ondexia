package com.ondexia.facturacion.recuperacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondexia.facturacion.CertificadoDePrueba;
import com.ondexia.facturacion.Ordenes;
import com.ondexia.facturacion.sunat.ConstructorDeComprobante;
import com.ondexia.facturacion.sunat.FirmadorDeComprobante;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.jupiter.api.io.TempDir;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificadorDeOriginalXmlTest {
    private final VerificadorDeOriginalXml verificador = new VerificadorDeOriginalXml();
    private final io.github.project.openubl.xbuilder.signature.CertificateDetails certificado =
            FirmadorDeComprobante.abrir(CertificadoDePrueba.bytes(), CertificadoDePrueba.CLAVE);

    private byte[] firmado() {
        return new FirmadorDeComprobante().firmar(new ConstructorDeComprobante()
                .construir(Ordenes.boleta(UUID.randomUUID())), certificado).xml();
    }

    private VerificadorDeOriginalXml.Resultado verificar(byte[] xml) {
        return verificador.verificar(xml, certificado.getX509Certificate(), "20100000009", "03", "B001-12", true);
    }

    private byte[] firmadoConSha256(boolean fragmento) throws Exception {
        var analizador = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        analizador.setNamespaceAware(true);
        var documento = analizador.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(
                new ConstructorDeComprobante().construir(Ordenes.boleta(UUID.randomUUID()))
                        .getBytes(StandardCharsets.ISO_8859_1)));
        var fabrica = javax.xml.crypto.dsig.XMLSignatureFactory.getInstance("DOM");
        var transformaciones = List.of(fabrica.newTransform(javax.xml.crypto.dsig.Transform.ENVELOPED,
                (javax.xml.crypto.dsig.spec.TransformParameterSpec) null));
        var referencia = fabrica.newReference(fragmento ? "#fragmento" : "",
                fabrica.newDigestMethod(javax.xml.crypto.dsig.DigestMethod.SHA256, null), transformaciones, null, null);
        var datos = fabrica.newSignedInfo(fabrica.newCanonicalizationMethod(
                javax.xml.crypto.dsig.CanonicalizationMethod.INCLUSIVE,
                (javax.xml.crypto.dsig.spec.C14NMethodParameterSpec) null),
                fabrica.newSignatureMethod(javax.xml.crypto.dsig.SignatureMethod.RSA_SHA256, null), List.of(referencia));
        var claves = fabrica.getKeyInfoFactory();
        var informacion = claves.newKeyInfo(List.of(claves.newX509Data(List.of(certificado.getX509Certificate()))));
        var contenido = documento.getElementsByTagNameNS(
                "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2", "ExtensionContent").item(0);
        var contexto = new javax.xml.crypto.dsig.dom.DOMSignContext(certificado.getPrivateKey(), contenido);
        if (fragmento) {
            var elemento = (org.w3c.dom.Element) documento.getElementsByTagNameNS(
                    "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2", "ID").item(0);
            elemento.setAttribute("Id", "fragmento");
            contexto.setIdAttributeNS(elemento, null, "Id");
        }
        fabrica.newXMLSignature(datos, informacion).sign(contexto);
        var transformador = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
        transformador.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "ISO-8859-1");
        var salida = new java.io.ByteArrayOutputStream();
        transformador.transform(new javax.xml.transform.dom.DOMSource(documento),
                new javax.xml.transform.stream.StreamResult(salida));
        return salida.toByteArray();
    }

    @Test
    void sha256ValidaSinCompatibilidadLegada() throws Exception {
        var resultado = verificador.verificar(firmadoConSha256(false), certificado.getX509Certificate(),
                "20100000009", "03", "B001-12", false);
        assertThat(resultado.codigo()).isEqualTo("INTEGRIDAD_VERIFICADA");
        assertThat(resultado.integridadVerificada()).isTrue();
        assertThat(resultado.confianzaVerificada()).isFalse();
        assertThat(resultado.aceptacionSunatVerificada()).isFalse();
    }

    @Test
    void entradaVaciaOExcesivaNoSeProcesa() {
        for (byte[] xml : new byte[][] {new byte[0], new byte[VerificadorDeOriginalXml.LIMITE_XML + 1]}) {
            assertThat(verificar(xml).codigo()).isEqualTo("ENTRADA_NO_ADMITIDA");
        }
    }

    @Test
    void facturaYNotaDeCreditoConservanSuIdentidad() {
        var constructor = new ConstructorDeComprobante();
        var firmador = new FirmadorDeComprobante();
        byte[] factura = firmador.firmar(constructor.construir(Ordenes.facturaExonerada(UUID.randomUUID())), certificado).xml();
        byte[] nota = firmador.firmar(constructor.construir(Ordenes.notaDeCredito(UUID.randomUUID())), certificado).xml();
        assertThat(verificador.verificar(factura, certificado.getX509Certificate(), "20100000009", "01", "F001-7", true)
                .integridadVerificada()).isTrue();
        assertThat(verificador.verificar(nota, certificado.getX509Certificate(), "20100000009", "07", "BC01-3", true)
                .integridadVerificada()).isTrue();
    }

    @Test
    void dtdInclusoSinEntidadesSeRechaza() {
        String xml = new String(firmado(), StandardCharsets.ISO_8859_1);
        int finDeclaracion = xml.indexOf("?>") + 2;
        assertThat(finDeclaracion).isGreaterThan(2);
        String conDtd = xml.substring(0, finDeclaracion) + "<!DOCTYPE Invoice>" + xml.substring(finDeclaracion);
        assertThat(verificar(conDtd.getBytes(StandardCharsets.ISO_8859_1)).codigo()).isEqualTo("XML_NO_ADMITIDO");
    }

    @Test
    void originalIntegroNoAcreditaConfianzaNiAceptacion() {
        byte[] xml = firmado();
        byte[] antes = xml.clone();
        var resultado = verificar(xml);
        assertThat(resultado.codigo()).isEqualTo("INTEGRIDAD_SHA1_LEGADO");
        assertThat(resultado.integridadVerificada()).isTrue();
        assertThat(resultado.confianzaVerificada()).isFalse();
        assertThat(resultado.aceptacionSunatVerificada()).isFalse();
        assertThat(xml).isEqualTo(antes);
    }

    @Test
    void rechazaSha1SiNoSeAutorizaCompatibilidad() {
        var resultado = verificador.verificar(firmado(), certificado.getX509Certificate(),
                "20100000009", "03", "B001-12", false);
        assertThat(resultado.codigo()).isEqualTo("PERFIL_NO_ADMITIDO");
        assertThat(resultado.integridadVerificada()).isFalse();
    }

    @Test
    void alteracionDeContenidoInvalidaFirma() {
        byte[] alterado = new String(firmado(), StandardCharsets.ISO_8859_1)
                .replace("Juan Perez Gomez", "Nombre alterado").getBytes(StandardCharsets.ISO_8859_1);
        assertThat(verificar(alterado).codigo()).isEqualTo("FIRMA_INVALIDA");
    }

    @Test
    void identidadEsperadaSeContrastaConValoresIndependientes() {
        byte[] xml = firmado();
        for (String[] esperado : new String[][] {
                {"20100000010", "03", "B001-12"}, {"20100000009", "01", "B001-12"},
                {"20100000009", "03", "B001-13"}}) {
            var resultado = verificador.verificar(xml, certificado.getX509Certificate(),
                    esperado[0], esperado[1], esperado[2], true);
            assertThat(resultado.codigo()).isEqualTo("IDENTIDAD_NO_COINCIDE");
            assertThat(resultado.integridadVerificada()).isFalse();
        }
    }

    @Test
    void certificadoIndependienteEsObligatorio() {
        assertThat(verificador.verificar(firmado(), null, "20100000009", "03", "B001-12", true)
                .codigo()).isEqualTo("ENTRADA_NO_ADMITIDA");
    }

    @Test
    void referenciaExternaSeRechazaAntesDeValidar() {
        byte[] externo = new String(firmado(), StandardCharsets.ISO_8859_1)
                .replace("URI=\"\"", "URI=\"https://no-consultar.invalid/original.xml\"")
                .getBytes(StandardCharsets.ISO_8859_1);
        assertThat(verificar(externo).codigo()).isEqualTo("PERFIL_NO_ADMITIDO");
    }

    @Test
    void certificadoDistintoSeRechaza(@TempDir Path temporal) throws Exception {
        Path destino = temporal.resolve("otro.pfx");
        var proceso = new ProcessBuilder(List.of(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "otro", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
                "-dname", "CN=OTRA PRUEBA", "-storetype", "PKCS12", "-keystore", destino.toString(),
                "-storepass", "prueba", "-keypass", "prueba"))
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            assertThat(proceso.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally {
            if (proceso.isAlive()) { proceso.destroyForcibly(); }
        }
        assertThat(proceso.exitValue()).isZero();
        var otro = FirmadorDeComprobante.abrir(Files.readAllBytes(destino), "prueba");
        assertThat(verificador.verificar(firmado(), otro.getX509Certificate(), "20100000009", "03", "B001-12", true)
                .codigo()).isEqualTo("CERTIFICADO_NO_COINCIDE");
    }

    @Test
    void firmaDeFragmentoNoVerificaDocumentoCompleto() throws Exception {
        byte[] fragmento = firmadoConSha256(true);
        // La firma es válida sobre el fragmento: aun así no protege la venta completa.
        var analizador = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        analizador.setNamespaceAware(true);
        var documento = analizador.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(fragmento));
        var elemento = (org.w3c.dom.Element) documento.getElementsByTagNameNS(
                "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2", "ID").item(0);
        var contexto = new javax.xml.crypto.dsig.dom.DOMValidateContext(certificado.getX509Certificate().getPublicKey(),
                documento.getElementsByTagNameNS(javax.xml.crypto.dsig.XMLSignature.XMLNS, "Signature").item(0));
        contexto.setIdAttributeNS(elemento, null, "Id");
        assertThat(javax.xml.crypto.dsig.XMLSignatureFactory.getInstance("DOM")
                .unmarshalXMLSignature(contexto).validate(contexto)).isTrue();
        assertThat(verificar(fragmento).codigo()).isEqualTo("PERFIL_NO_ADMITIDO");
    }

    @Test
    void transformaXsltNoSeAdmite() {
        byte[] transformacion = new String(firmado(), StandardCharsets.ISO_8859_1)
                .replace("http://www.w3.org/2000/09/xmldsig#enveloped-signature", "http://www.w3.org/TR/1999/REC-xslt-19991116")
                .getBytes(StandardCharsets.ISO_8859_1);
        assertThat(verificar(transformacion).codigo()).isEqualTo("PERFIL_NO_ADMITIDO");
    }

    @Test
    void dosFirmasNoSeAdmiten() {
        String xml = new String(firmado(), StandardCharsets.ISO_8859_1);
        String duplicado = xml.replace("</ext:ExtensionContent>",
                "<ds:Signature xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\"/></ext:ExtensionContent>");
        assertThat(verificar(duplicado.getBytes(StandardCharsets.ISO_8859_1)).codigo()).isEqualTo("PERFIL_NO_ADMITIDO");
    }

    @Test
    void identidadDuplicadaNoSeAcepta() {
        String xml = new String(firmado(), StandardCharsets.ISO_8859_1).replace("<cbc:ID>B001-12</cbc:ID>",
                "<cbc:ID>B001-12</cbc:ID><cbc:ID>B001-13</cbc:ID>");
        assertThat(verificar(xml.getBytes(StandardCharsets.ISO_8859_1)).integridadVerificada()).isFalse();
    }

    @Test
    void dtdYEntidadExternaNoSeProcesan() {
        byte[] xml = "<!DOCTYPE Invoice [<!ENTITY externa SYSTEM 'file:///no-leer'>]><Invoice>&externa;</Invoice>"
                .getBytes(StandardCharsets.UTF_8);
        assertThat(verificar(xml).codigo()).isEqualTo("XML_NO_ADMITIDO");
        assertThat(verificar("no es XML".getBytes(StandardCharsets.UTF_8)).codigo()).isEqualTo("XML_NO_ADMITIDO");
    }
}
