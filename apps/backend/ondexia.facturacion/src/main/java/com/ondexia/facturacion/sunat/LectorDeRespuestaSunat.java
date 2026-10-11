package com.ondexia.facturacion.sunat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Lee la respuesta SOAP de {@code sendBill} sin ninguna biblioteca de SOAP: es
 * un XML con dos formas posibles, y el analizador del JDK basta.
 *
 * <p>Con estado 200, el cuerpo trae {@code applicationResponse} con el ZIP del
 * CDR en base64. Con 500, un {@code Fault} cuyo {@code faultcode} termina en el
 * código de SUNAT ({@code soap-env:Client.2335}). Cualquier otra cosa —HTML de
 * mantenimiento, un 401 del proxy— es «sin respuesta», y se guarda el estado
 * HTTP pero <strong>no el cuerpo</strong> (CLAUDE.md: un cuerpo de error puede
 * repetir la credencial enviada).
 */
public class LectorDeRespuestaSunat {
    private static final int LIMITE_SOAP = 16 * 1024 * 1024;

    private static final String NS_CBC =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String NS_CAC =
            "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";

    public RespuestaSunat leer(int estadoHttp, byte[] cuerpo) {
        Document soap;
        try {
            soap = analizar(cuerpo);
        } catch (Exception noEsXml) {
            return RespuestaSunat.sinRespuesta("HTTP_" + estadoHttp,
                    "SUNAT respondió " + estadoHttp + " con un cuerpo que no es SOAP.");
        }
        Element fault = primero(soap, "*", "Fault");
        if (fault != null) {
            String faultcode = texto(primero(fault, "*", "faultcode"));
            String faultstring = texto(primero(fault, "*", "faultstring"));
            return new RespuestaSunat(RespuestaSunat.Tipo.FALLO, codigoDe(faultcode),
                    faultstring == null ? "SUNAT devolvió un fallo sin descripción." : faultstring,
                    List.of(), null);
        }
        Element respuesta = primero(soap, "*", "applicationResponse");
        if (respuesta != null) {
            return leerCdrCodificado(texto(respuesta)).conOriginal(cuerpo);
        }
        Element estadoDelTicket = primero(soap, "*", "status");
        if (estadoDelTicket != null) {
            return leerEstadoDeTicket(estadoDelTicket).conOriginal(cuerpo);
        }
        return RespuestaSunat.sinRespuesta("HTTP_" + estadoHttp,
                "SUNAT respondió " + estadoHttp + " sin CDR ni fallo.");
    }

    /**
     * La respuesta de {@code sendSummary}: un ticket, no una constancia
     * (doc 13 §6). El ticket viaja en {@code codigo} porque es lo que hay que
     * guardar para preguntar después.
     */
    public RespuestaSunat leerTicket(int estadoHttp, byte[] cuerpo) {
        Document soap;
        try {
            soap = analizar(cuerpo);
        } catch (Exception noEsXml) {
            return RespuestaSunat.sinRespuesta("HTTP_" + estadoHttp,
                    "SUNAT respondió " + estadoHttp + " con un cuerpo que no es SOAP.");
        }
        Element fault = primero(soap, "*", "Fault");
        if (fault != null) {
            return new RespuestaSunat(RespuestaSunat.Tipo.FALLO,
                    codigoDe(texto(primero(fault, "*", "faultcode"))),
                    textoODefecto(primero(fault, "*", "faultstring"),
                            "SUNAT devolvió un fallo sin descripción."),
                    List.of(), null);
        }
        Element ticket = primero(soap, "*", "ticket");
        if (ticket == null || texto(ticket) == null || texto(ticket).isBlank()) {
            return RespuestaSunat.sinRespuesta("SIN_TICKET",
                    "SUNAT aceptó el envío pero no devolvió ticket.");
        }
        return new RespuestaSunat(RespuestaSunat.Tipo.TICKET, texto(ticket).trim(),
                "SUNAT recibió el envío y devolvió un ticket.", List.of(), null).conOriginal(cuerpo);
    }

    /**
     * Lo que dice {@code getStatus}.
     *
     * <p>{@code 98} es «todavía lo estoy procesando» y no es un error: hay que
     * volver a preguntar. {@code 0} y {@code 99} traen el CDR dentro —el
     * segundo con el motivo del rechazo—, así que en los dos casos lo que vale
     * es lo que diga esa constancia.
     */
    private RespuestaSunat leerEstadoDeTicket(Element estado) {
        String codigo = textoODefecto(primero(estado, "*", "statusCode"), "");
        String mensaje = textoODefecto(primero(estado, "*", "statusMessage"), "");
        if ("98".equals(codigo.trim())) {
            return new RespuestaSunat(RespuestaSunat.Tipo.EN_PROCESO, "98",
                    mensaje.isBlank() ? "SUNAT todavía está procesando el envío." : mensaje,
                    List.of(), null);
        }
        Element contenido = primero(estado, "*", "content");
        if (contenido != null && texto(contenido) != null && !texto(contenido).isBlank()) {
            return leerCdrCodificado(texto(contenido));
        }
        return RespuestaSunat.sinRespuesta(codigo.isBlank() ? "SIN_ESTADO" : codigo.trim(),
                mensaje.isBlank() ? "SUNAT respondió al ticket sin constancia." : mensaje);
    }

    private static String textoODefecto(Element elemento, String defecto) {
        String valor = texto(elemento);
        return valor == null ? defecto : valor;
    }

    /** El CDR: un {@code ApplicationResponse} con el código, la descripción y las notas. */
    public RespuestaSunat leerCdr(byte[] zip) {
        Document cdr;
        try {
            cdr = analizar(Empaquetador.primerXml(zip));
        } catch (Exception e) {
            return cdrIlegible();
        }
        Element response = primero(cdr, NS_CAC, "Response");
        String codigo = response == null ? null : texto(primero(response, NS_CBC, "ResponseCode"));
        String descripcion = response == null ? null : texto(primero(response, NS_CBC, "Description"));
        var notas = new ArrayList<String>();
        NodeList nodos = cdr.getElementsByTagNameNS(NS_CBC, "Note");
        for (int i = 0; i < nodos.getLength(); i++) {
            String nota = nodos.item(i).getTextContent();
            if (nota != null && !nota.isBlank()) {
                notas.add(nota.trim());
            }
        }
        return new RespuestaSunat(RespuestaSunat.Tipo.CDR,
                codigo == null ? "CDR_SIN_CODIGO" : codigo.trim(),
                descripcion == null ? "" : descripcion.trim(), notas, zip);
    }

    private RespuestaSunat leerCdrCodificado(String contenido) {
        try {
            return leerCdr(Base64.getMimeDecoder().decode(contenido.trim()));
        } catch (IllegalArgumentException noAdmitido) {
            return cdrIlegible();
        }
    }

    private static RespuestaSunat cdrIlegible() {
        return RespuestaSunat.sinRespuesta("CDR_ILEGIBLE", "SUNAT devolvió un CDR que no se pudo leer.");
    }

    /** {@code soap-env:Client.2335} → {@code 2335}; {@code 0100} se queda como está. */
    static String codigoDe(String faultcode) {
        if (faultcode == null || faultcode.isBlank()) {
            return "FALLO_SIN_CODIGO";
        }
        String limpio = faultcode.trim();
        int punto = limpio.lastIndexOf('.');
        return punto >= 0 ? limpio.substring(punto + 1) : limpio;
    }

    private static Document analizar(byte[] xml) throws Exception {
        if (xml == null || xml.length == 0 || xml.length > LIMITE_SOAP) {
            throw new SAXException("Tamaño de XML no admitido.");
        }
        var fabrica = DocumentBuilderFactory.newInstance();
        fabrica.setNamespaceAware(true);
        // Entradas externas: LecturaHostilDeCdrTest.dtdNoConvierteRespuestaEnTicket
        // e inclusionExternaNoProduceTicket comprueban rechazo e inclusión desactivada;
        // diagnosticoDelParserNoSeGuardaNiSeImprime comprueba ausencia de diagnósticos.
        fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        fabrica.setFeature("http://xml.org/sax/features/external-general-entities", false);
        fabrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        fabrica.setExpandEntityReferences(false);
        fabrica.setXIncludeAware(false);
        fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var analizador = fabrica.newDocumentBuilder();
        analizador.setEntityResolver((publico, sistema) -> { throw new SAXException("Recurso externo no admitido."); });
        analizador.setErrorHandler(new DefaultHandler() {
            @Override public void error(SAXParseException error) throws SAXException { throw error; }
            @Override public void fatalError(SAXParseException error) throws SAXException { throw error; }
        });
        return analizador.parse(new ByteArrayInputStream(xml));
    }

    private static Element primero(Document documento, String ns, String nombre) {
        NodeList lista = documento.getElementsByTagNameNS(ns, nombre);
        return lista.getLength() == 0 ? null : (Element) lista.item(0);
    }

    private static Element primero(Element padre, String ns, String nombre) {
        NodeList lista = padre.getElementsByTagNameNS(ns, nombre);
        return lista.getLength() == 0 ? null : (Element) lista.item(0);
    }

    private static String texto(Element elemento) {
        return elemento == null ? null : elemento.getTextContent();
    }

    static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
