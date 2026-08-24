package com.ondexia.consultas.web;

import com.ondexia.domain.comun.error.ErrorDeDominio;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.consultas.ConsultaNoDisponible;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce los errores del dominio a respuestas HTTP.
 *
 * <h2>El cuerpo lleva `reintentable`, y no es decoración</h2>
 *
 * <p>Una clave caducada del proveedor y una caída pasajera suya son el mismo 503
 * y no llevan a la misma sugerencia. Sin este campo, el cliente tiene que
 * <strong>adivinar</strong> por el código si merece la pena ofrecer «volver a
 * intentarlo», y las dos formas de adivinar mal cuestan: ofrecerlo cuando no
 * sirve hace perder el tiempo, no ofrecerlo cuando sí manda a soporte por un
 * minuto de caída.
 *
 * <p>Es el {@code retryable} que apiperu.dev devuelve en su respuesta, elevado a
 * concepto del dominio y devuelto aquí tal cual.
 *
 * <h2>Por qué no se usa `ProblemDetail` como en la API</h2>
 *
 * <p>Porque el consumidor de esto no es el mismo. La API la lee el SPA a través
 * de un cliente que ya sabe leer {@code ProblemDetail}; esta ruta la lee un
 * servicio pequeño y propio, y un cuerpo de tres campos —código, mensaje,
 * reintentable— se consume sin ninguna ceremonia. Si algún día lo consumen más
 * clientes, merecerá la pena unificarlo.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorDeErrores.class);

    /** Tres campos y nada más. Ver la cabecera. */
    public record Fallo(String codigo, String mensaje, boolean reintentable) {
    }

    /**
     * El padrón no conoce el RUC. Es una <em>respuesta</em>: el número está mal.
     *
     * <p>No reintentable, y la distinción con el 503 es la mitad del valor de esta
     * clase: confundirlas hace que la aplicación diga «ese RUC no existe» a
     * alguien cuyo RUC existe, y esa persona intentará corregir un número
     * correcto.
     */
    @ExceptionHandler(RecursoNoEncontrado.class)
    public ResponseEntity<Fallo> noEncontrado(RecursoNoEncontrado error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new Fallo(error.getCodigo(), error.getMessage(), false));
    }

    /**
     * No se pudo preguntar. El RUC puede estar perfectamente bien.
     *
     * <p>503 y no 500: no es un fallo de esta función, y el cliente puede querer
     * reintentar.
     */
    @ExceptionHandler(ConsultaNoDisponible.class)
    public ResponseEntity<Fallo> noDisponible(ConsultaNoDisponible error) {
        LOG.warn("Consulta no disponible ({}, reintentable: {})",
                error.getCodigo(), error.esReintentable());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new Fallo(error.getCodigo(), error.getMessage(), error.esReintentable()));
    }

    /**
     * Un RUC con el dígito verificador mal, sobre todo.
     *
     * <p>Se responde sin salir a la red, que es el punto: la errata de tecleo es
     * el error más frecuente y no hay razón para gastar una consulta de un plan
     * de pago en ella.
     */
    @ExceptionHandler(ErrorDeDominio.class)
    public ResponseEntity<Fallo> reglaDeNegocio(ErrorDeDominio error) {
        return ResponseEntity.badRequest()
                .body(new Fallo(error.getCodigo(), error.getMessage(), false));
    }

    /** El RUC no tiene once dígitos: lo rechaza la anotación del controlador. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Fallo> formato(ConstraintViolationException error) {
        return ResponseEntity.badRequest()
                .body(new Fallo("ruc_invalido", "El RUC son once dígitos.", false));
    }
}
