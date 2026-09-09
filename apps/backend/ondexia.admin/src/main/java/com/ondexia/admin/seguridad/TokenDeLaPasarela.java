package com.ondexia.admin.seguridad;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.text.ParseException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifica el token del panel: firma incluida.
 *
 * <h2>Lo que había aquí, y por qué era la falla A2</h2>
 *
 * <p>Este decodificador NO comprobaba la firma. Aceptaba cualquier token bien
 * formado —incluido uno con {@code alg: none}— y se limitaba a mirar emisor,
 * caducidad y cliente. La premisa escrita era esta: «la pasarela es la única
 * puerta; el permiso de invocación está atado al ARN de esa API, no hay otra
 * puerta».
 *
 * <p>La premisa era falsa cuando se escribió. La ruta {@code OPTIONS /{proxy+}}
 * de esa misma API <strong>no lleva autorizador</strong> y apunta a esta misma
 * función: ya existía una puerta sin cerradura. Que hoy no se llegue a ejecutar
 * ningún controlador es una casualidad de que todos declaren método —{@code GET}
 * o {@code PUT}—; un {@code @RequestMapping} sin método bastaría para convertir
 * eso en toma total de una consola que ve las cuentas de todos los clientes.
 *
 * <p>Y había una prueba, {@code unaFirmaQueNadieVerificaPasa}, que fijaba la
 * excepción en vez de la premisa: dejaba constancia de que una firma falsa
 * pasaba, en lugar de comprobar lo que la premisa afirmaba. Está invertida.
 *
 * <h2>Cómo se verifica sin salir a internet</h2>
 *
 * <p>El obstáculo era real: esta Lambda vive en subred privada sin NAT, y el
 * decodificador que Spring construye desde {@code issuer-uri} descarga la
 * configuración del emisor al primer token. Esa descarga se agotaba, el contexto
 * no levantaba y la pasarela devolvía 502:
 *
 * <pre>
 *   Unable to resolve the Configuration with the provided Issuer of
 *   "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXXXXXXX"
 *   Caused by: java.net.SocketTimeoutException: Connect timed out
 * </pre>
 *
 * <p>La salida no era elegir entre pagar un endpoint de interfaz y no verificar:
 * las claves públicas las descarga <strong>Terraform</strong> al aplicar
 * —{@code data.http.jwks_personal}— y llegan en {@code COGNITO_JWKS}. La fuente
 * es inmutable, así que en ejecución no hay red, ni caché, ni espera que agotar.
 *
 * <p>Se descartó copiar SnapStart de {@code ondexia.api}, que es lo que hacía que
 * allí la descarga funcionara: al crear la instantánea la función todavía no está
 * enganchada a la red privada, así que sale a internet y el JWKS queda dentro de
 * la foto. Sale gratis, pero apoya la validación en un detalle de plataforma que
 * AWS no ha prometido. Con el JWKS inyectado, ni la API depende ya de eso.
 *
 * <p>Sobre la rotación: Cognito no rota las claves de un grupo por su cuenta. Si
 * lo hiciera, el síntoma sería un 401 en toda petición y se arregla volviendo a
 * aplicar.
 *
 * @see TokenDeLaPasarelaTest la prueba que ejercita esto
 */
public class TokenDeLaPasarela implements JwtDecoder {

    private final NimbusJwtDecoder decodificador;

    /**
     * @param emisor  el grupo de PERSONAL, no el de inquilinos
     * @param cliente el cliente de Cognito del panel, tal como llega en
     *                {@code client_id} (token de acceso) o {@code aud} (token de
     *                identidad)
     * @param jwks    las claves públicas del grupo, en JSON. Las inyecta
     *                Terraform; sin ellas no hay nada que verificar y el
     *                constructor falla
     */
    public TokenDeLaPasarela(String emisor, String cliente, String jwks) {
        JWKSet claves;
        try {
            claves = JWKSet.parse(jwks);
        } catch (ParseException e) {
            /*
             * Falla al arrancar, no en la primera peticion. Una consola que ve
             * las cuentas de todos los clientes no debe levantarse sin saber
             * verificar firmas: es preferible que el despliegue se detenga.
             */
            throw new IllegalStateException(
                    "COGNITO_JWKS no es un juego de claves JSON valido. Lo inyecta "
                            + "Terraform desde data.http.jwks_personal.", e);
        }
        if (claves.getKeys().isEmpty()) {
            throw new IllegalStateException("COGNITO_JWKS no trae ninguna clave.");
        }

        /*
         * RS256 y solo RS256, que es con lo que firma Cognito. Fijar el algoritmo
         * cierra la confusion de algoritmo: `alg: none` —que antes pasaba— y un
         * HS256 firmado con la clave publica RSA como si fuera secreto
         * compartido. Aceptar lo que diga la cabecera es dejar que quien ataca
         * elija como se le verifica.
         */
        var procesador = new DefaultJWTProcessor<SecurityContext>();
        procesador.setJWSKeySelector(
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, new ImmutableJWKSet<>(claves)));
        // Las reclamaciones las miran los validadores de abajo. Tenerlas en dos
        // sitios significa que un dia solo se actualiza uno.
        procesador.setJWTClaimsSetVerifier((reclamaciones, contexto) -> { });

        this.decodificador = new NimbusJwtDecoder(procesador);
        this.decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(emisor),
                new JwtTimestampValidator(),
                new ExigeCaducidad(),
                new ValidadorDeCliente(cliente)));
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        return decodificador.decode(token);
    }

    /**
     * {@code exp} obligatorio.
     *
     * <p>{@link JwtTimestampValidator} acepta un token SIN caducidad: comprueba
     * que no esté vencida si existe. Un token sin ella es una sesión que no
     * termina nunca, y en esta consola eso es peor que en ninguna otra parte.
     */
    private record ExigeCaducidad() implements OAuth2TokenValidator<Jwt> {

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            return token.getExpiresAt() != null
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                            "invalid_token", "El token no declara caducidad", null));
        }
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
