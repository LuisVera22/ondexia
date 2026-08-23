package com.ondexia.infrastructure.salida.consultas;

import com.fasterxml.jackson.databind.JsonNode;
import com.ondexia.domain.consultas.ConsultaNoDisponible;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Convierte lo que puede salir mal de una llamada HTTP en el vocabulario del
 * dominio.
 *
 * <h2>Qué se decide aquí</h2>
 *
 * <p>Las tres respuestas posibles, y su diferencia importa:
 *
 * <ul>
 *   <li><strong>Datos</strong> — el padrón conoce el RUC.
 *   <li><strong>Vacío</strong> — el padrón no lo conoce. Es una respuesta: el
 *       número está mal.
 *   <li><strong>{@link ConsultaNoDisponible}</strong> — no se pudo preguntar. El
 *       RUC puede estar perfectamente bien.
 * </ul>
 *
 * <h2>Qué hace reintentable a un fallo</h2>
 *
 * <p>Un 401 o un 403 significan que la clave está mal: reintentar da el mismo
 * error y hace esperar en balde a quien se está registrando. Un 429 o un 5xx, o
 * un tiempo agotado, son pasajeros. La distinción no es un detalle de eficiencia
 * —decide si la cascada espera o pasa al siguiente proveedor— y por eso el
 * dominio la nombra.
 *
 * <p>Lo desconocido cuenta como <strong>no</strong> reintentable. Suponer lo
 * contrario es lo que produce la espera inútil.
 */
final class LlamadaDeConsulta {

    private static final Logger LOG = LoggerFactory.getLogger(LlamadaDeConsulta.class);

    private LlamadaDeConsulta() {
    }

    static Optional<JsonNode> ejecutar(String proveedor, Supplier<JsonNode> llamada) {
        try {
            JsonNode cuerpo = llamada.get();
            if (cuerpo == null || cuerpo.isEmpty()) {
                return Optional.empty();
            }
            return sinExito(cuerpo) ? Optional.empty() : Optional.of(cuerpo);

        } catch (RestClientResponseException error) {
            // El 404 y el 422 son la forma en que estos proveedores dicen «ese
            // RUC no existe». No es un fallo: es la respuesta.
            int codigo = error.getStatusCode().value();
            if (codigo == HttpStatus.NOT_FOUND.value()
                    || codigo == HttpStatus.UNPROCESSABLE_CONTENT.value()) {
                return Optional.empty();
            }
            throw traducir(proveedor, error, codigo);

        } catch (ResourceAccessException red) {
            // Tiempo agotado o no se pudo abrir la conexion. Pasajero por
            // naturaleza.
            LOG.warn("La consulta a {} no llegó: {}", proveedor, red.getMessage());
            throw new ConsultaNoDisponible(
                    "consulta_sin_respuesta",
                    "No se pudo contactar con el servicio de consulta de RUC.",
                    true, red);
        }
    }

    /**
     * Un {@code success: false} con 200, que es como apiperu.dev responde a lo
     * que no puede resolver.
     *
     * <p>Sin distinguirlo, la respuesta negativa se tomaría por un dato válido y
     * el resultado sería una empresa con la razón social vacía.
     *
     * <p>Pero «no pudo» son dos cosas, y el propio proveedor las separa con
     * {@code retryable}: un RUC que no existe es una respuesta —vacío—, y un
     * fallo suyo no lo es. Es el único de los tres que nos lo dice sin que haya
     * que inferirlo del código HTTP, así que se le hace caso.
     *
     * @return cierto si es la respuesta «no existe»; lanza si es un fallo suyo
     */
    private static boolean sinExito(JsonNode cuerpo) {
        JsonNode exito = cuerpo.path("success");
        if (!exito.isBoolean() || exito.asBoolean()) {
            return false;
        }

        JsonNode reintentable = cuerpo.path("retryable");
        if (reintentable.isBoolean() && reintentable.asBoolean()) {
            throw new ConsultaNoDisponible(
                    "consulta_no_disponible",
                    "El servicio de consulta de RUC no está disponible ahora mismo.",
                    true);
        }
        return true;
    }

    private static ConsultaNoDisponible traducir(String proveedor,
            RestClientResponseException error, int codigo) {

        boolean reintentable = codigo == HttpStatus.TOO_MANY_REQUESTS.value()
                || error.getStatusCode().is5xxServerError();

        // El codigo va al registro; el mensaje al usuario no lo lleva. A quien
        // registra una empresa no le sirve «429» y a nosotros si.
        //
        // Y no se registra el cuerpo de la respuesta: un 401 de estos servicios
        // suele repetir la clave enviada.
        LOG.warn("La consulta a {} respondió {} (reintentable: {})",
                proveedor, codigo, reintentable);

        String mensaje = codigo == HttpStatus.UNAUTHORIZED.value()
                || codigo == HttpStatus.FORBIDDEN.value()
                ? "El servicio de consulta de RUC rechazó nuestras credenciales."
                : "El servicio de consulta de RUC no está disponible ahora mismo.";

        return new ConsultaNoDisponible("consulta_no_disponible", mensaje, reintentable, error);
    }
}
