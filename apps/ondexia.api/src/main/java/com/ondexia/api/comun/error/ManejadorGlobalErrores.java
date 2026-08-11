package com.ondexia.api.comun.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce excepciones a respuestas HTTP, en un solo formato.
 *
 * <p>El formato es {@code application/problem+json} (RFC 9457), que Spring
 * modela con {@link ProblemDetail}. Se usa el estandar y no un envoltorio
 * propio por una razon practica: el cliente Angular se genera desde el
 * contrato OpenAPI, y un tipo estandar lo entienden los generadores sin
 * configuracion.
 *
 * <h2>Que se cuenta y que no</h2>
 *
 * Los errores previstos —los que heredan de {@link ExcepcionAplicacion}—
 * exponen su mensaje: estan escritos para que alguien los lea y sepa que
 * corregir.
 *
 * <p>Cualquier otra excepcion es un defecto. Sale un 500 con un texto generico
 * y un <strong>identificador de incidencia</strong>; el detalle completo va al
 * log. La razon no es estetica: los mensajes de excepcion de Java suelen
 * contener nombres de tabla, fragmentos de SQL y a veces valores de datos, y
 * todo eso en una respuesta HTTP es reconocimiento gratuito para quien este
 * probando el sistema. El identificador permite que el usuario diga «me salio
 * el error 7f3a» y que eso baste para encontrarlo en CloudWatch.
 */
@RestControllerAdvice
public class ManejadorGlobalErrores extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    private static final String BASE_TIPO = "https://ondexia.com/errores/";

    @ExceptionHandler(ExcepcionAplicacion.class)
    public ProblemDetail manejarAplicacion(ExcepcionAplicacion error, HttpServletRequest peticion) {
        // Nivel WARN y no ERROR: es un error previsto. Registrarlo como ERROR
        // llena el panel de alertas de cosas que funcionan como deben, y el dia
        // que haya un error de verdad no se distingue.
        LOG.warn("{} en {} {}: {}", error.getCodigo(), peticion.getMethod(),
                peticion.getRequestURI(), error.getMessage());

        return construir(error.getEstado(), error.getCodigo(), error.getMessage(), peticion);
    }

    /**
     * Denegacion lanzada por {@code @PreAuthorize}, antes de entrar al metodo.
     *
     * <p>Se traduce al mismo cuerpo que {@link AccesoDenegadoException} para que
     * el cliente no tenga que distinguir de donde vino la negativa. El mensaje
     * es deliberadamente vago: decir que permiso concreto falta le describe a
     * quien sondea la estructura interna de la autorizacion.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail manejarDenegacion(AccessDeniedException error,
            HttpServletRequest peticion) {
        LOG.warn("acceso_denegado en {} {}", peticion.getMethod(), peticion.getRequestURI());
        return construir(HttpStatus.FORBIDDEN, "acceso_denegado",
                "No tienes permiso para realizar esta accion", peticion);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarInesperado(Exception error, HttpServletRequest peticion) {
        String incidencia = UUID.randomUUID().toString().substring(0, 8);

        LOG.error("Error no controlado [{}] en {} {}", incidencia, peticion.getMethod(),
                peticion.getRequestURI(), error);

        ProblemDetail problema = construir(HttpStatus.INTERNAL_SERVER_ERROR, "error_interno",
                "Ocurrio un error inesperado. Cita la incidencia " + incidencia
                        + " si necesitas reportarlo.",
                peticion);
        problema.setProperty("incidencia", incidencia);
        return problema;
    }

    /**
     * Errores de validacion de Bean Validation.
     *
     * <p>Se devuelven <strong>todos</strong> los campos invalidos a la vez, no
     * el primero. Un formulario de factura tiene decenas de campos, y
     * corregirlos de uno en uno con un viaje al servidor por cada uno es una
     * experiencia que nadie tolera. El frontend necesita el mapa completo para
     * marcar cada control.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException error, HttpHeaders cabeceras, HttpStatusCode estado,
            WebRequest peticion) {

        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError fallo : error.getBindingResult().getFieldErrors()) {
            campos.merge(fallo.getField(), mensajeDe(fallo), (a, b) -> a + "; " + b);
        }
        error.getBindingResult().getGlobalErrors().forEach(
                fallo -> campos.merge("_", mensajeDe(fallo.getDefaultMessage()), (a, b) -> a + "; " + b));

        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problema.setType(URI.create(BASE_TIPO + "validacion"));
        problema.setTitle("Datos invalidos");
        problema.setDetail("Revisa los campos senalados");
        problema.setProperty("codigo", "validacion");
        problema.setProperty("campos", campos);
        problema.setProperty("momento", Instant.now().toString());

        return ResponseEntity.badRequest().body(problema);
    }

    private String mensajeDe(FieldError fallo) {
        return mensajeDe(fallo.getDefaultMessage());
    }

    private String mensajeDe(String mensaje) {
        return mensaje == null ? "Valor invalido" : mensaje;
    }

    private ProblemDetail construir(HttpStatus estado, String codigo, String detalle,
            HttpServletRequest peticion) {
        ProblemDetail problema = ProblemDetail.forStatus(estado);
        problema.setType(URI.create(BASE_TIPO + codigo));
        problema.setTitle(estado.getReasonPhrase());
        problema.setDetail(detalle);
        problema.setInstance(URI.create(peticion.getRequestURI()));
        problema.setProperty("codigo", codigo);
        problema.setProperty("momento", Instant.now().toString());
        return problema;
    }
}
