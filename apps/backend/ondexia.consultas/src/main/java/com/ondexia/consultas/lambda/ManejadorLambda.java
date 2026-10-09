package com.ondexia.consultas.lambda;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.internal.LambdaContainerHandler;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequest;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.ondexia.consultas.ConsultasApplication;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Adaptador de entrada para AWS Lambda, igual que el de {@code ondexia.api}.
 *
 * <p>Traduce el evento JSON de API Gateway a la petición HTTP que Spring MVC ya
 * sabe atender. El controlador no se entera de que corre en Lambda, y ese es el
 * punto: la misma aplicación arranca contra Tomcat en local sin ninguna rama.
 *
 * <h2>Handler de HTTP API v2, no de proxy</h2>
 *
 * <p>La infraestructura declara {@code payload_format_version = "2.0"}
 * (ondexia.infra/consultas.tf). Los formatos 1.0 y 2.0 no son compatibles:
 * cambian de sitio la ruta, el método y las cabeceras. Con el handler
 * equivocado la aplicación arranca bien y devuelve 404 a todo.
 *
 * <h2>La inicialización va en un bloque estático</h2>
 *
 * <p>Lambda distingue init de invoke, y SnapStart toma la instantánea al final
 * de init. Levantar Spring aquí significa que la instantánea contiene el
 * contexto ya construido. Moverlo dentro de {@code handleRequest} funcionaría
 * igual en pruebas y anularía SnapStart por completo en producción.
 *
 * <h2>Qué NO hay que vigilar aquí, y por qué</h2>
 *
 * <p>La tabla de trampas de SnapStart (DTE §4.2) no aplica igual que en la API:
 *
 * <ul>
 *   <li><strong>Sin pool de conexiones</strong>: esta función no ve la base de
 *       datos, así que no hay sockets que no sobrevivan al restore.
 *   <li><strong>Sin {@code SecureRandom} que reservar.</strong> Es la parte que
 *       parecería peligrosa en algo que firma, y no lo es: Ed25519 produce firmas
 *       <em>deterministas</em> (RFC 8032), sin aleatoriedad. Una semilla clonada
 *       entre instancias no puede repetir un nonce porque no hay nonce.
 *   <li><strong>La clave privada sí queda en la instantánea.</strong> Es la fila
 *       del certificado {@code .pfx} de §4.2, con una diferencia: esa es material
 *       de un cliente y esta es nuestra, una sola, rotable a mano. Se lee en el
 *       arranque a propósito, para que una clave mal puesta falle al desplegar y
 *       no en el primer alta que intente alguien.
 * </ul>
 */
public class ManejadorLambda implements RequestStreamHandler {

    private static final SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse>
            HANDLER;

    static {
        try {
            /*
             * UTF-8 por omision en las respuestas.
             *
             * Sin esto el contenedor decodifica el cuerpo con ISO-8859-1 cuando
             * el Content-Type no declara charset, y el resultado es mojibake en
             * cada mensaje de error. Solo se ve en Lambda: en local responde
             * Tomcat, que si aplica server.servlet.encoding, asi que ni las
             * pruebas ni spring-boot:run lo detectan. Se descubrio leyendo un
             * error en pantalla en la API.
             */
            LambdaContainerHandler.getContainerConfig()
                    .setDefaultContentCharset(StandardCharsets.UTF_8.name());

            HANDLER = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(
                    ConsultasApplication.class);
        } catch (ContainerInitializationException noArranco) {
            // Fallar aqui marca la inicializacion como fallida y Lambda lo
            // reporta como error de init, visible en CloudWatch. Devolver 500 a
            // cada peticion esconderia la causa detras de miles de fallos
            // identicos.
            throw new IllegalStateException(
                    "No se pudo inicializar el contexto de Spring en Lambda", noArranco);
        }
    }

    @Override
    public void handleRequest(InputStream entrada, OutputStream salida, Context contexto)
            throws IOException {
        HANDLER.proxyStream(entrada, salida, contexto);
    }
}
