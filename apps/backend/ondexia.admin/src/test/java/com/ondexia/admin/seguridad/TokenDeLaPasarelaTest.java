package com.ondexia.admin.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/**
 * Lo que el panel comprueba de un token.
 *
 * <p>Los tokens de aquí se firman con una clave RSA generada en la propia
 * prueba, cuya pública se le entrega al decodificador. Eso <em>es</em> el
 * comportamiento desde el hallazgo A2: antes se firmaban con una clave inventada
 * que nadie miraba, y pasaban.
 *
 * <p>Los casos de firma viven en {@code FirmaDelTokenIT}, que además levanta el
 * contexto con el perfil {@code aws}. Aquí quedan las comprobaciones que no
 * necesitan Spring, incluida la que distingue un 401 de un 500.
 */
class TokenDeLaPasarelaTest {

    private static final String EMISOR =
            "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_personal";
    private static final String CLIENTE = "cliente-del-panel";

    private static final RSAKey CLAVE = generar();

    private final TokenDeLaPasarela decodificador =
            new TokenDeLaPasarela(EMISOR, CLIENTE, new JWKSet(CLAVE.toPublicJWK()).toString());

    private static RSAKey generar() {
        try {
            return new RSAKeyGenerator(2048).keyID("clave-de-la-prueba").generate();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar la clave de prueba", e);
        }
    }

    private static String token(String emisor, String cliente, Instant caduca) {
        // Emitido una hora antes de caducar, como los de Cognito. Fechar la
        // emision con «ahora» daria un token caducado ANTES de emitirse, que no
        // existe en la realidad y ademas ni siquiera llega a construirse.
        return token(emisor, cliente, caduca.minus(1, ChronoUnit.HOURS), caduca);
    }

    private static String token(String emisor, String cliente, Instant emitido, Instant caduca) {
        try {
            var reclamos = new JWTClaimsSet.Builder()
                    .issuer(emisor)
                    .subject("f468a438-d011-70a1-5a2a-d6caa87fa921")
                    .claim("client_id", cliente)
                    .claim("email", "operador@ondexia.com")
                    .issueTime(Date.from(emitido))
                    .expirationTime(Date.from(caduca))
                    .build();

            var firmado = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(CLAVE.getKeyID()).build(),
                    reclamos);
            firmado.sign(new RSASSASigner(CLAVE));
            return firmado.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo construir el token de prueba", e);
        }
    }

    private static String tokenValido() {
        return token(EMISOR, CLIENTE, Instant.now().plus(30, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("un token bueno se lee entero, con el correo que va a la bitacora")
    void tokenBueno() {
        var leido = decodificador.decode(tokenValido());

        assertThat(leido.getClaimAsString("email")).isEqualTo("operador@ondexia.com");
        assertThat(leido.getSubject()).isEqualTo("f468a438-d011-70a1-5a2a-d6caa87fa921");
        assertThat(leido.getExpiresAt())
                .as("sin esto JwtTimestampValidator no tendria que mirar")
                .isNotNull();
    }

    @Test
    @DisplayName("un token del grupo de inquilinos no entra en el panel")
    void otroEmisor() {
        String deCliente = token(
                "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_inquilinos",
                CLIENTE, Instant.now().plus(30, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> decodificador.decode(deCliente))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("un token de otra aplicacion del mismo grupo tampoco")
    void otroCliente() {
        String deOtraApp = token(EMISOR, "otro-cliente",
                Instant.now().plus(30, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> decodificador.decode(deOtraApp))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("un token caducado se rechaza")
    void caducado() {
        String viejo = token(EMISOR, CLIENTE, Instant.now().minus(1, ChronoUnit.HOURS));

        assertThatThrownBy(() -> decodificador.decode(viejo))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("lo que no es un JWT se rechaza sin reventar")
    void basura() {
        assertThatThrownBy(() -> decodificador.decode("esto-no-es-un-token"))
                .isInstanceOf(BadJwtException.class);
    }

    @Test
    @DisplayName("un token incoherente da 401 y no 500")
    void reclamosImposibles() {
        /*
         * Caduca antes de emitirse. Jwt lo rechaza con IllegalArgumentException,
         * que NO es una JwtException: si se dejara escapar, el filtro de Spring
         * Security no la reconoceria y la respuesta seria un 500 —un fallo
         * nuestro— en vez de un 401 —un token que no sirve—.
         *
         * Lo descubrio esta misma prueba al escribirla mal.
         */
        Instant ahora = Instant.now();
        String imposible = token(EMISOR, CLIENTE, ahora, ahora.minus(1, ChronoUnit.HOURS));

        assertThatThrownBy(() -> decodificador.decode(imposible))
                .isInstanceOf(JwtException.class);
    }

    /**
     * La inversión de {@code unaFirmaQueNadieVerificaPasa} (hallazgo A2).
     *
     * <p>Aquella prueba existía «para que la decisión estuviera escrita en algo
     * que se ejecuta»: firmaba con una clave inventada en la propia clase y
     * comprobaba que el panel lo aceptaba. Fijaba la excepción, no la premisa —
     * y la premisa que la justificaba, «no hay otra puerta», era falsa: la ruta
     * {@code OPTIONS /{proxy+}} llega a esta función sin autorizador.
     *
     * <p>Ahora comprueba lo contrario, con el mismo token de entonces: un HMAC
     * firmado con una cadena cualquiera. Se rechaza porque el selector de claves
     * solo admite RS256 contra el JWKS del grupo, así que la confusión de
     * algoritmo tampoco entra por aquí.
     */
    @Test
    @DisplayName("una firma que no es de Cognito se rechaza")
    void unaFirmaAjenaNoPasa() throws Exception {
        var reclamos = new JWTClaimsSet.Builder()
                .issuer(EMISOR)
                .subject("f468a438-d011-70a1-5a2a-d6caa87fa921")
                .claim("client_id", CLIENTE)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plus(30, ChronoUnit.MINUTES)))
                .build();
        var conHmac = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), reclamos);
        conHmac.sign(new MACSigner("clave-de-32-bytes-para-la-prueba".getBytes()));

        assertThatThrownBy(() -> decodificador.decode(conHmac.serialize()))
                .isInstanceOf(JwtException.class);
    }
}
