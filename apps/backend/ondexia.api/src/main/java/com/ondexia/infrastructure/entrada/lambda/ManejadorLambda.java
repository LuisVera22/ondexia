package com.ondexia.infrastructure.entrada.lambda;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.internal.LambdaContainerHandler;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequest;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.ondexia.OndexiaApiApplication;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Adaptador de entrada para AWS Lambda (DT-D17).
 *
 * <p>Es un adaptador como cualquier otro: traduce un protocolo de entrada
 * —aquí, un evento JSON de API Gateway— a la petición HTTP que Spring MVC ya
 * sabe atender. Los controladores no se enteran de que corren en Lambda, y ese
 * es el punto: la misma aplicación arranca con {@code spring-boot:run} en
 * local, contra Tomcat, sin ninguna rama condicional.
 *
 * <h2>Por qué el handler de HTTP API v2 y no el de proxy</h2>
 *
 * <p>La infraestructura declara {@code payload_format_version = "2.0"}
 * (ondexia.infra/api.tf). Los formatos 1.0 y 2.0 no son compatibles: cambian de
 * sitio la ruta, el método y las cabeceras. Con el handler equivocado la
 * aplicación arranca bien y devuelve 404 a todo, porque recibe un evento cuyos
 * campos no encuentra. Si algún día se cambia el formato en Terraform, hay que
 * cambiar también esta línea.
 *
 * <h2>Por qué la inicialización va en un bloque estático</h2>
 *
 * <p>Lambda distingue dos fases: init, que ocurre al crear el entorno de
 * ejecución, e invoke. Lo que se hace en un bloque estático ocurre en init — y
 * es ahí donde SnapStart toma la instantánea. Levantar Spring aquí significa
 * que la instantánea contiene el contexto ya construido: los arranques en frío
 * posteriores lo restauran en vez de reconstruirlo.
 *
 * <p>Moverlo dentro de {@code handleRequest} funcionaría igual de bien en
 * pruebas y anularía SnapStart por completo en producción.
 *
 * <p>Las conexiones de base de datos son el matiz de este diseño: quedarían
 * dentro de la instantánea, y un socket no sobrevive a una restauración. De eso
 * se encarga CRaC — ver el comentario de la dependencia {@code org.crac} en el
 * pom.
 */
public class ManejadorLambda implements RequestStreamHandler {

    private static final SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse> HANDLER;

    static {
        try {
            /*
             * UTF-8 por omisión en las respuestas.
             *
             * Sin esto, el contenedor decodifica el cuerpo con ISO-8859-1
             * siempre que el Content-Type no declare charset — y
             * `application/problem+json`, que es lo que produce ProblemDetail,
             * no lo declara. El resultado es mojibake en todos los mensajes de
             * error: «La operaciÃ³n necesita una empresa activa».
             *
             * Solo se ve en Lambda. En local responde Tomcat, que sí aplica la
             * configuración de `server.servlet.encoding`, así que ni las pruebas
             * ni `spring-boot:run` lo detectan. Se descubrió leyendo un error en
             * pantalla.
             */
            LambdaContainerHandler.getContainerConfig()
                    .setDefaultContentCharset(StandardCharsets.UTF_8.name());

            HANDLER = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(
                    OndexiaApiApplication.class);
        } catch (ContainerInitializationException e) {
            // Sin contexto no hay nada que servir. Fallar aquí marca la
            // inicialización como fallida y Lambda lo reporta como error de
            // init, que es visible en CloudWatch; devolver 500 a cada petición
            // escondería la causa detrás de miles de fallos idénticos.
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
