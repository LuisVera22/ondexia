package com.ondexia.infrastructure.configuration;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Emisor y validador de tokens propio, <strong>solo para el perfil
 * {@code local}</strong>.
 *
 * <h2>Por que existe</h2>
 *
 * El DTE §10.3 define la Fase 0 como desarrollo completo sin desplegar nada en
 * AWS: PostgreSQL en contenedor, la API corriendo directo, y la homologacion
 * contra la beta de SUNAT desde la maquina. Esa fase solo funciona si tambien
 * se puede autenticar sin Cognito — de otro modo no se puede llamar a un solo
 * endpoint hasta que exista el pool desplegado, y la fase mas barata del plan
 * deja de ser viable.
 *
 * <h2>Por que no es un agujero</h2>
 *
 * La clave se genera <strong>nueva en cada arranque</strong> y solo vive en
 * memoria: no hay ningun secreto en el repositorio, y un token emitido deja de
 * valer al reiniciar. La ruta de emision cuelga de {@code /desarrollo}, que
 * ademas queda fuera del contexto multiempresa.
 *
 * <p>Y sobre todo: el codigo que se ejecuta despues es <strong>identico</strong>
 * al de produccion. Se emite un JWT RS256 de verdad, lo valida el mismo
 * servidor de recursos, y {@code ResolutorContexto} lo resuelve igual. No hay
 * un camino «de pruebas» que pueda divergir del real — que es el defecto de
 * fondo de los filtros de autenticacion falsa.
 *
 * <h2>La barrera</h2>
 *
 * {@code @Profile("local")} es una condicion de configuracion, y las
 * condiciones de configuracion se equivocan. Por eso hay ademas una
 * comprobacion que impide arrancar si detecta que esto corre dentro de Lambda:
 * ahi el perfil {@code local} no puede ser mas que un error de despliegue, y es
 * preferible que la funcion no arranque a que arranque emitiendo tokens.
 */
@Configuration
@Profile("local")
public class SeguridadDesarrolloConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SeguridadDesarrolloConfig.class);

    private final RSAKey clave;

    public SeguridadDesarrolloConfig() throws KeyStoreException {
        exigirEntornoLocal();

        try {
            this.clave = new RSAKeyGenerator(2048).keyID("ondexia-local").generate();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException("No se pudo generar la clave de desarrollo", e);
        }

        LOG.warn("""

                ==========================================================================
                  PERFIL local: emisor de tokens de desarrollo ACTIVO.
                  Se acepta cualquier token firmado con una clave generada en este
                  arranque. Consigue uno en POST /desarrollo/token.
                  Esto NO debe estar activo en ningun entorno desplegado.
                ==========================================================================
                """);
    }

    /**
     * Se niega a arrancar dentro de Lambda.
     *
     * <p>{@code AWS_LAMBDA_FUNCTION_NAME} la define el propio entorno de
     * ejecucion; no es algo que se configure en el despliegue ni que se pueda
     * omitir por descuido.
     */
    private void exigirEntornoLocal() {
        String funcionLambda = System.getenv("AWS_LAMBDA_FUNCTION_NAME");
        if (funcionLambda != null && !funcionLambda.isBlank()) {
            throw new IllegalStateException(
                    "El perfil 'local' incluye un emisor de tokens de desarrollo y se ha "
                            + "detectado ejecucion en Lambda (funcion '" + funcionLambda
                            + "'). Se aborta el arranque: revisa SPRING_PROFILES_ACTIVE.");
        }
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        try {
            return NimbusJwtDecoder.withPublicKey(clave.toRSAPublicKey()).build();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException("Clave de desarrollo invalida", e);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        JWKSource<SecurityContext> fuente = new ImmutableJWKSet<>(new JWKSet(clave));
        return new NimbusJwtEncoder(fuente);
    }
}
