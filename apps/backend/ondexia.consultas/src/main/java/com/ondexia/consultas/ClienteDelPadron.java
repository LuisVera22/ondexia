package com.ondexia.consultas;

import com.ondexia.domain.consultas.ConsultaNoDisponible;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Una llamada a un proveedor del padrón, traducida al vocabulario del dominio.
 *
 * <h2>Las tres respuestas, y su diferencia importa</h2>
 *
 * <ul>
 *   <li><strong>Datos</strong> — el padrón conoce el RUC.
 *   <li><strong>Vacío</strong> — no lo conoce. Es una respuesta: el número está
 *       mal escrito.
 *   <li><strong>{@link ConsultaNoDisponible}</strong> — no se pudo preguntar. El
 *       RUC puede estar perfectamente bien.
 * </ul>
 *
 * <p>Confundir las dos últimas hace que la aplicación diga «ese RUC no existe» a
 * alguien cuyo RUC existe, y esa persona intentará corregir un número correcto.
 *
 * <h2>Qué hace reintentable a un fallo</h2>
 *
 * <p>Un 401 o un 403 significan que la clave está mal: reintentar da el mismo
 * error y hace esperar en balde a quien se está registrando. Un 429 o un 5xx, o
 * un tiempo agotado, son pasajeros.
 *
 * <p>Lo desconocido cuenta como <strong>no</strong> reintentable. Suponer lo
 * contrario es lo que produce la espera inútil.
 */
class ClienteDelPadron {

    private static final Logger LOG = LoggerFactory.getLogger(ClienteDelPadron.class);

    private final String nombre;
    private final RestClient cliente;

    ClienteDelPadron(String nombre, String token, Duration tiempoDeEspera) {
        this.nombre = nombre;

        /*
         * La fabrica se construye a mano en vez de dejar que RestClient detecte
         * una: la detectada no lleva tiempos de espera, y un cliente HTTP sin
         * ellos espera indefinidamente. Con API Gateway cortando a los 29 s, eso
         * convierte un proveedor lento en un 504 opaco de la pasarela.
         *
         * Y es la fabrica simple, no la de java.net.http, aunque esa sea la
         * moderna: su cliente abre un socket de loopback al construirse, y este
         * bean se crea al arrancar. En un entorno que no lo permita, la
         * aplicacion entera no levanta por una funcion que quiza nadie use en esa
         * ejecucion. Para dos peticiones por registro, HTTP/2 y el pool de
         * conexiones no compran nada que compense ese riesgo.
         */
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(tiempoDeEspera);
        fabrica.setReadTimeout(tiempoDeEspera);

        this.cliente = RestClient.builder()
                .requestFactory(fabrica)
                /*
                 * El token va en la cabecera por omision y no en cada llamada:
                 * asi ningun adaptador puede olvidarlo, y —lo que importa mas— el
                 * valor no aparece en el codigo de las llamadas, donde acabaria
                 * en un mensaje de registro el dia que alguien depure a base de
                 * imprimir.
                 */
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    Optional<JsonNode> get(String uri) {
        return ejecutar(() -> cliente.get().uri(uri).retrieve().body(JsonNode.class));
    }

    Optional<JsonNode> post(String uri, Map<String, String> cuerpo) {
        return ejecutar(() -> cliente.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(JsonNode.class));
    }

    private Optional<JsonNode> ejecutar(Supplier<JsonNode> llamada) {
        try {
            JsonNode cuerpo = llamada.get();
            if (cuerpo == null || cuerpo.isEmpty()) {
                return Optional.empty();
            }
            return sinExito(cuerpo) ? Optional.empty() : Optional.of(cuerpo);

        } catch (RestClientResponseException error) {
            int codigo = error.getStatusCode().value();

            // El 404 y el 422 son como estos proveedores dicen «ese RUC no
            // existe». No es un fallo: es la respuesta.
            if (codigo == 404 || codigo == 422) {
                return Optional.empty();
            }
            throw fallo(codigo, error.getStatusCode().is5xxServerError(), error);

        } catch (ResourceAccessException noLlego) {
            // Tiempo agotado o conexion imposible. Pasajero por naturaleza.
            LOG.warn("La consulta a {} no llegó: {}", nombre, noLlego.getMessage());
            throw new ConsultaNoDisponible("consulta_sin_respuesta",
                    "No se pudo contactar con el servicio de consulta de RUC.", true, noLlego);
        }
    }

    /**
     * Un {@code success: false} con 200, que es como responde apiperu.dev.
     *
     * <p>Sin distinguirlo, la negativa se tomaría por un dato válido y saldría una
     * empresa con la razón social vacía.
     *
     * <p>Y «no pudo» son dos cosas, que el propio proveedor separa con
     * {@code retryable}: un RUC que no existe es una respuesta; un fallo suyo no.
     * Es el único de los tres que lo dice sin que haya que inferirlo del código
     * HTTP, así que se le hace caso.
     *
     * @return cierto si es «no existe»; lanza si es un fallo del proveedor
     */
    private boolean sinExito(JsonNode cuerpo) {
        JsonNode exito = cuerpo.path("success");
        if (!exito.isBoolean() || exito.asBoolean()) {
            return false;
        }
        JsonNode reintentable = cuerpo.path("retryable");
        if (reintentable.isBoolean() && reintentable.asBoolean()) {
            LOG.warn("{} declaró un fallo reintentable", nombre);
            throw new ConsultaNoDisponible("consulta_no_disponible",
                    "El servicio de consulta de RUC no está disponible ahora mismo.", true);
        }
        return true;
    }

    private ConsultaNoDisponible fallo(int codigo, boolean esDelServidor, Throwable causa) {
        boolean reintentable = codigo == 429 || esDelServidor;

        /*
         * El codigo va al registro; el mensaje al usuario no lo lleva. A quien
         * registra una empresa no le sirve «429» y a nosotros si.
         *
         * Y no se registra el cuerpo de la respuesta: un 401 de estos servicios
         * suele repetir la clave enviada, y CloudWatch conserva los registros.
         */
        LOG.warn("La consulta a {} respondió {} (reintentable: {})",
                nombre, codigo, reintentable);

        String mensaje = codigo == 401 || codigo == 403
                ? "El servicio de consulta de RUC rechazó nuestras credenciales."
                : "El servicio de consulta de RUC no está disponible ahora mismo.";

        return new ConsultaNoDisponible("consulta_no_disponible", mensaje, reintentable, causa);
    }
}
