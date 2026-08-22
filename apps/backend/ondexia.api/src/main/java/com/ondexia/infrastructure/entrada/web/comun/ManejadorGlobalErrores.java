package com.ondexia.infrastructure.entrada.web.comun;

import com.ondexia.domain.comun.error.AccesoDenegado;
import com.ondexia.domain.comun.error.Conflicto;
import com.ondexia.domain.comun.error.ErrorDeDominio;
import com.ondexia.domain.comun.error.NoAutenticado;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
 * <h2>Aquí es donde el error de dominio se convierte en un código HTTP</h2>
 *
 * Las excepciones del dominio <strong>no llevan estado HTTP dentro</strong>, y
 * es deliberado: {@code ondexia-facturacion} consume de una cola y un
 * {@code HttpStatus} no significaría nada allí. Cada adaptador decide cómo se
 * representa el error en su medio, y este es el mapa de la capa web.
 *
 * <p>El formato es {@code application/problem+json} (RFC 9457). Se usa el
 * estándar y no un envoltorio propio porque el cliente Angular se genera del
 * contrato, y un tipo estándar lo entienden los generadores sin configuración.
 *
 * <h2>Qué se cuenta y qué no</h2>
 *
 * Los errores previstos exponen su mensaje: están escritos para que alguien los
 * lea. Cualquier otra excepción es un defecto: sale un 500 genérico con un
 * <strong>identificador de incidencia</strong>, y el detalle va al log. Los
 * mensajes de excepción de Java suelen llevar nombres de tabla y fragmentos de
 * SQL, y eso en una respuesta HTTP es reconocimiento gratuito.
 */
@RestControllerAdvice
public class ManejadorGlobalErrores extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    private static final String BASE_TIPO = "https://ondexia.com/errores/";

    /** El mapa de dominio a HTTP. Vive aquí y en ningún otro sitio. */
    private static HttpStatus estadoDe(ErrorDeDominio error) {
        if (error instanceof NoAutenticado) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (error instanceof AccesoDenegado) {
            return HttpStatus.FORBIDDEN;
        }
        if (error instanceof RecursoNoEncontrado) {
            return HttpStatus.NOT_FOUND;
        }
        if (error instanceof Conflicto) {
            return HttpStatus.CONFLICT;
        }
        // ReglaDeNegocioViolada y cualquier otra: la petición pedía algo que las
        // reglas no permiten.
        return HttpStatus.BAD_REQUEST;
    }

    @ExceptionHandler(ErrorDeDominio.class)
    public ProblemDetail manejarDominio(ErrorDeDominio error, HttpServletRequest peticion) {
        // WARN y no ERROR: es un error previsto. Registrarlo como ERROR llena el
        // panel de alertas de cosas que funcionan como deben.
        LOG.warn("{} en {} {}: {}", error.getCodigo(), peticion.getMethod(),
                peticion.getRequestURI(), error.getMessage());

        ProblemDetail problema =
                construir(estadoDe(error), error.getCodigo(), error.getMessage(), peticion);

        /*
         * Un conflicto de un campo concreto viaja en `campos`, igual que los de
         * validacion. No es solo comodidad para el cliente: es lo que hace que
         * «ya existe un establecimiento con ese codigo» aparezca debajo del
         * cuadro del codigo en vez de en un aviso de la esquina, donde no dice
         * cual de los cuatro campos hay que corregir.
         */
        if (error instanceof Conflicto conflicto && conflicto.getCampo() != null) {
            problema.setProperty("campos",
                    Map.of(conflicto.getCampo(), error.getMessage()));
        }

        return problema;
    }

    /**
     * Restricciones de la base: unicidad, formato, claves foráneas, disparadores.
     *
     * <p>Sin esto, un RUC repetido produce un 500. La restricción de la base es
     * la única comprobación de unicidad sin ventana de carrera, así que
     * traducirla es aceptar que es ella quien manda.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail manejarIntegridad(DataIntegrityViolationException error,
            HttpServletRequest peticion) {
        return TraductorRestricciones.traducir(error)
                .map(conflicto -> manejarDominio(conflicto, peticion))
                .orElseGet(() -> manejarInesperado(error, peticion));
    }

    /**
     * Denegación de {@code @PreAuthorize}, antes de entrar al método. El mensaje
     * es deliberadamente vago: decir qué permiso falta describe a quien sondea la
     * estructura interna de la autorización.
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
                        + " si necesitas reportarlo.", peticion);
        problema.setProperty("incidencia", incidencia);
        return problema;
    }

    /**
     * Se devuelven <strong>todos</strong> los campos inválidos, no el primero. Un
     * formulario de factura tiene decenas de campos, y corregirlos de uno en uno
     * con un viaje al servidor por cada uno no lo tolera nadie.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException error, HttpHeaders cabeceras, HttpStatusCode estado,
            WebRequest peticion) {

        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError fallo : error.getBindingResult().getFieldErrors()) {
            campos.merge(fallo.getField(), mensajeDe(fallo.getDefaultMessage()),
                    (a, b) -> a + "; " + b);
        }

        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problema.setType(URI.create(BASE_TIPO + "validacion"));
        problema.setTitle("Datos invalidos");
        problema.setDetail("Revisa los campos senalados");
        problema.setProperty("codigo", "validacion");
        problema.setProperty("campos", campos);
        problema.setProperty("momento", Instant.now().toString());

        return ResponseEntity.badRequest().body(problema);
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
