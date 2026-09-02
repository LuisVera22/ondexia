package com.ondexia.consultas.web;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import tools.jackson.databind.json.JsonMapper;

/**
 * Quién pide la consulta: el {@code sub} del token que trae la petición.
 *
 * <h2>Se lee sin verificar la firma, y por qué eso está bien aquí</h2>
 *
 * <p>Este módulo no tiene Spring Security ni el JWKS: la autenticación la hace el
 * autorizador de API Gateway, que verifica el token ANTES de invocar la función
 * y rechaza con 401 lo que no cuadre. Aquí solo se lee el {@code sub} de un
 * token que ya pasó por ahí.
 *
 * <p>Y aunque alguien lograra llegar con un token inventado, no ganaría nada: el
 * {@code sub} se hornea en la atestación, y es la <em>API</em> —que sí verifica
 * la firma del JWT con el JWKS— la que exige que coincida con el de quien la
 * presenta. Una atestación emitida para un {@code sub} falso solo la puede usar
 * quien tenga un token válido con ese {@code sub}, que es justo quien no lo
 * tiene. La propiedad de seguridad la sostiene la API; esto solo transporta el
 * dato (hallazgo M17).
 */
final class Solicitante {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Solicitante() {
    }

    static String de(String autorizacion) {
        if (autorizacion == null || !autorizacion.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new SolicitanteDesconocido("La consulta necesita un token de acceso.");
        }
        String[] partes = autorizacion.substring(7).trim().split("\\.");
        if (partes.length < 2) {
            throw new SolicitanteDesconocido("El token de acceso no tiene forma de JWT.");
        }

        String sub;
        try {
            byte[] cuerpo = Base64.getUrlDecoder().decode(partes[1]);
            sub = JSON.readTree(new String(cuerpo, StandardCharsets.UTF_8)).path("sub").asString();
        } catch (RuntimeException noSeLee) {
            throw new SolicitanteDesconocido("El token de acceso no se pudo leer.");
        }

        if (sub == null || sub.isBlank()) {
            throw new SolicitanteDesconocido("El token de acceso no dice quién es.");
        }
        return sub;
    }

    /** Sin solicitante identificable: 401, no 400. */
    static final class SolicitanteDesconocido extends RuntimeException {

        SolicitanteDesconocido(String mensaje) {
            super(mensaje);
        }
    }
}
