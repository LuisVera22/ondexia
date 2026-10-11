package com.ondexia.facturacion.sunat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LecturaHostilDeCdrTest {
    private static final int LIMITE_DESCOMPRIMIDO = 8 * 1024 * 1024;
    private final LectorDeRespuestaSunat lector = new LectorDeRespuestaSunat();

    private static byte[] zipConEntradaPrevia(byte[] previa) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("previa.txt"));
            zip.write(previa);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("dummy/constancia.XML"));
            zip.write("<constancia/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    @Test
    void xmlDescomprimidoSobreLimiteNoSeDevuelve() {
        byte[] excesivo = Empaquetador.comprimir("constancia.xml", new byte[LIMITE_DESCOMPRIMIDO + 1]);
        assertThatThrownBy(() -> Empaquetador.primerXml(excesivo)).isInstanceOf(IllegalArgumentException.class);
        assertThat(lector.leerCdr(excesivo).codigo()).isEqualTo("CDR_ILEGIBLE");
    }

    @Test
    void limiteExactoSeAdmiteSinUsarTamanoDeclarado() {
        byte[] exacto = new byte[LIMITE_DESCOMPRIMIDO];
        byte[] zip = Empaquetador.comprimir("constancia.xml", exacto);
        assertThat(Empaquetador.primerXml(zip)).isEqualTo(exacto);
    }

    @Test
    void bytesDeEntradaPreviaTambienCuentan() throws Exception {
        assertThatThrownBy(() -> Empaquetador.primerXml(zipConEntradaPrevia(new byte[LIMITE_DESCOMPRIMIDO])))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void demasiadasEntradasNoSeRecorren() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (int indice = 0; indice < 32; indice++) {
                zip.putNextEntry(new ZipEntry("ignorada-" + indice + ".txt"));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("constancia.xml"));
            zip.write("<constancia/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertThatThrownBy(() -> Empaquetador.primerXml(bytes.toByteArray())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void treintaYDosEntradasSeAdmiten() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (int indice = 0; indice < 31; indice++) {
                zip.putNextEntry(new ZipEntry("carpeta-" + indice + "/"));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("constancia.xml"));
            zip.write("<constancia/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertThat(Empaquetador.primerXml(bytes.toByteArray())).isEqualTo("<constancia/>".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void zipComprimidoExcesivoSeRechazaAntesDeRecorrerlo() {
        assertThatThrownBy(() -> Empaquetador.primerXml(new byte[8 * 1024 * 1024 + 1]))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Tamaño del ZIP no admitido.");
    }

    @Test
    void zipAlLimiteComprimidoSeAdmite() throws Exception {
        String nombre = "constancia.xml";
        // ZIP STORED sin extras: cabeceras 30 + 46 + 22 y dos copias del nombre.
        byte[] xml = new byte[8 * 1024 * 1024 - 98 - 2 * nombre.length()];
        var resumen = new java.util.zip.CRC32();
        resumen.update(xml);
        var entrada = new ZipEntry(nombre);
        entrada.setMethod(ZipEntry.STORED);
        entrada.setSize(xml.length);
        entrada.setCrc(resumen.getValue());
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(entrada);
            zip.write(xml);
            zip.closeEntry();
        }
        assertThat(bytes.size()).isEqualTo(8 * 1024 * 1024);
        assertThat(Empaquetador.primerXml(bytes.toByteArray())).isEqualTo(xml);
    }

    @Test
    void presupuestoExactoIncluyeEntradaPreviaYXml() throws Exception {
        assertThat(Empaquetador.primerXml(zipConEntradaPrevia(new byte[LIMITE_DESCOMPRIMIDO - 13])))
                .isEqualTo("<constancia/>".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void sinXmlYZipCorruptoNoSeInterpretanComoConstancia() {
        for (byte[] zip : new byte[][] {new byte[0], new byte[] {1, 2, 3},
                Empaquetador.comprimir("ignorada.txt", "texto".getBytes(StandardCharsets.UTF_8))}) {
            var respuesta = lector.leerCdr(zip);
            assertThat(respuesta.codigo()).isEqualTo("CDR_ILEGIBLE");
            assertThat(respuesta.aceptado()).isFalse();
            assertThat(respuesta.rechazado()).isFalse();
            assertThat(respuesta.respuestaOriginal()).isNull();
            assertThat(respuesta.cdr()).isNull();
        }
    }

    @Test
    void carpetaYExtensionMayusculaSeConservan() throws Exception {
        assertThat(Empaquetador.primerXml(zipConEntradaPrevia(new byte[0])))
                .isEqualTo("<constancia/>".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void base64IlegibleNoEscapaDelLector() {
        byte[] sobre = ("<Envelope><applicationResponse>A</applicationResponse></Envelope>")
                .getBytes(StandardCharsets.UTF_8);
        assertThat(lector.leer(200, sobre).codigo()).isEqualTo("CDR_ILEGIBLE");
    }

    @Test
    void base64IlegibleDeEstadoNoEscapaDelLector() {
        byte[] estado = "<Envelope><status><statusCode>0</statusCode><content>A</content></status></Envelope>"
                .getBytes(StandardCharsets.UTF_8);
        assertThat(lector.leer(200, estado).codigo()).isEqualTo("CDR_ILEGIBLE");
    }

    @Test
    void diagnosticoDelParserNoSeGuardaNiSeImprime() throws Exception {
        byte[] malformado = "<MARCADOR_PUBLICO_DE_PRUEBA></otro>".getBytes(StandardCharsets.UTF_8);
        var anterior = System.err;
        var errores = new ByteArrayOutputStream();
        RespuestaSunat respuesta;
        try (var captura = new PrintStream(errores, true, StandardCharsets.UTF_8)) {
            System.setErr(captura);
            respuesta = lector.leerCdr(Empaquetador.comprimir("constancia.xml", malformado));
        } finally {
            System.setErr(anterior);
        }
        org.assertj.core.api.SoftAssertions.assertSoftly(comprobaciones -> {
            comprobaciones.assertThat(respuesta.descripcion()).isEqualTo("SUNAT devolvió un CDR que no se pudo leer.");
            comprobaciones.assertThat(errores.toString(StandardCharsets.UTF_8)).isEmpty();
        });
        assertThat(respuesta.aceptado()).isFalse();
        assertThat(respuesta.rechazado()).isFalse();
        assertThat(respuesta.cdr()).isNull();
        assertThat(respuesta.respuestaOriginal()).isNull();
    }

    @Test
    void dtdNoConvierteRespuestaEnTicket() {
        byte[] sobre = "<!DOCTYPE Envelope><Envelope><ticket>1554895123456</ticket></Envelope>"
                .getBytes(StandardCharsets.UTF_8);
        var respuesta = lector.leerTicket(200, sobre);
        assertThat(respuesta.tipo()).isEqualTo(RespuestaSunat.Tipo.SIN_RESPUESTA);
        assertThat(respuesta.codigo()).isEqualTo("HTTP_200");
        assertThat(respuesta.respuestaOriginal()).isNull();
    }

    @Test
    void inclusionExternaNoProduceTicket(@TempDir Path carpeta) throws Exception {
        Path archivo = carpeta.resolve("inclusion-publica.txt");
        Files.writeString(archivo, "1554895123456", StandardCharsets.UTF_8);
        byte[] sobre = ("<Envelope xmlns:xi=\"http://www.w3.org/2001/XInclude\"><ticket>"
                + "<xi:include href=\"" + archivo.toUri() + "\" parse=\"text\"/>"
                + "</ticket></Envelope>").getBytes(StandardCharsets.UTF_8);
        var respuesta = lector.leerTicket(200, sobre);
        assertThat(respuesta.tipo()).isEqualTo(RespuestaSunat.Tipo.SIN_RESPUESTA);
        assertThat(respuesta.codigo()).isEqualTo("SIN_TICKET");
        assertThat(respuesta.respuestaOriginal()).isNull();
    }

    @Test
    void soapSobreLimiteNoSeAnalizaComoTicket() {
        byte[] cuerpo = ("<Envelope><ticket>1554895123456</ticket>" + " ".repeat(16 * 1024 * 1024)
                + "</Envelope>").getBytes(StandardCharsets.UTF_8);
        assertThat(lector.leerTicket(200, cuerpo).tipo()).isEqualTo(RespuestaSunat.Tipo.SIN_RESPUESTA);
    }

    @Test
    void soapEnLimiteExactoConservaTicketYBytes() {
        String inicio = "<Envelope><ticket>1554895123456</ticket>";
        String fin = "</Envelope>";
        byte[] cuerpo = (inicio + " ".repeat(16 * 1024 * 1024 - inicio.length() - fin.length()) + fin)
                .getBytes(StandardCharsets.UTF_8);
        var respuesta = lector.leerTicket(200, cuerpo);
        assertThat(respuesta.tipo()).isEqualTo(RespuestaSunat.Tipo.TICKET);
        assertThat(respuesta.codigo()).isEqualTo("1554895123456");
        assertThat(respuesta.respuestaOriginal()).isEqualTo(cuerpo);
    }
}
