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

    @org.springframework.beans.factory.annotation.Autowired
    public AlmacenDelBusS3(PropiedadesEmision propiedades) {
        this(exigirBucket(propiedades), S3Client.builder()
                .region(Region.of(System.getenv("AWS_REGION")))
                .httpClientBuilder(UrlConnectionHttpClient.builder()).build());
    }

    AlmacenDelBusS3(String bucket, S3Client s3) {
        this.bucket = bucket;
        this.s3 = s3;
    }

    private static String exigirBucket(PropiedadesEmision propiedades) {
        if (propiedades.bucket() == null || propiedades.bucket().isBlank()) {
            throw new IllegalStateException("Falta ondexia.emision.bucket (BUCKET_EMISION): sin bucket no hay bus.");
        }
        return propiedades.bucket();
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
        var peticion = PutObjectRequest.builder().bucket(bucket).key(clave).contentType(tipoContenido);
        // OriginalesEnS3Test.exigeEscrituraCondicional: solicita crear sin reemplazar.
        // La aplicación efectiva de esta condición en AWS no se ha ensayado aquí.
        if (clave.startsWith(com.ondexia.domain.comprobante.ClavesDelBus.DOCUMENTOS)) {
            peticion.ifNoneMatch("*");
        }
        s3.putObject(peticion.build(), RequestBody.fromBytes(contenido));
    }

    @Override
    public void borrar(String clave) {
        if (clave.startsWith(com.ondexia.domain.comprobante.ClavesDelBus.DOCUMENTOS)) {
            throw new IllegalArgumentException("Un documento original no se borra del bus.");
        }
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(clave).build());
    }
}
