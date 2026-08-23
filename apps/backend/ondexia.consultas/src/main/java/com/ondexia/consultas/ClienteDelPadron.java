package com.ondexia.consultas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondexia.domain.consultas.ConsultaNoDisponible;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Una llamada HTTP a un proveedor, traducida al vocabulario del dominio.
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
final class ClienteDelPadron {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final Duration tiempoDeEspera;
    private final String nombre;
    private final String token;

    ClienteDelPadron(String nombre, String token, Duration tiempoDeEspera) {
        this.nombre = nombre;
        this.token = token;
        this.tiempoDeEspera = tiempoDeEspera;
        // Sin seguir redirecciones: un proveedor que redirige a otro sitio no es
        // algo que queramos atender en silencio con la clave en la cabecera.
        this.http = HttpClient.newBuilder()
                .connectTimeout(tiempoDeEspera)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    Optional<JsonNode> get(URI uri) {
        return enviar(peticion(uri).GET().build());
    }

    Optional<JsonNode> post(URI uri, String cuerpo) {
        return enviar(peticion(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build());
    }

    private HttpRequest.Builder peticion(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(tiempoDeEspera)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
    }

    private Optional<JsonNode> enviar(HttpRequest peticion) {
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException noLlego) {
            // Tiempo agotado o conexion imposible. Pasajero por naturaleza.
            registrar("no llegó: " + noLlego.getMessage(), true);
            throw new ConsultaNoDisponible("consulta_sin_respuesta",
                    "No se pudo contactar con el servicio de consulta de RUC.", true, noLlego);
        } catch (InterruptedException interrumpido) {
            // Se restaura la marca: tragarsela deja el hilo sin saber que le
            // pidieron parar, y en Lambda eso significa seguir trabajando
            // mientras el entorno se apaga.
            Thread.currentThread().interrupt();
            throw new ConsultaNoDisponible("consulta_interrumpida",
                    "La consulta del RUC se interrumpió.", true, interrumpido);
        }

        int codigo = respuesta.statusCode();

        // El 404 y el 422 son como estos proveedores dicen «ese RUC no existe».
        if (codigo == 404 || codigo == 422) {
            return Optional.empty();
        }
        if (codigo < 200 || codigo >= 300) {
            throw fallo(codigo);
        }

        JsonNode cuerpo;
        try {
            cuerpo = JSON.readTree(respuesta.body());
        } catch (IOException noEsJson) {
            registrar("respondió algo que no es JSON", false);
            throw new ConsultaNoDisponible("consulta_respuesta_ilegible",
                    "El servicio de consulta de RUC respondió de forma inesperada.",
                    false, noEsJson);
        }

        if (cuerpo == null || cuerpo.isEmpty()) {
            return Optional.empty();
        }
        return sinExito(cuerpo) ? Optional.empty() : Optional.of(cuerpo);
    }

    /**
     * Un {@code success: false} con 200, que es como responde apiperu.dev.
     *
     * <p>Sin distinguirlo, la negativa se tomaría por un dato válido y saldría
     * una empresa con la razón social vacía.
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
            registrar("declaró un fallo reintentable", true);
            throw new ConsultaNoDisponible("consulta_no_disponible",
                    "El servicio de consulta de RUC no está disponible ahora mismo.", true);
        }
        return true;
    }

    private ConsultaNoDisponible fallo(int codigo) {
        boolean reintentable = codigo == 429 || codigo >= 500;
        registrar("respondió " + codigo, reintentable);

        String mensaje = codigo == 401 || codigo == 403
                ? "El servicio de consulta de RUC rechazó nuestras credenciales."
                : "El servicio de consulta de RUC no está disponible ahora mismo.";
        return new ConsultaNoDisponible("consulta_no_disponible", mensaje, reintentable);
    }

    /**
     * A la salida estándar, que en Lambda es CloudWatch.
     *
     * <p>Nunca el cuerpo de la respuesta: un 401 de estos servicios suele
     * repetir la clave que se envió, y CloudWatch conserva los registros.
     */
    private void registrar(String que, boolean reintentable) {
        System.out.println("[consultas] " + nombre + " " + que
                + " (reintentable: " + reintentable + ")");
    }
}
