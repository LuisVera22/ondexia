package com.ondexia.infrastructure.salida.marca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.pruebas.ImagenDePrueba;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

class AlmacenDeMarcaS3Test {
    @Test void validaBytesRecibidosDeS3() {
        var datos = ImagenDePrueba.de("png");
        var s3 = mock(S3Client.class);
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().contentType("image/png").contentLength((long) datos.length).build(),
                new ByteArrayInputStream(datos)));
        assertThatCode(() -> almacen(s3).validarImagen("empresas/una/logo.png", "image/png", 262_144)).doesNotThrowAnyException();
    }

    @Test void cuerpoExcedidoSeAbortaSinConfiarEnLongitudAnunciada() {
        var leidos = new AtomicInteger();
        var aborto = new AtomicBoolean();
        var cuerpo = new InputStream() {
            @Override public int read() { leidos.incrementAndGet(); return 0; }
        };
        var s3 = mock(S3Client.class);
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength(1L).build(),
                AbortableInputStream.create(cuerpo, () -> aborto.set(true))));
        assertThatThrownBy(() -> almacen(s3).validarImagen("logo.png", "image/png", 262_144))
                .isInstanceOf(ReglaDeNegocioViolada.class).hasMessageContaining("tamaño permitido");
        assertThat(leidos.get()).isEqualTo(262_145);
        assertThat(aborto.get()).isTrue();
    }

    @Test void falloDeLecturaSeAbortaYNoFiltraDiagnostico() {
        var aborto = new AtomicBoolean();
        var cuerpo = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("diagnostico-ajeno"); }
        };
        var s3 = mock(S3Client.class);
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(), AbortableInputStream.create(cuerpo, () -> aborto.set(true))));
        assertThatThrownBy(() -> almacen(s3).validarImagen("logo.png", "image/png", 262_144))
                .isInstanceOf(IllegalStateException.class).hasMessage("No se pudo comprobar el archivo de marca.");
        assertThat(aborto.get()).isTrue();
    }

    @Test void desaparecidoNoSeAcepta() {
        var s3 = mock(S3Client.class);
        when(s3.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().message("diagnostico-ajeno").build());
        assertThatThrownBy(() -> almacen(s3).validarImagen("logo.png", "image/png", 262_144))
                .isInstanceOf(ReglaDeNegocioViolada.class).hasMessage("No se encontró el archivo. Vuelve a intentar la subida.");
    }

    private AlmacenDeMarcaS3 almacen(S3Client s3) {
        return new AlmacenDeMarcaS3(s3, null, "marca-prueba", "https://marca.invalid");
    }
}
