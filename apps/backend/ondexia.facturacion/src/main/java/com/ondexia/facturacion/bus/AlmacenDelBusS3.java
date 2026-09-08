package com.ondexia.facturacion.bus;

import com.ondexia.facturacion.PropiedadesEmision;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * El bus sobre S3. Las credenciales son las del rol de ejecución; la región la
 * pone Lambda en {@code AWS_REGION}. Lo que este rol puede leer y escribir lo
 * dice {@code facturacion.tf}: lee {@code pendientes/}, {@code certificados/} y
 * {@code credenciales/}; escribe {@code documentos/}, {@code resultados/} y
 * {@code errores/}; borra {@code pendientes/}.
 */
@Component
@Profile("aws")
public class AlmacenDelBusS3 implements AlmacenDelBus {

    private final S3Client s3;
    private final String bucket;

    public AlmacenDelBusS3(PropiedadesEmision propiedades) {
        if (propiedades.bucket() == null || propiedades.bucket().isBlank()) {
            throw new IllegalStateException(
                    "Falta ondexia.emision.bucket (BUCKET_EMISION): sin bucket no hay bus.");
        }
        this.bucket = propiedades.bucket();
        this.s3 = S3Client.builder()
                .region(Region.of(System.getenv("AWS_REGION")))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Override
    public Optional<byte[]> leer(String clave) {
        try {
            return Optional.of(s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(clave).build()).asByteArray());
        } catch (NoSuchKeyException noExiste) {
            return Optional.empty();
        }
    }

    @Override
    public void escribir(String clave, byte[] contenido, String tipoContenido) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(clave)
                .contentType(tipoContenido).build(), RequestBody.fromBytes(contenido));
    }

    @Override
    public void borrar(String clave) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(clave).build());
    }
}
