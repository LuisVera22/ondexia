package com.ondexia.facturacion.recuperacion;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Verificación local, sin restauración ni afirmación de confianza tributaria. */
public final class VerificadorDeOriginalXml {
    private static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    public static final int LIMITE_XML = 8 * 1024 * 1024;

    public record Resultado(String codigo, boolean integridadVerificada,
            boolean confianzaVerificada, boolean aceptacionSunatVerificada) { }

    public Resultado verificar(byte[] original, X509Certificate certificadoEsperado,
            String rucEsperado, String tipoEsperado, String numeroEsperado, boolean permitirSha1Legado) {
        if (original == null || original.length == 0 || original.length > LIMITE_XML
                || certificadoEsperado == null || vacio(rucEsperado) || vacio(numeroEsperado)
                || !List.of("01", "03", "07").contains(tipoEsperado == null ? "" : tipoEsperado)) {
            return fallo("ENTRADA_NO_ADMITIDA");
        }
        Element raiz;
        try {
            var fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setNamespaceAware(true);
            // dtdYEntidadExternaNoSeProcesan: sin DTD, entidades ni acceso a recursos externos.
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            fabrica.setFeature("http://xml.org/sax/features/external-general-entities", false);
            fabrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            fabrica.setXIncludeAware(false);
            fabrica.setExpandEntityReferences(false);
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var analizador = fabrica.newDocumentBuilder();
            analizador.setEntityResolver((publico, sistema) -> { throw new SAXException("Recurso externo no admitido."); });
            analizador.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException error) throws SAXException { throw error; }
                @Override public void fatalError(SAXParseException error) throws SAXException { throw error; }
            });
            raiz = analizador.parse(new ByteArrayInputStream(original)).getDocumentElement();
        } catch (Exception noAdmitido) {
            return fallo("XML_NO_ADMITIDO");
        }
        try {
            String nombre = "07".equals(tipoEsperado) ? "CreditNote" : "Invoice";
            if (!nombre.equals(raiz.getLocalName()) || !("urn:oasis:names:specification:ubl:schema:xsd:" + nombre + "-2")
                    .equals(raiz.getNamespaceURI())) {
                return fallo("IDENTIDAD_NO_COINCIDE");
            }
            String tipo = "07".equals(tipoEsperado) ? "07" : hijo(raiz, CBC, "InvoiceTypeCode").getTextContent().trim();
            Element emisor = hijo(hijo(hijo(raiz, CAC, "AccountingSupplierParty"), CAC, "Party"), CAC, "PartyIdentification");
            if (!numeroEsperado.equals(hijo(raiz, CBC, "ID").getTextContent().trim())
                    || !tipoEsperado.equals(tipo)
                    || !rucEsperado.equals(hijo(emisor, CBC, "ID").getTextContent().trim())) {
                return fallo("IDENTIDAD_NO_COINCIDE");
            }
            var firmas = raiz.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
            if (firmas.getLength() != 1) { return fallo("PERFIL_NO_ADMITIDO"); }
            Element nodoFirma = (Element) firmas.item(0);
            Node contenido = nodoFirma.getParentNode();
            if (!es(contenido, EXT, "ExtensionContent") || !es(contenido.getParentNode(), EXT, "UBLExtension")
                    || !es(contenido.getParentNode().getParentNode(), EXT, "UBLExtensions")
                    || contenido.getParentNode().getParentNode().getParentNode() != raiz) {
                return fallo("PERFIL_NO_ADMITIDO");
            }
            var certificados = nodoFirma.getElementsByTagNameNS(XMLSignature.XMLNS, "X509Certificate");
            if (certificados.getLength() != 1 || !MessageDigest.isEqual(certificadoEsperado.getEncoded(),
                    Base64.getMimeDecoder().decode(certificados.item(0).getTextContent()))) {
                return fallo("CERTIFICADO_NO_COINCIDE");
            }
            if (!perfilDomAdmitido(nodoFirma, permitirSha1Legado)) { return fallo("PERFIL_NO_ADMITIDO"); }
            var contexto = new DOMValidateContext(certificadoEsperado.getPublicKey(), nodoFirma);
            // Compatibilidad local explícita. Perfil restringido antes de validar; nunca afecta al Emisor.
            // rechazaSha1SiNoSeAutorizaCompatibilidad y referenciaExternaSeRechazaAntesDeValidar.
            if (permitirSha1Legado) { contexto.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE); }
            var fabricaFirmas = XMLSignatureFactory.getInstance("DOM");
            XMLSignature firma = fabricaFirmas.unmarshalXMLSignature(contexto);
            var referencias = firma.getSignedInfo().getReferences();
            String algoritmo = firma.getSignedInfo().getSignatureMethod().getAlgorithm();
            boolean sha1 = SignatureMethod.RSA_SHA1.equals(algoritmo);
            if (!(SignatureMethod.RSA_SHA256.equals(algoritmo) || permitirSha1Legado && sha1)
                    || referencias.size() != 1 || !firma.getObjects().isEmpty()
                    || !List.of(CanonicalizationMethod.INCLUSIVE, CanonicalizationMethod.EXCLUSIVE)
                            .contains(firma.getSignedInfo().getCanonicalizationMethod().getAlgorithm())) {
                return fallo("PERFIL_NO_ADMITIDO");
            }
            Reference referencia = referencias.getFirst();
            if (!"".equals(referencia.getURI()) || !(sha1 ? DigestMethod.SHA1 : DigestMethod.SHA256)
                    .equals(referencia.getDigestMethod().getAlgorithm())) {
                return fallo("PERFIL_NO_ADMITIDO");
            }
            List<String> transformaciones = referencia.getTransforms().stream().map(Transform::getAlgorithm).toList();
            if (!List.of(List.of(Transform.ENVELOPED), List.of(Transform.ENVELOPED, CanonicalizationMethod.INCLUSIVE),
                    List.of(Transform.ENVELOPED, CanonicalizationMethod.EXCLUSIVE)).contains(transformaciones)) {
                return fallo("PERFIL_NO_ADMITIDO");
            }
            var resolutorLocal = fabricaFirmas.getURIDereferencer();
            contexto.setURIDereferencer((referenciaUri, contextoXml) -> {
                if (!"".equals(referenciaUri.getURI())) {
                    throw new javax.xml.crypto.URIReferenceException("Referencia externa no admitida.");
                }
                return resolutorLocal.dereference(referenciaUri, contextoXml);
            });
            if (!firma.validate(contexto)) { return fallo("FIRMA_INVALIDA"); }
            return new Resultado(sha1 ? "INTEGRIDAD_SHA1_LEGADO" : "INTEGRIDAD_VERIFICADA", true, false, false);
        } catch (javax.xml.crypto.MarshalException perfil) {
            return fallo("PERFIL_NO_ADMITIDO");
        } catch (Exception noAdmitido) {
            return fallo("XML_NO_ADMITIDO");
        }
    }

    /** Perfil cerrado antes de construir servicios de transformaciones; transformaXsltNoSeAdmite. */
    private static boolean perfilDomAdmitido(Element firma, boolean permitirSha1) {
        Element datos = hijo(firma, XMLSignature.XMLNS, "SignedInfo");
        String algoritmo = hijo(datos, XMLSignature.XMLNS, "SignatureMethod").getAttribute("Algorithm");
        boolean sha1 = SignatureMethod.RSA_SHA1.equals(algoritmo);
        if (!(SignatureMethod.RSA_SHA256.equals(algoritmo) || permitirSha1 && sha1)
                || !List.of(CanonicalizationMethod.INCLUSIVE, CanonicalizationMethod.EXCLUSIVE)
                        .contains(hijo(datos, XMLSignature.XMLNS, "CanonicalizationMethod").getAttribute("Algorithm"))
                || firma.getElementsByTagNameNS(XMLSignature.XMLNS, "Object").getLength() != 0
                || firma.getElementsByTagNameNS(XMLSignature.XMLNS, "RetrievalMethod").getLength() != 0
                || datos.getElementsByTagNameNS(XMLSignature.XMLNS, "Reference").getLength() != 1) {
            return false;
        }
        Element referencia = hijo(datos, XMLSignature.XMLNS, "Reference");
        if (!referencia.hasAttribute("URI") || !referencia.getAttribute("URI").isEmpty()
                || !(sha1 ? DigestMethod.SHA1 : DigestMethod.SHA256)
                        .equals(hijo(referencia, XMLSignature.XMLNS, "DigestMethod").getAttribute("Algorithm"))) {
            return false;
        }
        Element transformaciones = hijo(referencia, XMLSignature.XMLNS, "Transforms");
        var algoritmos = new java.util.ArrayList<String>();
        for (Node nodo = transformaciones.getFirstChild(); nodo != null; nodo = nodo.getNextSibling()) {
            if (nodo.getNodeType() == Node.ELEMENT_NODE) {
                if (!es(nodo, XMLSignature.XMLNS, "Transform") || ((Element) nodo).getChildNodes().getLength() != 0) {
                    return false;
                }
                algoritmos.add(((Element) nodo).getAttribute("Algorithm"));
            }
        }
        return List.of(List.of(Transform.ENVELOPED), List.of(Transform.ENVELOPED, CanonicalizationMethod.INCLUSIVE),
                List.of(Transform.ENVELOPED, CanonicalizationMethod.EXCLUSIVE)).contains(algoritmos);
    }

    private static boolean vacio(String texto) { return texto == null || texto.isBlank(); }
    private static boolean es(Node nodo, String espacio, String nombre) {
        return nodo != null && nombre.equals(nodo.getLocalName()) && espacio.equals(nodo.getNamespaceURI());
    }
    private static Element hijo(Element padre, String espacio, String nombre) {
        Element encontrado = null;
        for (Node nodo = padre.getFirstChild(); nodo != null; nodo = nodo.getNextSibling()) {
            if (es(nodo, espacio, nombre)) {
                if (encontrado != null) { throw new IllegalArgumentException("Identidad ambigua."); }
                encontrado = (Element) nodo;
            }
        }
        if (encontrado == null) { throw new IllegalArgumentException("Campo requerido ausente."); }
        return encontrado;
    }
    private static Resultado fallo(String codigo) { return new Resultado(codigo, false, false, false); }
}
