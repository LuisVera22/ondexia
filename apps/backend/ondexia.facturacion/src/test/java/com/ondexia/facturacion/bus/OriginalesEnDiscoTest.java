package com.ondexia.facturacion.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.facturacion.PropiedadesEmision;
import java.time.Duration;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OriginalesEnDiscoTest {
    @TempDir Path directorio;

    @Test
    void noSobrescribeUnOriginal() {
        var bus = new AlmacenDelBusEnDisco(new PropiedadesEmision("", directorio.toString(),
                "", "", Duration.ofSeconds(1)));
        byte[] original = {1, 2, 3};
        bus.escribir("documentos/original.xml", original, "application/xml");
        assertThatThrownBy(() -> bus.escribir("documentos/original.xml", new byte[]{4, 5},
                "application/xml")).isInstanceOf(java.io.UncheckedIOException.class);
        assertThat(bus.leer("documentos/original.xml").orElseThrow()).isEqualTo(original);
        assertThatThrownBy(() -> bus.borrar("documentos/original.xml"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(bus.leer("documentos/original.xml").orElseThrow()).isEqualTo(original);
    }
}
