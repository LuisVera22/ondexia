package com.ondexia.infrastructure.salida.marca;

import com.ondexia.domain.marca.AlmacenDeMarca;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Almacén de marca sobre S3.
 *
 * <h2>Firmar no usa la red</h2>
 *
 * <p>{@link S3Presigner} calcula la firma localmente con las credenciales. No
 * abre ninguna conexión, así que funciona en una Lambda sin salida a internet —
 * que es la razón por la que esta pantalla no necesitaba la NAT que el plan
 * temía.
 *
 * <p>Lo que sí sale a la red es {@link #describir} y {@link #eliminar}, y salen
 * por el <strong>endpoint de puerta de enlace</strong> de S3 que ya está en la
 * VPC. Es gratuito y no cruza internet.
 *
 * <h2>El tipo va dentro de la firma</h2>
 *
 * <p>La URL se firma para un {@code Content-Type} concreto. Subir otra cosa
 * rompe la firma y S3 responde 403 por su cuenta, sin que nadie tenga que
 * revisarlo después.
 *
 * <p>El tamaño no se puede atar igual —una URL prefirmada de {@code PUT} no
 * admite un rango de longitud, eso solo lo hace la política de {@code POST}, que
 * este SDK no genera— así que se comprueba de dos maneras: antes de firmar,
 * sobre lo que el cliente declara, y después de la subida con
 * {@link #describir}, sobre lo que S3 recibió de verdad. Un archivo que se pase
 * queda en el bucket pero nunca se referencia.
 */
@Component
@Profile("aws")
public class AlmacenDeMarcaS3 implements AlmacenDeMarca {

    /**
     * Margen para completar la subida.
     *
     * <p>Cinco minutos: sobra para un archivo de un megabyte incluso con mala
     * conexión, y acota la ventana en que una URL filtrada sirve de algo.
     */
    private static final Duration VALIDEZ = Duration.ofMinutes(5);

    private final S3Client s3;
    private final S3Presigner firmador;
    private final String bucket;
    private final String cdn;

    public AlmacenDeMarcaS3(S3Client s3, S3Presigner firmador,
            @Value("${ondexia.marca.bucket}") String bucket,
            @Value("${ondexia.marca.cdn}") String cdn) {
        this.s3 = s3;
        this.firmador = firmador;
        this.bucket = bucket;
        // Sin barra final: se compone con la clave, que nunca empieza por barra.
        this.cdn = cdn.endsWith("/") ? cdn.substring(0, cdn.length() - 1) : cdn;
    }

    @Override
    public AutorizacionDeSubida autorizarSubida(String clave, String tipoContenido, long bytes) {
        var peticion = PutObjectRequest.builder()
                .bucket(bucket)
                .key(clave)
                .contentType(tipoContenido)
                .build();

        var firmada = firmador.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(VALIDEZ)
                .putObjectRequest(peticion)
                .build());

        return new AutorizacionDeSubida(firmada.url().toString(), clave, VALIDEZ);
    }

    @Override
    public Optional<ObjetoDeMarca> describir(String clave) {
        try {
            var respuesta = s3.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(clave)
                    .build());

            return Optional.of(new ObjetoDeMarca(respuesta.contentLength(),
                    respuesta.contentType()));
        } catch (NoSuchKeyException e) {
            // La subida no llegó a ocurrir. No es un fallo del sistema: es la
            // respuesta a «¿está ahí?».
            return Optional.empty();
        }
    }

    @Override
    public void eliminar(String clave) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(clave).build());
    }

    @Override
    public String urlPublica(String clave) {
        return cdn + "/" + clave;
    }
}
