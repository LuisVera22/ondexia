package com.ondexia.facturacion.sunat;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Las tres formas en que SUNAT contesta, con cuerpos como los reales. */
public class LectorDeRespuestaSunatTest {

    private final LectorDeRespuestaSunat lector = new LectorDeRespuestaSunat();

    public static byte[] cdr(String codigo, String descripcion, String... notas) {
        var sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <ApplicationResponse xmlns="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2"
                  xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                  xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                  <cbc:ID>20100000009-03-B001-12</cbc:ID>
                """);
        for (String nota : notas) {
            sb.append("  <cbc:Note>").append(nota).append("</cbc:Note>\n");
        }
        sb.append("""
                  <cac:DocumentResponse>
                    <cac:Response>
                      <cbc:ReferenceID>B001-12</cbc:ReferenceID>
                      <cbc:ResponseCode>%s</cbc:ResponseCode>
                      <cbc:Description>%s</cbc:Description>
                    </cac:Response>
                  </cac:DocumentResponse>
                </ApplicationResponse>
                """.formatted(codigo, descripcion));
        return Empaquetador.comprimir("R-20100000009-03-B001-00000012.xml",
                sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    static byte[] sobreConCdr(byte[] zip) {
        return ("""
                <soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap-env:Body>
                    <br:sendBillResponse xmlns:br="http://service.sunat.gob.pe">
                      <applicationResponse>%s</applicationResponse>
                    </br:sendBillResponse>
                  </soap-env:Body>
                </soap-env:Envelope>
                """.formatted(Base64.getEncoder().encodeToString(zip))).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Un CDR con código 0 es aceptado, con sus observaciones")
    void aceptado() {
        var respuesta = lector.leer(200, sobreConCdr(cdr("0",
                "La Boleta numero B001-12, ha sido aceptada", "4252 - El dato ingresado como observación")));
        assertThat(respuesta.tipo()).isEqualTo(RespuestaSunat.Tipo.CDR);
        assertThat(respuesta.aceptado()).isTrue();
        assertThat(respuesta.rechazado()).isFalse();
        assertThat(respuesta.codigo()).isEqualTo("0");
        assertThat(respuesta.descripcion()).contains("aceptada");
        assertThat(respuesta.notas()).containsExactly("4252 - El dato ingresado como observación");
        assertThat(respuesta.cdr()).isNotNull();
    }

    @Test
    @DisplayName("Un fallo SOAP con Client.2335 es un rechazo con ese código")
    void falloRechazo() {
        byte[] cuerpo = """
                <soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap-env:Body>
                    <soap-env:Fault>
                      <faultcode>soap-env:Client.2335</faultcode>
                      <faultstring>El documento electrónico ingresado ha sido alterado</faultstring>
                    </soap-env:Fault>
                  </soap-env:Body>
                </soap-env:Envelope>
                """.getBytes(StandardCharsets.UTF_8);
        var respuesta = lector.leer(500, cuerpo);
        assertThat(respuesta.tipo()).isEqualTo(RespuestaSunat.Tipo.FALLO);
        assertThat(respuesta.codigo()).isEqualTo("2335");
        assertThat(respuesta.rechazado()).isTrue();
        assertThat(respuesta.aceptado()).isFalse();
        assertThat(respuesta.descripcion()).contains("alterado");
        assertThat(respuesta.cdr()).isNull();
    }

    @Test
    @DisplayName("Un fallo 0100–1999 es del servicio: ni aceptado ni rechazado")
    void falloDelServicio() {
        byte[] cuerpo = """
                <soap-env:Envelope xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap-env:Body><soap-env:Fault>
                    <faultcode>soap-env:Server.0100</faultcode>
                    <faultstring>El sistema no puede responder su solicitud. Intente nuevamente o comuniquese con su Administrador.</faultstring>
                  </soap-env:Fault></soap-env:Body>
                </soap-env:Envelope>
                """.getBytes(StandardCharsets.UTF_8);
        var respuesta = lector.leer(500, cuerpo);
        assertThat(respuesta.codigo()).isEqualTo("0100");
        assertThat(respuesta.rechazado()).isFalse();
        assertThat(respuesta.aceptado()).isFalse();
    }

    @Test
    @DisplayName("HTML de mantenimiento o un 401: sin respuesta, con el estado y sin el cuerpo")
    void sinRespuesta() {
        var respuesta = lector.leer(503, "<html><body>Mantenimiento</body></html>".getBytes());
        assertThat(respuesta.tipo()).isEqualTo(RespuestaSunat.Tipo.SIN_RESPUESTA);
        assertThat(respuesta.codigo()).isEqualTo("HTTP_503");
        assertThat(respuesta.descripcion()).doesNotContain("Mantenimiento");
    }

    @Test
    @DisplayName("El código se toma después del último punto del faultcode")
    void codigoDelFault() {
        assertThat(LectorDeRespuestaSunat.codigoDe("soap-env:Client.2335")).isEqualTo("2335");
        assertThat(LectorDeRespuestaSunat.codigoDe("soap-env:Server.0100")).isEqualTo("0100");
        assertThat(LectorDeRespuestaSunat.codigoDe("1033")).isEqualTo("1033");
        assertThat(LectorDeRespuestaSunat.codigoDe(null)).isEqualTo("FALLO_SIN_CODIGO");
    }

    @Test
    @DisplayName("El sobre de sendBill lleva RUC+usuario, la clave y el zip en base64")
    void sobre() {
        String sobre = ClienteSunat.sobre("20100000009MODDATOS", "clave<&>", "x.zip", new byte[] {1, 2, 3});
        assertThat(sobre).contains("<wsse:Username>20100000009MODDATOS</wsse:Username>")
                .contains("<wsse:Password>clave&lt;&amp;&gt;</wsse:Password>")
                .contains("<fileName>x.zip</fileName>")
                .contains("<contentFile>AQID</contentFile>")
                .contains("xmlns:ser=\"http://service.sunat.gob.pe\"");
    }
}
