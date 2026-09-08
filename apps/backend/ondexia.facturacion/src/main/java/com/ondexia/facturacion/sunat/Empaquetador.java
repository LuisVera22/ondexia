package com.ondexia.facturacion.sunat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * SUNAT recibe y devuelve archivos en ZIP: dentro va un solo XML con el nombre
 * del comprobante ({@code RUC-TIPO-SERIE-NUMERO.xml}).
 */
public final class Empaquetador {

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

    /** El primer XML que haya dentro. SUNAT a veces añade una carpeta {@code dummy/}. */
    public static byte[] primerXml(byte[] zip) {
        try (var entrada = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry item;
            while ((item = entrada.getNextEntry()) != null) {
                if (!item.isDirectory() && item.getName().toLowerCase().endsWith(".xml")) {
                    return entrada.readAllBytes();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new IllegalArgumentException("El ZIP no trae ningún XML.");
    }
}
