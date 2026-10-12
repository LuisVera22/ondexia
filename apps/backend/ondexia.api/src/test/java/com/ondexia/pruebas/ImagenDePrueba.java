package com.ondexia.pruebas;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public final class ImagenDePrueba {
    private ImagenDePrueba() { }

    public static byte[] de(String formato) {
        try {
            var salida = new ByteArrayOutputStream();
            if (!ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), formato, salida)) {
                throw new IllegalStateException("No hay escritor para la imagen de prueba.");
            }
            return salida.toByteArray();
        } catch (IOException fallo) {
            throw new IllegalStateException("No se pudo construir la imagen de prueba.", fallo);
        }
    }
}
