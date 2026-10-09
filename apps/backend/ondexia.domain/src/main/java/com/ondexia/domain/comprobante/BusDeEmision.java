package com.ondexia.domain.comprobante;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto: el bus entre la API y el Emisor (doc 14 §2).
 *
 * <p>En la plataforma es un bucket de S3: la API escribe la orden en
 * {@code pendientes/}, el evento invoca al Emisor, y el Emisor deja el XML, el
 * CDR y el resultado en {@code resultados/}. En local y en pruebas es un mapa en
 * memoria. El caso de uso no sabe cuál de los dos tiene delante, que es lo que
 * permite probar la emisión sin AWS.
 *
 * <h2>El resultado se lee, no se recibe</h2>
 *
 * <p>La API no se entera del resultado por un evento: lo busca cuando alguien
 * pregunta por el comprobante ({@link #resultadoDe}). Hubo una segunda Lambda
 * de la API suscrita a {@code resultados/} en el diseño; se retiró porque
 * obligaba a levantar el contexto de Spring de la API en un handler sin HTTP
 * —sin forma de probarlo desde aquí— para ganar unos segundos que la pantalla
 * cubre consultando mientras está en cola. Cuando la iteración 6 traiga el
 * planificador del resumen diario, la misma tarea sincronizará lo que quede en
 * cola sin que nadie lo mire.
 */
public interface BusDeEmision {

    /** Deja la orden donde el Emisor la va a recoger. */
    void publicar(OrdenDeEmision orden);

    /** El resultado que el Emisor dejó para esa orden, si ya lo dejó. */
    Optional<ResultadoDeEmision> resultadoDe(UUID empresaId, UUID ordenId);

    /**
     * Si el objeto existe. Para confirmar que el {@code .pfx} y las credenciales
     * llegaron al bucket antes de dar por configurada la emisión. Se responde
     * con un listado por prefijo, no leyendo el objeto: la API no tiene lectura
     * sobre {@code certificados/} ni {@code credenciales/}.
     */
    boolean existe(String clave);

    /**
     * URL temporal para descargar un objeto que dejó el Emisor: el XML firmado o
     * el CDR. La API no sirve el archivo; firma la URL y el navegador lo baja de
     * S3, igual que los logos de marca.
     */
    String urlDeDescarga(String clave, Duration validez);

    /** URL prefirmada para que el navegador suba un objeto con ese tipo, sin pasar por la API. */
    String urlDeSubida(String clave, String tipoContenido, Duration validez);
}
