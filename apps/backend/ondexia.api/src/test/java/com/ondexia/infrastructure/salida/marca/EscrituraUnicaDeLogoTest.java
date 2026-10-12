package com.ondexia.infrastructure.salida.marca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.pruebas.ImagenDePrueba;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URLDecoder;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class EscrituraUnicaDeLogoTest {
    @Test void condicionDeCreacionFormaParteDeLaFirmaRealDelSdk() {
        try (var firmador = S3Presigner.builder().region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("acceso-prueba", "clave-prueba")))
                .build()) {
            var almacen = new AlmacenDeMarcaS3(null, firmador, "marca-prueba", "https://marca.invalid");
            var permiso = almacen.autorizarSubida("empresas/una/logo/archivo.png", "image/png", 100);
            var consulta = URLDecoder.decode(URI.create(permiso.url()).getRawQuery(), StandardCharsets.UTF_8);
            assertThat(consulta).contains("X-Amz-SignedHeaders=content-type;host;if-none-match");
        }
    }

    @Test void reutilizarSubidaNoReemplazaContenidoAnteriorEnElDoble() {
        var almacen = new AlmacenDeMarcaEnMemoria();
        var original = ImagenDePrueba.de("png");
        almacen.simularSubida("logo.png", "image/png", original);
        assertThatThrownBy(() -> almacen.simularSubida("logo.png", "image/jpeg", new byte[]{4}))
                .isInstanceOf(ReglaDeNegocioViolada.class);
        assertThat(almacen.describir("logo.png").orElseThrow())
                .isEqualTo(new com.ondexia.domain.marca.AlmacenDeMarca.ObjetoDeMarca(original.length, "image/png"));
        almacen.validarImagen("logo.png", "image/png", 1_048_576);
    }

    @Test void simulacionDeMetadataTampocoReemplazaArchivo() {
        var almacen = new AlmacenDeMarcaEnMemoria();
        almacen.simularSubida("logo.png", "image/png", 100);
        assertThatThrownBy(() -> almacen.simularSubida("logo.png", "image/png", 200))
                .isInstanceOf(ReglaDeNegocioViolada.class);
    }

    @Test void clavesDistintasAdmitenVersionesIndependientes() {
        var almacen = new AlmacenDeMarcaEnMemoria();
        almacen.simularSubida("primero.png", "image/png", ImagenDePrueba.de("png"));
        almacen.simularSubida("segundo.jpg", "image/jpeg", ImagenDePrueba.de("jpeg"));
        almacen.validarImagen("primero.png", "image/png", 1_048_576);
        almacen.validarImagen("segundo.jpg", "image/jpeg", 1_048_576);
    }

    @Test void cambiarElArregloDelEmisorNoModificaLoGuardado() {
        var almacen = new AlmacenDeMarcaEnMemoria();
        var original = ImagenDePrueba.de("png");
        almacen.simularSubida("logo.png", "image/png", original);
        java.util.Arrays.fill(original, (byte) 0);
        almacen.validarImagen("logo.png", "image/png", 1_048_576);
    }
}
