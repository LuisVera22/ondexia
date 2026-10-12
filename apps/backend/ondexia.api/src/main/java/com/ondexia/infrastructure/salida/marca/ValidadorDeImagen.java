package com.ondexia.infrastructure.salida.marca;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Validación local de contenido; casos y límites en ValidadorDeImagenTest. */
final class ValidadorDeImagen {
    // Acota expansión antes de asignar píxeles; no mide toda la memoria del proceso.
    static final long MAXIMO_PIXELES = 4_194_304L;

    private ValidadorDeImagen() { }

    static void validar(byte[] contenido, String tipoContenido, long maximoBytes) {
        if (contenido.length > maximoBytes) {
            throw new ReglaDeNegocioViolada("archivo_demasiado_grande", "El archivo supera el tamaño permitido.");
        }
        String tipo = tipoContenido == null ? "" : tipoContenido.trim().toLowerCase(Locale.ROOT);
        String formato = switch (tipo) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpeg";
            default -> throw invalida();
        };
        try (var entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(contenido))) {
            var lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) throw invalida();
            var lector = lectores.next();
            try {
                if (!lector.getFormatName().equalsIgnoreCase(formato)) throw invalida();
                lector.setInput(entrada, true, true);
                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);
                if (ancho <= 0 || alto <= 0 || (long) ancho * alto > MAXIMO_PIXELES) {
                    throw new ReglaDeNegocioViolada("imagen_demasiado_grande",
                            "La imagen supera 4.194.304 píxeles. Reduce sus dimensiones antes de subirla.");
                }
                var imagen = lector.read(0);
                if (imagen == null) throw invalida();
                imagen.flush();
            } finally {
                lector.dispose();
            }
        } catch (IOException | IllegalArgumentException fallo) {
            // No mostrar diagnóstico del parser ni contenido ajeno.
            throw invalida();
        }
    }

    private static ReglaDeNegocioViolada invalida() {
        return new ReglaDeNegocioViolada("imagen_no_valida",
                "El archivo no es una imagen PNG o JPG válida del formato indicado.");
    }
}
