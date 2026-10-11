package com.ondexia.facturacion.sunat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * SUNAT recibe y devuelve archivos en ZIP: dentro va un solo XML con el nombre
 * del comprobante ({@code RUC-TIPO-SERIE-NUMERO.xml}).
 */
public final class Empaquetador {
    private static final int LIMITE_ZIP = 8 * 1024 * 1024;
    private static final int LIMITE_DESCOMPRIMIDO = 8 * 1024 * 1024;
    private static final int LIMITE_ENTRADAS = 32;

    private Empaquetador() {
    }

    public static byte[] comprimir(String nombreXml, byte[] xml) {
        var salida = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(salida)) {
            zip.putNextEntry(new ZipEntry(nombreXml));
            zip.write(xml);
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return salida.toByteArray();
    }

    /**
     * Primer XML, sin extracción al disco. SUNAT a veces añade {@code dummy/}.
     * Lectura acotada, incluidas entradas previas: {@code LecturaHostilDeCdrTest}.
     */
    public static byte[] primerXml(byte[] zip) {
        if (zip == null || zip.length == 0 || zip.length > LIMITE_ZIP) {
            throw new IllegalArgumentException("Tamaño del ZIP no admitido.");
        }
        try (var entrada = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry item;
            int examinadas = 0;
            int consumidos = 0;
            byte[] bloque = new byte[8192];
            while ((item = entrada.getNextEntry()) != null) {
                if (++examinadas > LIMITE_ENTRADAS) {
                    throw new IllegalArgumentException("El ZIP supera el límite de entradas.");
                }
                boolean esXml = !item.isDirectory() && item.getName().toLowerCase(Locale.ROOT).endsWith(".xml");
                var xml = esXml ? new ByteArrayOutputStream() : null;
                int leidos;
                // No confiar en ZipEntry.getSize(); tampoco dejar que getNextEntry descarte sin límite.
                while ((leidos = entrada.read(bloque, 0,
                        Math.min(bloque.length, LIMITE_DESCOMPRIMIDO - consumidos + 1))) != -1) {
                    consumidos += leidos;
                    if (consumidos > LIMITE_DESCOMPRIMIDO) {
                        throw new IllegalArgumentException("El ZIP supera el límite descomprimido.");
                    }
                    if (esXml) { xml.write(bloque, 0, leidos); }
                }
                if (esXml) { return xml.toByteArray(); }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new IllegalArgumentException("El ZIP no trae ningún XML.");
    }
}
