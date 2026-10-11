package com.ondexia.infrastructure.salida.marca;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.pruebas.ImagenDePrueba;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

class ValidadorDeImagenTest {
    @Test void pngEnLimiteDeBytesEsValido() {
        var datos = ImagenDePrueba.de("png");
        assertThatCode(() -> ValidadorDeImagen.validar(datos, "image/png", datos.length)).doesNotThrowAnyException();
    }

    @Test void jpegEsValido() {
        assertThatCode(() -> ValidadorDeImagen.validar(ImagenDePrueba.de("jpeg"), "image/jpeg", 262_144)).doesNotThrowAnyException();
    }

    @Test void unByteSobreLimiteFalla() {
        var datos = ImagenDePrueba.de("png");
        assertThatThrownBy(() -> ValidadorDeImagen.validar(datos, "image/png", datos.length - 1))
                .isInstanceOf(ReglaDeNegocioViolada.class).hasMessageContaining("tamaño permitido");
    }

    @Test void svgAnunciadoComoPngFalla() {
        invalida("<svg/>".getBytes(StandardCharsets.UTF_8), "image/png");
    }

    @Test void pngAnunciadoComoJpegFalla() { invalida(ImagenDePrueba.de("png"), "image/jpeg"); }
    @Test void jpegAnunciadoComoPngFalla() { invalida(ImagenDePrueba.de("jpeg"), "image/png"); }
    @Test void pngTruncadoFalla() { invalida(Arrays.copyOf(ImagenDePrueba.de("png"), 40), "image/png"); }
    @Test void vacioFalla() { invalida(new byte[0], "image/png"); }

    @Test void dimensionesExcesivasFallanAntesDeDecodificar() {
        var datos = ImagenDePrueba.de("png");
        // Cambia IHDR y su CRC; el flujo de píxeles sigue siendo 2×3, no una bomba real.
        ByteBuffer.wrap(datos).putInt(16, 65_536).putInt(20, 65_536);
        var crc = new CRC32(); crc.update(datos, 12, 17);
        ByteBuffer.wrap(datos).putInt(29, (int) crc.getValue());
        assertThatThrownBy(() -> ValidadorDeImagen.validar(datos, "image/png", 262_144))
                .isInstanceOf(ReglaDeNegocioViolada.class).hasMessageContaining("píxeles");
    }

    private void invalida(byte[] datos, String tipo) {
        assertThatThrownBy(() -> ValidadorDeImagen.validar(datos, tipo, 262_144))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessage("El archivo no es una imagen PNG o JPG válida del formato indicado.");
    }
}
