package com.ondexia.infrastructure.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Cliente de S3 para los logos de marca. Solo en la plataforma.
 *
 * <p>Fuera del perfil {@code aws} no se crea ninguno: en local y en pruebas actúa
 * {@code AlmacenDeMarcaEnMemoria}. Construir un cliente que nadie va a usar
 * obligaría a tener credenciales de AWS para arrancar la aplicación en local, y
 * eso rompe la Fase 0 del DTE §10.3.
 *
 * <h2>Cliente HTTP del JDK</h2>
 *
 * <p>El SDK trae Netty y Apache por omisión. Los dos arrastran su propia pila de
 * red —Netty además arranca hilos— y aquí las llamadas son dos por subida.
 * {@link UrlConnectionHttpClient} usa lo que ya está en el JDK: sin peso extra en
 * un artefacto que ronda los 67 MB, y sin hilos que SnapStart tenga que
 * congelar.
 *
 * <h2>Las credenciales</h2>
 *
 * <p>No se configuran: las toma de la cadena por omisión, que en Lambda son las
 * del rol de ejecución. Nada de claves en variables de entorno.
 */
@Configuration
@Profile("aws")
public class S3Config {

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                // Region.of() lee AWS_REGION, que define el propio entorno de
                // Lambda. No hay que pasarla desde Terraform.
                .region(Region.of(System.getenv("AWS_REGION")))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    /**
     * El firmador no lleva cliente HTTP: firmar es un cálculo local sobre las
     * credenciales, sin ninguna llamada de red. Esa propiedad es la que permite
     * subir archivos desde una Lambda sin salida a internet.
     */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(System.getenv("AWS_REGION")))
                .build();
    }
}
