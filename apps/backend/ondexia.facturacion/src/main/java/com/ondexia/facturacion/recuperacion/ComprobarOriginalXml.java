package com.ondexia.facturacion.recuperacion;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/** Entrada operativa sobre copias locales estables y un certificado público independiente. */
public final class ComprobarOriginalXml {
    private ComprobarOriginalXml() { }

    public static void main(String[] argumentos) {
        System.exit(ejecutar(argumentos, System.out, System.err));
    }

    /** Salidas: 0 integridad comprobada; 1 candidato rechazado; 2 entrada ilegible/no admitida. */
    public static int ejecutar(String[] argumentos, PrintStream salida, PrintStream errores) {
        if (argumentos.length != 5 && argumentos.length != 6
                || argumentos.length == 6 && !"--permitir-sha1-legado".equals(argumentos[5])) {
            errores.println("Uso: ComprobarOriginalXml XML CERTIFICADO_PUBLICO RUC TIPO SERIE-NUMERO [--permitir-sha1-legado]");
            return 2;
        }
        try {
            byte[] original = leer(Path.of(argumentos[0]), VerificadorDeOriginalXml.LIMITE_XML);
            byte[] publico = leer(Path.of(argumentos[1]), 64 * 1024);
            var fabrica = CertificateFactory.getInstance("X.509");
            var entrada = new java.io.ByteArrayInputStream(publico);
            var certificado = (X509Certificate) fabrica.generateCertificate(entrada);
            if (entrada.available() != 0) {
                throw new IllegalArgumentException("Se requiere un único certificado público.");
            }
            var resultado = new VerificadorDeOriginalXml().verificar(original, certificado,
                    argumentos[2], argumentos[3], argumentos[4], argumentos.length == 6);
            salida.printf("{\"codigo\":\"%s\",\"integridadVerificada\":%s,\"confianzaVerificada\":%s,\"aceptacionSunatVerificada\":%s}%n",
                    resultado.codigo(), resultado.integridadVerificada(),
                    resultado.confianzaVerificada(), resultado.aceptacionSunatVerificada());
            return resultado.integridadVerificada() ? 0 : 1;
        } catch (Exception noAdmitida) {
            errores.println("No se pudieron comprobar las entradas locales. Use copias regulares estables y un certificado público X.509.");
            return 2;
        }
    }

    private static byte[] leer(Path ruta, int limite) throws java.io.IOException {
        if (!Files.isRegularFile(ruta, LinkOption.NOFOLLOW_LINKS)) {
            throw new java.io.IOException("Archivo regular requerido.");
        }
        // No sigue el enlace final; los directorios de la copia deben ser de confianza.
        try (var canal = Files.newByteChannel(ruta, java.util.Set.of(
                java.nio.file.StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            if (canal.size() == 0 || canal.size() > limite) {
                throw new java.io.IOException("Tamaño no admitido.");
            }
            try (var entrada = java.nio.channels.Channels.newInputStream(canal)) {
                byte[] bytes = entrada.readNBytes(limite + 1);
                if (bytes.length == 0 || bytes.length > limite) {
                    throw new java.io.IOException("Tamaño no admitido.");
                }
                return bytes;
            }
        }
    }
}
