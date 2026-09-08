package com.ondexia.facturacion.sunat;

import io.github.project.openubl.xbuilder.signature.CertificateDetails;
import io.github.project.openubl.xbuilder.signature.CertificateDetailsFactory;
import io.github.project.openubl.xbuilder.signature.XMLSigner;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneOffset;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Firma el XML con el certificado de la empresa (doc 14 §5).
 *
 * <p>El certificado llega como bytes del {@code .pfx} y su contraseña, leídos
 * del bus para esta orden y descartados al terminar: no se guardan en ningún
 * campo ni quedan en la instantánea de SnapStart. La firma es la que SUNAT
 * exige: XML-DSig envuelta, RSA-SHA1 sobre el documento entero, con el
 * certificado dentro de {@code KeyInfo}; la produce {@link XMLSigner}, que la
 * coloca en {@code ext:ExtensionContent} con el identificador que la plantilla
 * ya referencia.
 *
 * <p>El {@code DigestValue} se devuelve aparte: va impreso en la representación
 * y dentro del QR (doc 12 §5.4).
 */
public class FirmadorDeComprobante {

    /** El {@code Id} de la firma; la plantilla lo enlaza desde {@code cac:Signature}. */
    static final String ID_FIRMA = "PROJECT-OPENUBL-SIGN";

    public record Firmado(byte[] xml, String resumen) {
    }

    /** Lo que se puede decir de un certificado sin firmar nada. */
    public record DatosDelCertificado(String sujeto, LocalDate venceEn) {
    }

    /**
     * Abre el {@code .pfx}. Falla con mensaje propio si la contraseña no
     * corresponde o el archivo no es un PKCS#12.
     */
    public static CertificateDetails abrir(byte[] pfx, String contrasena) {
        try {
            return CertificateDetailsFactory.create(new ByteArrayInputStream(pfx), contrasena);
        } catch (java.io.IOException e) {
            // KeyStore.load envuelve la contraseña incorrecta en IOException con
            // UnrecoverableKeyException de causa. Es el fallo más frecuente y
            // merece un mensaje que lo diga.
            throw new CertificadoNoAbre(e.getCause() instanceof GeneralSecurityException
                    ? "La contraseña no corresponde al certificado."
                    : "El archivo no es un certificado PKCS#12 (.pfx) legible.", e);
        } catch (GeneralSecurityException e) {
            throw new CertificadoNoAbre("No se pudo leer la clave privada del certificado.", e);
        }
    }

    public static DatosDelCertificado describir(CertificateDetails certificado) {
        X509Certificate x509 = certificado.getX509Certificate();
        return new DatosDelCertificado(x509.getSubjectX500Principal().getName(),
                x509.getNotAfter().toInstant().atOffset(ZoneOffset.UTC).toLocalDate());
    }

    public Firmado firmar(String xml, CertificateDetails certificado) {
        try {
            Document firmado = XMLSigner.signXML(xml, ID_FIRMA, certificado.getX509Certificate(),
                    certificado.getPrivateKey());
            return new Firmado(serializar(firmado), resumen(firmado));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el comprobante: " + e.getMessage(), e);
        }
    }

    private static String resumen(Document firmado) {
        NodeList valores = firmado.getElementsByTagNameNS(
                "http://www.w3.org/2000/09/xmldsig#", "DigestValue");
        return valores.getLength() == 0 ? null : valores.item(0).getTextContent();
    }

    private static byte[] serializar(Document documento) throws Exception {
        var transformador = TransformerFactory.newInstance().newTransformer();
        // Sin reindentar ni tocar la declaración: cualquier cambio después de
        // firmar invalida la firma. ISO-8859-1 es lo que declara la plantilla.
        transformador.setOutputProperty(OutputKeys.ENCODING, "ISO-8859-1");
        var salida = new StringWriter();
        transformador.transform(new DOMSource(documento), new StreamResult(salida));
        return salida.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    /** El certificado no se pudo abrir: contraseña, formato o clave. */
    public static class CertificadoNoAbre extends RuntimeException {

        public CertificadoNoAbre(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
