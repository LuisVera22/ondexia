package com.ondexia.admin.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/**
 * Lo que el panel comprueba de un token, ahora que la firma la comprueba la
 * pasarela.
 *
 * <p>Los tokens de aquí se firman con una clave inventada, y eso no es una
 * simplificación de la prueba: es el comportamiento. Ver
 * {@link #unaFirmaQueNadieVerificaPasa()}.
 */
class TokenDeLaPasarelaTest {

    private static final String EMISOR =
            "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_personal";
    private static final String CLIENTE = "cliente-del-panel";

    private final TokenDeLaPasarela decodificador = new TokenDeLaPasarela(EMISOR, CLIENTE);

    /** Una clave cualquiera: el panel no la usa para nada, y ese es el punto. */
    private static final byte[] CLAVE = "clave-de-32-bytes-para-la-prueba".getBytes();

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

            var firmado = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), reclamos);
            firmado.sign(new MACSigner(CLAVE));
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
                .isInstanceOf(BadJwtException.class);
    }

    @Test
    @DisplayName("una firma que nadie verifica pasa: la comprueba la pasarela")
    void unaFirmaQueNadieVerificaPasa() {
        /*
         * Esta prueba no describe un descuido, describe la decision, y esta
         * escrita para que se caiga si alguien la cambia sin querer.
         *
         * El token va firmado con una clave inventada en esta misma clase, que
         * no tiene nada que ver con Cognito, y aun asi se acepta. Se puede
         * porque la funcion solo es invocable desde su pasarela —el permiso de
         * invocacion esta atado al ARN de esa API— y la ruta $default lleva un
         * autorizador JWT que valida la firma antes de invocar.
         *
         * Si algun dia se anade otra ruta sin autorizador, u otro disparador
         * sobre esta funcion, esta linea deja de ser aceptable. Que este aqui,
         * verde y con nombre explicito, es lo que hace que alguien lo note.
         */
        String conFirmaInventada = tokenValido();

        assertThat(decodificador.decode(conFirmaInventada).getSubject()).isNotNull();
    }
}
