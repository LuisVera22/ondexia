package com.ondexia.admin.seguridad;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/**
 * Lee el token que la pasarela ya validó. No comprueba la firma, y es a
 * propósito.
 *
 * <h2>El problema que resuelve</h2>
 *
 * <p>La Lambda del panel vive en una subred privada sin NAT: su grupo de
 * seguridad solo deja salir al 5432 hacia la base y al 443 hacia S3. El
 * decodificador que construye Spring a partir de {@code issuer-uri} necesita
 * descargar la configuración del emisor, y esa descarga no llega a ninguna
 * parte. El contexto no levantaba y la pasarela devolvía 502:
 *
 * <pre>
 *   Unable to resolve the Configuration with the provided Issuer of
 *   "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXXXXXXX"
 *   Caused by: java.net.SocketTimeoutException: Connect timed out
 * </pre>
 *
 * <h2>Por qué se puede confiar en la pasarela</h2>
 *
 * <p>Porque es la única puerta. La ruta {@code $default} lleva un autorizador
 * JWT que comprueba firma, emisor, audiencia y caducidad <strong>antes</strong>
 * de invocar la función, y el permiso de invocación está atado al ARN de esa API
 * — nadie más puede llamarla. Revalidar la firma aquí es repetir un trabajo ya
 * hecho, y hacerlo costaba una salida a internet que esta red no tiene.
 *
 * <p>La contrapartida, dicha con todas las letras: si algún día se añade otra
 * ruta sin autorizador, u otro disparador sobre esta misma función, este
 * decodificador aceptaría un token que nadie verificó. Por eso las
 * comprobaciones que <em>sí</em> se pueden hacer sin red se hacen igualmente —
 * emisor, caducidad y cliente—, y por eso la prueba
 * {@code unaFirmaFalsaPasa} existe: para que la decisión esté escrita en algo
 * que se ejecuta, y no solo en un comentario.
 *
 * <h2>La alternativa que se descartó</h2>
 *
 * <p>Copiar SnapStart de {@code ondexia.api}, que es lo que hace que allí esta
 * descarga funcione: al crear la instantánea la función todavía no está
 * enganchada a la red privada, así que sale a internet y el JWKS queda dentro de
 * la foto. Sale gratis, pero apoya la validación en un detalle de plataforma que
 * AWS no ha prometido, y congela las claves de Cognito hasta el siguiente
 * despliegue — si Cognito rota una, deja de validar. No se copió una fragilidad;
 * el camino es quitársela también a la API.
 */
public class TokenDeLaPasarela implements JwtDecoder {

    private final OAuth2TokenValidator<Jwt> validaciones;

    /**
     * @param emisor  el grupo de PERSONAL, no el de inquilinos
     * @param cliente el cliente de Cognito del panel, tal como llega en
     *                {@code client_id} (token de acceso) o {@code aud} (token de
     *                identidad)
     */
    public TokenDeLaPasarela(String emisor, String cliente) {
        this.validaciones = new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(emisor),
                new JwtTimestampValidator(),
                new ValidadorDeCliente(cliente));
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        Jwt leido = leer(token);

        OAuth2TokenValidatorResult resultado = validaciones.validate(leido);
        if (resultado.hasErrors()) {
            throw new JwtValidationException(
                    "El token no supera las comprobaciones del panel",
                    resultado.getErrors());
        }
        return leido;
    }

    private static Jwt leer(String token) {
        JWT analizado;
        Map<String, Object> reclamos;
        try {
            analizado = JWTParser.parse(token);
            reclamos = new HashMap<>(analizado.getJWTClaimsSet().getClaims());
        } catch (ParseException e) {
            throw new BadJwtException("El token no es un JWT legible", e);
        }

        // Nimbus devuelve las fechas como Date; Spring espera Instant, y sin
        // esta conversion getClaimAsInstant falla en cuanto alguien la use.
        reclamos.replaceAll((nombre, valor) ->
                valor instanceof Date fecha ? fecha.toInstant() : valor);

        try {
            return new Jwt(token,
                    instante(reclamos.get("iat")),
                    instante(reclamos.get("exp")),
                    analizado.getHeader().toJSONObject(),
                    reclamos);
        } catch (IllegalArgumentException e) {
            /*
             * Jwt exige, entre otras cosas, que la caducidad sea posterior a la
             * emision, y protesta con IllegalArgumentException. Esa excepcion no
             * es una JwtException, asi que el filtro de Spring Security no la
             * reconoce: se escapa de la cadena y sale un 500 —un fallo nuestro—
             * donde corresponde un 401 —un token que no sirve—. Ademas de
             * confundir a quien lea los registros, un 500 revela mas de lo que
             * deberia sobre lo que ocurre dentro.
             */
            throw new BadJwtException("El token tiene reclamos incoherentes", e);
        }
    }

    private static Instant instante(Object valor) {
        return valor instanceof Instant momento ? momento : null;
    }

    /**
     * Que el token sea de nuestro cliente y no de otro del mismo grupo.
     *
     * <p>Cognito pone {@code client_id} en los tokens de acceso y {@code aud} en
     * los de identidad. Se aceptan los dos sitios, que es exactamente lo que
     * hace el autorizador de la pasarela; comprobar solo uno rechazaría tokens
     * que la pasarela ya dio por buenos, y el fallo aparecería como un 401
     * inexplicable después de un acceso correcto.
     */
    private record ValidadorDeCliente(String esperado) implements OAuth2TokenValidator<Jwt> {

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            String cliente = token.getClaimAsString("client_id");
            if (cliente == null) {
                var audiencia = token.getAudience();
                cliente = audiencia == null || audiencia.isEmpty() ? null : audiencia.getFirst();
            }
            return esperado.equals(cliente)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                            "El token no es de este cliente de Cognito", null));
        }
    }
}
