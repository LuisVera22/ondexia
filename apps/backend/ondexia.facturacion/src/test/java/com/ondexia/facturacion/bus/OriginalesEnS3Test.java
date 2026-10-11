package com.ondexia.facturacion.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class OriginalesEnS3Test {
    /** Intercepta la frontera SDK sin red ni instrumentación del proceso de Java. */
    private static class ClienteS3Fingido {
        PutObjectRequest peticion;
        RequestBody cuerpo;
        int invocaciones;
        final S3Client cliente = (S3Client) Proxy.newProxyInstance(S3Client.class.getClassLoader(),
                new Class<?>[]{S3Client.class}, (instancia, metodo, argumentos) -> {
                    invocaciones++;
                    if (metodo.getName().equals("putObject")) {
                        peticion = (PutObjectRequest) argumentos[0];
                        cuerpo = (RequestBody) argumentos[1];
                        return PutObjectResponse.builder().build();
                    }
                    throw new UnsupportedOperationException("Operación S3 inesperada: " + metodo.getName());
                });
    }

    @Test
    void exigeEscrituraCondicional() throws java.io.IOException {
        var s3 = new ClienteS3Fingido();
        var bus = new AlmacenDelBusS3("bucket-de-prueba", s3.cliente);
        byte[] original = {1, 2, 3};
        bus.escribir("documentos/original.xml", original, "application/xml");
        assertThat(s3.invocaciones).isEqualTo(1);
        assertThat(s3.peticion.ifNoneMatch()).isEqualTo("*");
        assertThat(s3.peticion.key()).isEqualTo("documentos/original.xml");
        try (var entrada = s3.cuerpo.contentStreamProvider().newStream()) {
            assertThat(entrada.readAllBytes()).isEqualTo(original);
        }
    }

    @Test
    void noSolicitaBorrarUnOriginal() {
        var s3 = new ClienteS3Fingido();
        var bus = new AlmacenDelBusS3("bucket-de-prueba", s3.cliente);
        assertThatThrownBy(() -> bus.borrar("documentos/original.xml"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(s3.invocaciones).isZero();
    }
}
