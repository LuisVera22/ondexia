package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ondexia.infrastructure.configuration.SeguridadPasarelaConfig;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Caracteriza el JWKS inmutable de la API; no simula una rotación real de Cognito
 * ni acredita actualización automática o despliegue de una Lambda.
 */
class RotacionDeClavesIT {

    private static final String EMISOR = "https://emisor.invalid/grupo-controlado";
    private static final String CLIENTE = "aplicacion-controlada";
    private static RSAKey anterior;
    private static RSAKey nueva;
    private static RSAKey ajena;

    @BeforeAll
    static void prepararClavesIndependientes() throws JOSEException {
        anterior = new RSAKeyGenerator(2048).keyID("anterior").generate();
        nueva = new RSAKeyGenerator(2048).keyID("nueva").generate();
        // El mismo kid no acredita la firma: el material RSA sigue siendo ajeno.
        ajena = new RSAKeyGenerator(2048).keyID("nueva").generate();
    }

    @Test
    void sinActualizarJwksRechazaClaveNuevaYConservaLaAnterior() throws Exception {
        var decodificador = configurar(anterior.toPublicJWK());
        assertThat(decodificador.decode(firmar(anterior)).getSubject()).isEqualTo("usuario-controlado");
        assertThatThrownBy(() -> decodificador.decode(firmar(nueva)))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decodificador.decode(firmar(nueva)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void nuevaConfiguracionAceptaAmbasClavesPublicadasSinCambiarLaInstanciaAnterior() throws Exception {
        var sinActualizar = configurar(anterior.toPublicJWK());
        var actualizado = configurar(anterior.toPublicJWK(), nueva.toPublicJWK());
        assertThat(actualizado.decode(firmar(nueva)).getSubject()).isEqualTo("usuario-controlado");
        assertThat(actualizado.decode(firmar(anterior)).getSubject()).isEqualTo("usuario-controlado");
        assertThatThrownBy(() -> sinActualizar.decode(firmar(nueva)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void configuracionActualizadaRechazaFirmaAjenaAunqueCoincidaElKid() throws Exception {
        var actualizado = configurar(anterior.toPublicJWK(), nueva.toPublicJWK());
        assertThatThrownBy(() -> actualizado.decode(firmar(ajena)))
                .isInstanceOf(JwtException.class);
    }

    private static JwtDecoder configurar(JWK... clavesPublicas) {
        return new SeguridadPasarelaConfig(EMISOR, CLIENTE,
                new JWKSet(List.of(clavesPublicas)).toString()).jwtDecoder();
    }

    private static String firmar(RSAKey clave) throws JOSEException {
        var instante = Instant.now();
        var reclamaciones = new JWTClaimsSet.Builder()
                .issuer(EMISOR)
                .subject("usuario-controlado")
                .claim("token_use", "access")
                .claim("client_id", CLIENTE)
                .issueTime(Date.from(instante))
                .expirationTime(Date.from(instante.plusSeconds(3600)))
                .build();
        var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(clave.getKeyID()).build(), reclamaciones);
        token.sign(new RSASSASigner(clave));
        return token.serialize();
    }
}
