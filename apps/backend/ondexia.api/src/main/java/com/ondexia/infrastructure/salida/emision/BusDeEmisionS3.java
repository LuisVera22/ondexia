package com.ondexia.infrastructure.salida.emision;

import com.ondexia.domain.comprobante.BusDeEmision;
import com.ondexia.domain.comprobante.ClavesDelBus;
import com.ondexia.domain.comprobante.OrdenDeEmision;
import com.ondexia.domain.comprobante.ResultadoDeEmision;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * El bus sobre el bucket de emisión (doc 14 §2).
 *
 * <p>Todo lo que hace pasa por el endpoint de puerta de enlace de S3, que es
 * gratuito y no cruza internet: es lo que permite que la API, en subred privada
 * sin NAT, hable con un Emisor que está fuera de la VPC.
 *
 * <h2>Lo que este rol puede y no puede</h2>
 *
 * <p>La política de la API en {@code facturacion.tf} concede escritura sobre
 * {@code pendientes/}, {@code certificados/} y {@code credenciales/}, lectura
 * sobre {@code resultados/} y {@code documentos/}, y listado por prefijo sobre
 * {@code certificados/} y {@code credenciales/}. <strong>No</strong> concede
 * lectura sobre esos dos últimos: la API firma la URL con la que el navegador
 * sube el {@code .pfx} y su contraseña, y después no puede leerlos. Un módulo
 * que puede leer certificado y contraseña es un módulo que puede firmar, y eso
 * es lo que CLAUDE.md prohíbe en {@code ondexia.api}. La prueba de que la
 * política dice eso es el propio {@code terraform plan}; desde este repositorio
 * no hay forma de ejercitar IAM.
 *
 * <p>La URL prefirmada de {@code PUT} ejecuta con las credenciales de quien
 * firmó, así que esa escritura sí tiene que estar en la política. Y el tipo de
 * contenido va dentro de la firma: subir otra cosa la rompe y S3 responde 403.
 */
@Component
@Profile("aws")
public class BusDeEmisionS3 implements BusDeEmision {

    private final S3Client s3;
    private final S3Presigner firmador;
    private final ObjectMapper json;
    private final String bucket;

    public BusDeEmisionS3(S3Client s3, S3Presigner firmador, ObjectMapper json,
            @Value("${ondexia.emision.bucket}") String bucket) {
        this.s3 = s3;
        this.firmador = firmador;
        this.json = json;
        this.bucket = bucket;
    }

    @Override
    public void publicar(OrdenDeEmision orden) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(ClavesDelBus.pendiente(orden.empresaId(), orden.id()))
                        .contentType("application/json")
                        .build(),
                RequestBody.fromString(json.writeValueAsString(orden)));
    }

    @Override
    public Optional<ResultadoDeEmision> resultadoDe(UUID empresaId, UUID ordenId) {
        try (var cuerpo = s3.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(ClavesDelBus.resultado(empresaId, ordenId))
                .build())) {
            return Optional.of(json.readValue(cuerpo, ResultadoDeEmision.class));
        } catch (NoSuchKeyException todaviaNo) {
            return Optional.empty();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo leer el resultado de la orden " + ordenId, e);
        }
    }

    @Override
    public boolean existe(String clave) {
        // Listar con la clave como prefijo, en vez de HeadObject: HeadObject
        // exige s3:GetObject, y sobre certificados/ y credenciales/ la API no lo
        // tiene a propósito. Un listado acotado a un prefijo no lee el objeto.
        var respuesta = s3.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(clave)
                .maxKeys(1)
                .build());
        return respuesta.contents().stream().anyMatch(o -> o.key().equals(clave) && o.size() > 0);
    }

    @Override
    public String urlDeDescarga(String clave, Duration validez) {
        return firmador.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(validez)
                        .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(clave).build())
                        .build())
                .url().toString();
    }

    @Override
    public String urlDeSubida(String clave, String tipoContenido, Duration validez) {
        return firmador.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(validez)
                        .putObjectRequest(PutObjectRequest.builder()
                                .bucket(bucket).key(clave).contentType(tipoContenido).build())
                        .build())
                .url().toString();
    }
}
