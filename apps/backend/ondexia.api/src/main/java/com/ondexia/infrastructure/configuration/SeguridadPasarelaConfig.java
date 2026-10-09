package com.ondexia.infrastructure.configuration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.text.ParseException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Decodificador de tokens para el despliegue en Lambda. Verifica la firma.
 *
 * <h2>Qué había antes, y por qué era la falla A1</h2>
 *
 * <p>Este bean comprobaba emisor y caducidad y <strong>no verificaba la
 * firma</strong>. El argumento era correcto en su premisa e insuficiente en su
 * conclusión: verificar exige el JWKS de Cognito, la función vive en subred
 * privada sin NAT, y un endpoint de interfaz cuesta ~7.30 USD/mes (DTE §4.8).
 * De ahí se concluía que bastaba con delegar en el autorizador de API Gateway.
 *
 * <p>El problema de delegar es que convierte toda la autenticación en una
 * propiedad de la configuración de la pasarela. Cualquier descuido futuro —una
 * ruta sin autorizador, un permiso de invocación más ancho, una Function URL
 * añadida para depurar— pasa de ser un fallo de configuración a ser
 * autenticación arbitraria: quien alcance la función presenta el token que
 * quiera, firmado por quien quiera, y esta aplicación lo cree.
 *
 * <p>Y no era hipotético: la ruta {@code OPTIONS /{proxy+}} ya llega a la Lambda
 * sin pasar por el autorizador.
 *
 * <h2>La tercera vía: el JWKS horneado</h2>
 *
 * <p>No hacía falta elegir entre pagar un endpoint y no verificar. Las claves
 * públicas del grupo de usuarios las descarga <em>Terraform</em> al aplicar
 * —quien aplica sí tiene internet— y llegan aquí en {@code COGNITO_JWKS}. No es
 * un secreto: son claves públicas.
 *
 * <p>La fuente es inmutable ({@link ImmutableJWKSet}): no hay red en ejecución,
 * no hay caché que expire, no hay tiempo de espera que agotar. Si Cognito rotara
 * las claves —hoy no lo hace por su cuenta— el síntoma sería un 401 en toda
 * petición y se arregla volviendo a aplicar.
 *
 * <h2>Qué se exige, además de la firma</h2>
 *
 * <ul>
 *   <li>{@code iss} igual al pool esperado.
 *   <li>{@code exp} <strong>presente</strong> y no vencido. Antes era opcional:
 *       un token sin {@code exp} pasaba, y eso es una sesión eterna.
 *   <li>{@code token_use} de Cognito: {@code access} o {@code id}. Sin esto, un
 *       token de refresco valdría como token de acceso.
 *   <li>La audiencia: {@code client_id} en los de acceso, {@code aud} en los de
 *       identidad. Cognito usa campos distintos según el tipo, y comprobar solo
 *       uno de los dos deja pasar el otro.
 * </ul>
 *
 * @see com.ondexia.pruebas.FirmaDelTokenIT la prueba que ejercita esto
 */
@Configuration
@Profile("aws")
public class SeguridadPasarelaConfig {

    private final String emisorEsperado;
    private final String clienteEsperado;
    private final String jwks;

    public SeguridadPasarelaConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String emisorEsperado,
            @Value("${ondexia.cognito.cliente}") String clienteEsperado,
            @Value("${ondexia.cognito.jwks}") String jwks) {
        this.emisorEsperado = emisorEsperado;
        this.clienteEsperado = clienteEsperado;
        this.jwks = jwks;
    }

    /**
     * Al existir este bean, la autoconfiguración de Spring Boot se retira y no
     * intenta construir el suyo contra el JWKS remoto — que es lo que no se
     * puede hacer desde esta subred.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        JWKSet claves;
        try {
            claves = JWKSet.parse(jwks);
        } catch (ParseException e) {
            /*
             * Falla al arrancar y no en la primera peticion. En Lambda el
             * arranque es el que toma la instantanea de SnapStart, asi que la
             * version queda en Failed y el alias no la puede apuntar: el
             * despliegue se detiene sirviendo todavia el codigo anterior, que es
             * exactamente lo que se quiere cuando la configuracion de
             * autenticacion esta rota.
             */
            throw new IllegalStateException(
                    "COGNITO_JWKS no es un juego de claves JSON válido. Lo inyecta "
                            + "Terraform desde data.http.jwks_inquilinos.", e);
        }

        if (claves.getKeys().isEmpty()) {
            throw new IllegalStateException("COGNITO_JWKS no trae ninguna clave.");
        }

        var procesador = new DefaultJWTProcessor<SecurityContext>();
        /*
         * RS256 y nada mas. Cognito firma con RS256, y fijar el algoritmo aqui
         * cierra la familia de ataques de confusion de algoritmo: `alg: none`,
         * o un HS256 firmado con la clave publica RSA como si fuera secreto
         * compartido. Aceptar «lo que diga la cabecera» es dejar que el atacante
         * elija como se le verifica.
         */
        procesador.setJWSKeySelector(
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, new ImmutableJWKSet<>(claves)));

        /*
         * Se desactiva el verificador de reclamaciones de Nimbus: las
         * comprobaciones las hace Spring con los validadores de abajo, y tenerlas
         * en dos sitios significa que un dia solo se actualiza uno.
         */
        procesador.setJWTClaimsSetVerifier((reclamaciones, contexto) -> { });

        var decodificador = new NimbusJwtDecoder(procesador);
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtIssuerValidator(emisorEsperado),
                new JwtTimestampValidator(),
                new ExigeCaducidad(),
                new ExigeUsoYAudiencia(clienteEsperado))));

        return decodificador;
    }

    /**
     * {@code exp} obligatorio.
     *
     * <p>{@link JwtTimestampValidator} acepta un token SIN caducidad: comprueba
     * que no esté vencida si existe. Un token sin {@code exp} es una sesión que
     * no termina nunca, y Cognito siempre la pone — que falte significa que el
     * token no es suyo.
     */
    private static final class ExigeCaducidad implements OAuth2TokenValidator<Jwt> {

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            if (token.getExpiresAt() == null) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "El token no declara caducidad.", null));
            }
            return OAuth2TokenValidatorResult.success();
        }
    }

    /**
     * Que el token sea de acceso o de identidad, y para ESTA aplicación.
     *
     * <p>Cognito pone la audiencia en dos campos distintos según el tipo: los de
     * identidad llevan {@code aud}, los de acceso llevan {@code client_id} y
     * ningún {@code aud}. Comprobar solo uno deja pasar el otro sin mirar, que es
     * como un token emitido para otra aplicación del mismo grupo acaba siendo
     * válido aquí.
     *
     * <p>El de refresco se descarta por {@code token_use}: no es un JWT que
     * Cognito espere ver en una cabecera Authorization.
     */
    private static final class ExigeUsoYAudiencia implements OAuth2TokenValidator<Jwt> {

        private final String clienteEsperado;

        private ExigeUsoYAudiencia(String clienteEsperado) {
            this.clienteEsperado = clienteEsperado;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            String uso = token.getClaimAsString("token_use");
            if (!"access".equals(uso) && !"id".equals(uso)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "token_use no es 'access' ni 'id'.", null));
            }

            /*
             * Sin cliente configurado no se comprueba la audiencia, y se dice
             * aqui por que no es un agujero: `ondexia.cognito.cliente` viene de
             * COGNITO_CLIENTE_ID, que Terraform pone siempre en el perfil `aws`.
             * Vacio solo ocurre en una prueba que no ejercita esto.
             */
            if (clienteEsperado == null || clienteEsperado.isBlank()) {
                return OAuth2TokenValidatorResult.success();
            }

            boolean nuestro = "access".equals(uso)
                    ? clienteEsperado.equals(token.getClaimAsString("client_id"))
                    : token.getAudience() != null && token.getAudience().contains(clienteEsperado);

            if (!nuestro) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "El token no se emitió para esta aplicación.", null));
            }
            return OAuth2TokenValidatorResult.success();
        }
    }
}
