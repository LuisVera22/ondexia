package com.ondexia.facturacion.recuperacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondexia.facturacion.CertificadoDePrueba;
import com.ondexia.facturacion.Ordenes;
import com.ondexia.facturacion.sunat.ConstructorDeComprobante;
import com.ondexia.facturacion.sunat.FirmadorDeComprobante;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComprobarOriginalXmlTest {
    @TempDir Path carpeta;

    private String[] argumentos() throws Exception {
        var certificado = FirmadorDeComprobante.abrir(CertificadoDePrueba.bytes(), CertificadoDePrueba.CLAVE);
        Files.write(carpeta.resolve("original.xml"), new FirmadorDeComprobante().firmar(
                new ConstructorDeComprobante().construir(Ordenes.boleta(UUID.randomUUID())), certificado).xml());
        Files.write(carpeta.resolve("publico.cer"), certificado.getX509Certificate().getEncoded());
        return new String[] {carpeta.resolve("original.xml").toString(), carpeta.resolve("publico.cer").toString(),
                "20100000009", "03", "B001-12", "--permitir-sha1-legado"};
    }

    @Test
    void salidaOperativaNoModificaArchivosNiDeclaraAceptacion() throws Exception {
        var argumentos = argumentos();
        byte[] antes = Files.readAllBytes(Path.of(argumentos[0]));
        var salida = new ByteArrayOutputStream();
        var errores = new ByteArrayOutputStream();
        assertThat(ComprobarOriginalXml.ejecutar(argumentos, new PrintStream(salida), new PrintStream(errores))).isZero();
        assertThat(salida.toString()).isEqualTo("{\"codigo\":\"INTEGRIDAD_SHA1_LEGADO\",\"integridadVerificada\":true,"
                + "\"confianzaVerificada\":false,\"aceptacionSunatVerificada\":false}" + System.lineSeparator());
        assertThat(errores.size()).isZero();
        assertThat(Files.readAllBytes(Path.of(argumentos[0]))).isEqualTo(antes);
    }

    @Test
    void candidatoAlteradoDevuelveRechazo() throws Exception {
        var argumentos = argumentos();
        var ruta = Path.of(argumentos[0]);
        var juego = java.nio.charset.StandardCharsets.ISO_8859_1;
        String original = Files.readString(ruta, juego);
        assertThat(original).contains("Juan Perez Gomez");
        Files.writeString(ruta, original.replace("Juan Perez Gomez", "Cliente cambiado"), juego);
        var salida = new ByteArrayOutputStream();
        assertThat(ComprobarOriginalXml.ejecutar(argumentos, new PrintStream(salida), System.err)).isEqualTo(1);
        assertThat(salida.toString()).contains("\"codigo\":\"FIRMA_INVALIDA\"", "\"integridadVerificada\":false");
    }

    @Test
    void enlaceYEntradaIlegibleNoSeSiguenNiRevelanDetalles() throws Exception {
        var argumentos = argumentos();
        var enlace = carpeta.resolve("enlace.xml");
        Files.createSymbolicLink(enlace, Path.of(argumentos[0]));
        argumentos[0] = enlace.toString();
        var errores = new ByteArrayOutputStream();
        var salida = new ByteArrayOutputStream();
        assertThat(ComprobarOriginalXml.ejecutar(argumentos, new PrintStream(salida), new PrintStream(errores))).isEqualTo(2);
        assertThat(salida.size()).isZero();
        assertThat(errores.toString()).doesNotContain(carpeta.toString(), "Exception");
        argumentos[0] = carpeta.resolve("ausente.xml").toString();
        assertThat(ComprobarOriginalXml.ejecutar(argumentos, new PrintStream(salida), new PrintStream(errores))).isEqualTo(2);
        assertThat(ComprobarOriginalXml.ejecutar(new String[0], new PrintStream(salida), new PrintStream(errores))).isEqualTo(2);
    }
}
