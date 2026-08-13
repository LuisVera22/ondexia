package com.ondexia.domain.marca;

import java.time.Duration;
import java.util.Optional;

/**
 * Puerto de salida: dónde viven los archivos de marca.
 *
 * <h2>El archivo nunca pasa por nuestra API</h2>
 *
 * <p>El servidor <strong>firma</strong> una autorización de subida y el navegador
 * del usuario sube el archivo directo al almacén. Tres razones, en orden de
 * importancia:
 *
 * <ul>
 *   <li>Firmar es un cálculo local con las credenciales, <strong>sin ninguna
 *       llamada de red</strong>. Por eso esto funciona en una Lambda sin salida a
 *       internet, y por eso el plan se equivocaba al aplazarlo por «necesita
 *       URLs prefirmadas».</li>
 *   <li>Un archivo que atraviesa la API consume memoria y tiempo de función, y
 *       choca con el límite de 10 MB de la pasarela.</li>
 *   <li>La subida no depende de que nuestra API esté disponible en ese instante.</li>
 * </ul>
 *
 * <h2>Y por eso hay que comprobar después</h2>
 *
 * <p>Como el archivo no pasa por aquí, lo que el cliente <em>dijo</em> que iba a
 * subir y lo que subió pueden no coincidir. {@link #describir} existe para eso:
 * se pregunta al almacén qué llegó de verdad antes de dar la subida por buena.
 */
public interface AlmacenDeMarca {

    /**
     * Lo que el navegador necesita para subir: a dónde y hasta cuándo.
     *
     * @param url       destino de un {@code PUT} con el archivo como cuerpo
     * @param clave     identificador del objeto, que se devuelve luego al
     *                  confirmar. No se deduce de la URL a propósito: la URL
     *                  lleva firma y parámetros, y recomponerla sería frágil
     * @param validaPor margen para completar la subida
     */
    record AutorizacionDeSubida(String url, String clave, Duration validaPor) {
    }

    /** Lo que el almacén dice que recibió. La verdad, frente a lo que se prometió. */
    record ObjetoDeMarca(long bytes, String tipoContenido) {
    }

    /**
     * Firma una subida para esa clave, ese tipo y ese tamaño exactos.
     *
     * <p>El tipo va dentro de la firma, de modo que subir otra cosa invalida la
     * petición en el propio almacén: no depende de que nadie lo revise después.
     */
    AutorizacionDeSubida autorizarSubida(String clave, String tipoContenido, long bytes);

    /** Vacío si el objeto no existe — es decir, si la subida nunca ocurrió. */
    Optional<ObjetoDeMarca> describir(String clave);

    void eliminar(String clave);

    /** URL pública por CDN. Es la que se pinta en la pantalla y en los PDF. */
    String urlPublica(String clave);
}
