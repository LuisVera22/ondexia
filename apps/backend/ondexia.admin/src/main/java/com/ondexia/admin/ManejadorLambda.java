package com.ondexia.admin;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.internal.LambdaContainerHandler;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequest;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Punto de entrada del panel en Lambda.
 *
 * <p>Gemelo del de {@code ondexia.api}, con dos diferencias que importan: arranca
 * {@link AplicacionAdmin} —no la aplicación de clientes— y <strong>no lleva
 * SnapStart</strong>. La consola la usan dos o tres personas al día; un arranque
 * en frío de segundos es tolerable, y a cambio se evita lo que más ha costado en
 * los despliegues de la API: publicar una versión con instantánea tarda minutos y
 * convierte cualquier fallo de arranque en una versión en {@code Failed}.
 */
public class ManejadorLambda implements RequestStreamHandler {

    private static final SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse>
            HANDLER;

    static {
        try {
            /*
             * UTF-8 por omision en las respuestas. Sin esto el contenedor
             * decodifica el cuerpo con ISO-8859-1 cuando el Content-Type no
             * declara charset —y application/problem+json no lo declara—, y los
             * mensajes de error salen con mojibake. Solo se ve en Lambda: en
             * local responde Tomcat, que si aplica server.servlet.encoding.
             */
            LambdaContainerHandler.getContainerConfig()
                    .setDefaultContentCharset(StandardCharsets.UTF_8.name());

            HANDLER = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(
                    AplicacionAdmin.class);
        } catch (ContainerInitializationException e) {
            // Fallar aqui marca la inicializacion como fallida y Lambda lo
            // reporta en CloudWatch. Devolver 500 a cada peticion esconderia la
            // causa detras de miles de fallos identicos.
            throw new IllegalStateException(
                    "No se pudo inicializar el contexto de Spring en Lambda", e);
        }
    }

    @Override
    public void handleRequest(InputStream entrada, OutputStream salida, Context contexto)
            throws IOException {
        HANDLER.proxyStream(entrada, salida, contexto);
    }
}
