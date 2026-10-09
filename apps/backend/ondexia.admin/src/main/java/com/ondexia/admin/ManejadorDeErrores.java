package com.ondexia.admin;

import com.ondexia.domain.comun.error.ErrorDeDominio;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce los errores del dominio a respuestas HTTP.
 *
 * <h2>Por qué existe, en vez de lanzar excepciones de Spring</h2>
 *
 * <p>La primera versión de {@code GestionDeCuentas} lanzaba
 * {@code ResponseStatusException}, que es un tipo de Spring Web. Funcionaba, y
 * tenía dos costes: el servicio no se podía probar ni reutilizar sin arrastrar
 * la capa web, y el panel hablaba un idioma distinto del resto del proyecto para
 * decir lo mismo — {@code ondexia.api} ya tiene esta jerarquía de errores y su
 * propio traductor.
 *
 * <p>Ahora los servicios lanzan errores de dominio, que no saben qué es un
 * código HTTP, y la traducción vive aquí, en la frontera, que es su sitio.
 *
 * <h2>El código del error viaja en el cuerpo</h2>
 *
 * <p>Un 400 no dice qué salió mal. El campo {@code codigo} —{@code
 * plan_inexistente}, {@code estado_invalido}— sí, y permite que la interfaz
 * reaccione a un caso concreto sin analizar el texto del mensaje, que está
 * escrito para una persona y puede cambiar.
 */
@RestControllerAdvice
class ManejadorDeErrores {

    @ExceptionHandler(RecursoNoEncontrado.class)
    ProblemDetail noEncontrado(RecursoNoEncontrado error) {
        return detalle(HttpStatus.NOT_FOUND, error);
    }

    @ExceptionHandler(ReglaDeNegocioViolada.class)
    ProblemDetail reglaViolada(ReglaDeNegocioViolada error) {
        return detalle(HttpStatus.BAD_REQUEST, error);
    }

    private static ProblemDetail detalle(HttpStatus estado, ErrorDeDominio error) {
        var problema = ProblemDetail.forStatusAndDetail(estado, error.getMessage());
        problema.setProperty("codigo", error.getCodigo());
        return problema;
    }
}
