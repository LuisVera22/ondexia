package com.ondexia.pruebas;

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
import com.ondexia.infrastructure.configuration.SeguridadPasarelaConfig;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * El decodificador del perfil {@code aws} verifica la firma. De verdad.
 *
 * <h2>Por qué esta prueba existe</h2>
 *
 * <p>Hallazgo A1 de la auditoría 2026-09-01: este componente comprobaba emisor y
 * caducidad y <strong>no verificaba la firma</strong>, delegándola en el
 * autorizador de API Gateway. El comentario de {@code application-aws.yml} decía
 * que sí se revalidaba; llevaba desactualizado desde que el bean cambió.
 *
 * <p>Ningún test ejercitaba ese decodificador, y ahí está la lección: un control
 * de seguridad descrito en prosa y no ejercitado por una prueba deja de existir
 * en cuanto alguien toca el código, sin que nada se ponga rojo.
 *
 * <h2>Qué se monta</h2>
 *
 * <p>Solo {@link SeguridadPasarelaConfig}, con el perfil {@code aws} activo y las
 * propiedades mínimas. No se levanta la aplicación entera a propósito: el perfil
 * {@code aws} exige base de datos, y esta prueba no habla de base de datos.
 *
 * <p>Que el contexto arranque ya prueba algo: la clase lleva
 * {@code @Profile("aws")}, así que si el perfil no estuviera activo no habría
 * bean y esto fallaría al inyectar.
 *
 * <h2>El par de claves se genera aquí</h2>
 *
 * <p>Dos pares: uno cuya pública va en el JWKS —el legítimo— y otro que no está
 * en ninguna parte, que es el del atacante. Generarlos en la prueba, en vez de
 * llevar claves fijas en el repositorio, evita la pregunta de si esa clave
 * privada estuvo alguna vez en algún sitio de verdad.
 */
@SpringBootTest(
        classes = SeguridadPasarelaConfig.class,
        properties = {
            "spring.security.oauth2.resourceserver.jwt.issuer-uri="
                    + FirmaDelTokenIT.EMISOR,
            "ondexia.cognito.cliente=" + FirmaDelTokenIT.CLIENTE,
            // El JWKS lo pone el inicializador de abajo: hay que generarlo antes
            // de que arranque el contexto y una anotación no puede llamar a
            // código.
        })
@ActiveProfiles("aws")
class FirmaDelTokenIT {

    static final String EMISOR = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_deprueba";
    static final String CLIENTE = "cliente-de-esta-aplicacion";

    /** La legítima: su pública está en el JWKS que ve la aplicación. */
    private static RSAKey nuestra;

    /** La del atacante: no está en ninguna parte. */
    private static RSAKey ajena;

    @BeforeAll
    static void generarClaves() throws JOSEException {
        // Ya generadas por el inicializador estático de abajo; este método solo
        // documenta el orden. Ver `prepararJwks`.
        assertThat(nuestra).isNotNull();
        assertThat(ajena).isNotNull();
    }

    /*
     * El JWKS tiene que existir ANTES de que Spring construya el contexto, y las
     * propiedades de @SpringBootTest son constantes de compilacion. Un bloque
     * estatico corre antes que todo eso y deja el valor en una propiedad del
     * sistema, que application.yml resuelve con ${COGNITO_JWKS:}... no: se
     * inyecta directamente como propiedad de Spring.
     */
    static {
        try {
            nuestra = new RSAKeyGenerator(2048).keyID("clave-legitima").generate();
            ajena = new RSAKeyGenerator(2048).keyID("clave-ajena").generate();
            System.setProperty(
                    "ondexia.cognito.jwks", new JWKSet(nuestra.toPublicJWK()).toString());
        } catch (JOSEException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Autowired
    private JwtDecoder decodificador;

    // ── Lo que tiene que pasar ──────────────────────────────────────────────

    @Test
    @DisplayName("Un token firmado con la clave del pool se acepta")
    void tokenLegitimo() throws Exception {
        var token = firmar(nuestra, reclamacionesValidas().build());

        assertThatCode(() -> decodificador.decode(token)).doesNotThrowAnyException();
        assertThat(decodificador.decode(token).getSubject()).isEqualTo("usuario-1");
    }

    // ── Lo que no ───────────────────────────────────────────────────────────

    /**
     * La prueba que da nombre al hallazgo.
     *
     * <p>Este token es correcto en todo menos en quién lo firmó: mismo emisor,
     * misma audiencia, sin caducar. Antes pasaba, y con él pasaba cualquier
     * identidad que el atacante quisiera declarar.
     */
    @Test
    @DisplayName("Un token firmado con OTRA clave se rechaza")
    void firmaAjena() throws Exception {
        var token = firmar(ajena, reclamacionesValidas().build());

        assertThatThrownBy(() -> decodificador.decode(token))
                .isInstanceOf(JwtException.class);
    }

    /**
     * {@code alg: none}: el token sin firma.
     *
     * <p>Se rechaza porque el selector de claves fija RS256. Aceptar «lo que
     * diga la cabecera» es dejar que el atacante elija cómo se le verifica.
     */
    @Test
    @DisplayName("Un token sin firma —alg: none— se rechaza")
    void sinFirma() {
        var token = new PlainJWT(reclamacionesValidas().build()).serialize();

        assertThatThrownBy(() -> decodificador.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Un token caducado se rechaza")
    void caducado() throws Exception {
        var token = firmar(nuestra, reclamacionesValidas()
                .expirationTime(Date.from(Instant.now().minusSeconds(3600)))
                .build());

        assertThatThrownBy(() -> decodificador.decode(token))
                .isInstanceOf(JwtException.class);
    }

    /**
     * Sin {@code exp}, que antes pasaba.
     *
     * <p>{@code JwtTimestampValidator} solo comprueba la caducidad si existe, así
     * que un token sin ella era una sesión que no termina nunca.
     */
    @Test
    @DisplayName("Un token sin caducidad se rechaza")
    void sinCaducidad() throws Exception {
        var reclamaciones = new JWTClaimsSet.Builder()
                .issuer(EMISOR)
                .subject("usuario-1")
                .claim("token_use", "access")
                .claim("client_id", CLIENTE)
                .issueTime(Date.from(Instant.now()))
                .build();

        assertThatThrownBy(() -> decodificador.decode(firmar(nuestra, reclamaciones)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Un token de otro pool se rechaza aunque esté bien firmado")
    void otroEmisor() throws Exception {
        var token = firmar(nuestra, reclamacionesValidas()
                .issuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_otro")
                .build());

        assertThatThrownBy(() -> decodificador.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Nested
    @DisplayName("Uso y audiencia")
    class UsoYAudiencia {

        @Test
        @DisplayName("Un token de refresco no vale como token de acceso")
        void tokenDeRefresco() throws Exception {
            var token = firmar(nuestra, reclamacionesValidas()
                    .claim("token_use", "refresh")
                    .build());

            assertThatThrownBy(() -> decodificador.decode(token))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("Un token de acceso emitido para otra aplicación se rechaza")
        void otroClienteEnAcceso() throws Exception {
            var token = firmar(nuestra, reclamacionesValidas()
                    .claim("client_id", "otra-aplicacion-del-mismo-pool")
                    .build());

            assertThatThrownBy(() -> decodificador.decode(token))
                    .isInstanceOf(JwtException.class);
        }

        /**
         * El de identidad lleva la audiencia en {@code aud} y no en
         * {@code client_id}. Comprobar solo uno de los dos campos deja pasar el
         * otro sin mirar.
         */
        @Test
        @DisplayName("Un token de identidad para otra aplicación se rechaza")
        void otroClienteEnIdentidad() throws Exception {
            var token = firmar(nuestra, new JWTClaimsSet.Builder()
                    .issuer(EMISOR)
                    .subject("usuario-1")
                    .claim("token_use", "id")
                    .audience("otra-aplicacion-del-mismo-pool")
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                    .build());

            assertThatThrownBy(() -> decodificador.decode(token))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("Un token de identidad de esta aplicación se acepta")
        void identidadNuestra() throws Exception {
            var token = firmar(nuestra, new JWTClaimsSet.Builder()
                    .issuer(EMISOR)
                    .subject("usuario-1")
                    .claim("token_use", "id")
                    .audience(CLIENTE)
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                    .build());

            assertThatCode(() -> decodificador.decode(token)).doesNotThrowAnyException();
        }
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    private static JWTClaimsSet.Builder reclamacionesValidas() {
        return new JWTClaimsSet.Builder()
                .issuer(EMISOR)
                .subject("usuario-1")
                .claim("token_use", "access")
                .claim("client_id", CLIENTE)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)));
    }

    private static String firmar(RSAKey clave, JWTClaimsSet reclamaciones) throws JOSEException {
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(clave.getKeyID()).build(),
                reclamaciones);
        jwt.sign(new RSASSASigner(clave));
        return jwt.serialize();
    }
}
