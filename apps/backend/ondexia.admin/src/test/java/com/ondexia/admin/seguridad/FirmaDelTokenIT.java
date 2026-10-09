package com.ondexia.admin.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;

/**
 * La consola, con el perfil {@code aws}, rechaza un token que no firmó Cognito.
 *
 * <h2>Por qué esta prueba existe</h2>
 *
 * <p>Hallazgo A2 de la auditoría 2026-09-01. {@link TokenDeLaPasarela} aceptaba
 * cualquier firma —incluida {@code alg: none}— apoyándose en que «la pasarela es
 * la única puerta». No lo era: {@code OPTIONS /{proxy+}} llega a esta misma
 * función sin autorizador.
 *
 * <p>Y la prueba que existía, {@code unaFirmaQueNadieVerificaPasa}, fijaba la
 * excepción en vez de la premisa: dejaba constancia verde de que una firma falsa
 * pasaba. Esta la sustituye.
 *
 * <h2>Qué se monta</h2>
 *
 * <p>{@link DecodificadorDelPanel} con el perfil {@code aws} activo y las
 * propiedades mínimas. Solo esa clase: cargar {@code SeguridadAdmin} arrastraría
 * {@code HttpSecurity}, con él la autoconfiguración entera y con ella un
 * contenedor de PostgreSQL, para comprobar que una firma no cuadra.
 *
 * <p>Que arranque ya prueba algo. Con el perfil {@code aws},
 * {@code ondexia.panel.jwks} es obligatoria y sin claves el constructor lanza:
 * la consola no puede levantarse sin saber verificar firmas.
 */
@SpringBootTest(
        classes = DecodificadorDelPanel.class,
        properties = {
            "ondexia.panel.emisor=" + FirmaDelTokenIT.EMISOR,
            "ondexia.panel.cliente=" + FirmaDelTokenIT.CLIENTE,
        })
@ActiveProfiles("aws")
class FirmaDelTokenIT {

    static final String EMISOR = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_personal";
    static final String CLIENTE = "cliente-del-panel";

    /** La legítima: su pública está en el JWKS que ve la consola. */
    private static RSAKey nuestra;

    /** La del atacante: no está en ninguna parte. */
    private static RSAKey ajena;

    /*
     * El JWKS tiene que existir antes de que Spring construya el contexto, y las
     * propiedades de la anotacion son constantes de compilacion. Un bloque
     * estatico corre antes que nada de eso.
     */
    static {
        try {
            nuestra = new RSAKeyGenerator(2048).keyID("clave-legitima").generate();
            ajena = new RSAKeyGenerator(2048).keyID("clave-ajena").generate();
            System.setProperty("ondexia.panel.jwks", new JWKSet(nuestra.toPublicJWK()).toString());
        } catch (JOSEException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Autowired
    private JwtDecoder decodificador;

    @Test
    @DisplayName("Un token firmado por el pool se acepta")
    void tokenLegitimo() throws Exception {
        var token = firmar(nuestra, reclamacionesValidas().build());

        assertThat(decodificador.decode(token).getClaimAsString("email"))
                .isEqualTo("operador@ondexia.com");
    }

    /**
     * La prueba que sustituye a {@code unaFirmaQueNadieVerificaPasa}.
     *
     * <p>Este token es correcto en todo menos en quién lo firmó: mismo emisor,
     * mismo cliente, sin caducar. Antes se aceptaba, y con él se aceptaba
     * cualquier identidad que quien atacara quisiera declararse — incluida la de
     * un operador con acceso a todas las cuentas.
     */
    @Test
    @DisplayName("Un token firmado con OTRA clave se rechaza")
    void firmaAjena() throws Exception {
        var token = firmar(ajena, reclamacionesValidas().build());

        assertThatThrownBy(() -> decodificador.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Un token sin firma —alg: none— se rechaza")
    void sinFirma() {
        var token = new PlainJWT(reclamacionesValidas().build()).serialize();

        assertThatThrownBy(() -> decodificador.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Un token sin caducidad se rechaza")
    void sinCaducidad() throws Exception {
        var token = firmar(nuestra, new JWTClaimsSet.Builder()
                .issuer(EMISOR)
                .subject("f468a438-d011-70a1-5a2a-d6caa87fa921")
                .claim("client_id", CLIENTE)
                .issueTime(Date.from(Instant.now()))
                .build());

        assertThatThrownBy(() -> decodificador.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Un token del grupo de inquilinos no entra en el panel")
    void otroEmisor() throws Exception {
        var token = firmar(nuestra, reclamacionesValidas()
                .issuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_inquilinos")
                .build());

        assertThatThrownBy(() -> decodificador.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Un token de otra aplicación del mismo grupo tampoco")
    void otroCliente() throws Exception {
        var token = firmar(nuestra, reclamacionesValidas()
                .claim("client_id", "otra-consola")
                .build());

        assertThatThrownBy(() -> decodificador.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Y el contexto arranca, que es lo que prueba que el JWKS es obligatorio")
    void elContextoArranca() {
        assertThat(decodificador)
                .as("con el perfil aws el decodificador es el nuestro, no el de Spring")
                .isInstanceOf(TokenDeLaPasarela.class);
    }

    private static JWTClaimsSet.Builder reclamacionesValidas() {
        Instant caduca = Instant.now().plus(30, ChronoUnit.MINUTES);
        return new JWTClaimsSet.Builder()
                .issuer(EMISOR)
                .subject("f468a438-d011-70a1-5a2a-d6caa87fa921")
                .claim("client_id", CLIENTE)
                .claim("email", "operador@ondexia.com")
                .issueTime(Date.from(caduca.minus(1, ChronoUnit.HOURS)))
                .expirationTime(Date.from(caduca));
    }

    private static String firmar(RSAKey clave, JWTClaimsSet reclamaciones) throws JOSEException {
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(clave.getKeyID()).build(),
                reclamaciones);
        jwt.sign(new RSASSASigner(clave));
        return jwt.serialize();
    }
}
