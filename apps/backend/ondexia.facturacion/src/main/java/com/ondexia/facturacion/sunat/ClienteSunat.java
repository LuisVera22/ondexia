package com.ondexia.facturacion.sunat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * El {@code sendBill} de SUNAT con el {@code HttpClient} del JDK (doc 14 §5).
 *
 * <p>Es SOAP 1.1 con WS-Security {@code UsernameToken}: el usuario es el RUC
 * seguido del usuario SOL, sin separador, y la contraseña es la clave SOL. El
 * cuerpo lleva el nombre del ZIP y su contenido en base64. Es todo lo que
 * xsender hace por esta operación, y se escribe aquí para no cargar Camel y
 * CXF en el arranque (ver el pom).
 *
 * <p>La clave SOL entra por parámetro y sale en el sobre; no se guarda ni se
 * registra. Tampoco se registra el cuerpo de una respuesta de error.
 */
public class ClienteSunat {

    private final HttpClient http;
    private final Duration tiempoDeEspera;
    private final LectorDeRespuestaSunat lector = new LectorDeRespuestaSunat();

    public ClienteSunat(Duration tiempoDeEspera) {
        this.tiempoDeEspera = tiempoDeEspera;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public RespuestaSunat enviar(String urlServicio, String ruc, String usuarioSol, String claveSol,
            String nombreZip, byte[] zip) {
        String sobre = sobre(ruc + usuarioSol, claveSol, nombreZip, zip);
        var peticion = HttpRequest.newBuilder(URI.create(urlServicio))
                .timeout(tiempoDeEspera)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "urn:sendBill")
                .POST(HttpRequest.BodyPublishers.ofString(sobre, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<byte[]> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofByteArray());
            return lector.leer(respuesta.statusCode(), respuesta.body());
        } catch (IOException e) {
            return RespuestaSunat.sinRespuesta("SIN_CONEXION",
                    "No se pudo conectar con SUNAT: " + e.getClass().getSimpleName()
                            + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RespuestaSunat.sinRespuesta("INTERRUMPIDO", "El envío a SUNAT se interrumpió.");
        }
    }

    /** El sobre, visible para la prueba: lo único que SUNAT ve de nosotros. */
    static String sobre(String usuario, String clave, String nombreZip, byte[] zip) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" \
                xmlns:ser="http://service.sunat.gob.pe" \
                xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">\
                <soapenv:Header><wsse:Security><wsse:UsernameToken>\
                <wsse:Username>%s</wsse:Username><wsse:Password>%s</wsse:Password>\
                </wsse:UsernameToken></wsse:Security></soapenv:Header>\
                <soapenv:Body><ser:sendBill><fileName>%s</fileName><contentFile>%s</contentFile>\
                </ser:sendBill></soapenv:Body></soapenv:Envelope>"""
                .formatted(escapar(usuario), escapar(clave), escapar(nombreZip),
                        Base64.getEncoder().encodeToString(zip));
    }

    private static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
